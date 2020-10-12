package ai.peddy.notey;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Track;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * In charge of all services Notey offers to the Activity:
 * - Communication with the Spotify remote over RPC
 * - Discovery and messaging with the Wearables on the mesh
 * - Recording audio from other apps
 */
public class NoteyService extends Service implements
        Subscription.EventCallback<PlayerState>,
        MessageClient.OnMessageReceivedListener,
        CapabilityClient.OnCapabilityChangedListener {

    public static final String TRACK_URI_NAME_DELIM = "<:>";

    private static final String TAG = "NoteyService";
    private static final String MARKER_CLIENT_CAPABILITY = "marker_client";
    private static final String SPOTIFY_ADD_MARKER_MESSAGE_PATH = "/spotify/marker/add";
    private static final String SPOTIFY_NOW_PLAYING_PATH = "/spotify/track/get";

    private Track nowPlaying;
    private Node wearableNode;
    private SpotifyClient spotify;
    private SharedPreferences sharedPreferences;

    /*
     * Called when Service is created
     */
    @Override
    public void onCreate() {
        // Set up SpotifyClient
        spotify = SpotifyClient.getSpotifyClient(getApplicationContext());
        spotify.registerPlayerStateListener(this);

        // Register message listeners and continually ensure connection to right wearable
        Wearable.getMessageClient(this).
                addListener(this);
        Wearable.getCapabilityClient(this)
                .getCapability(MARKER_CLIENT_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener(this::onCapabilityChanged);
        Wearable.getCapabilityClient(this)
                .addListener(this, MARKER_CLIENT_CAPABILITY);

        sharedPreferences = getSharedPreferences(
                getString(R.string.session_markers_prefs_file), MODE_PRIVATE);

        displayNotification();
        Log.i(TAG, "NoteyService successfully created");
    }

    /*
     * Called each time Service is started
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("This service cannot be bound to.");
    }

     @Override
     /*
      * Called when another device on the mesh sends a message to this device
      */
     public void onMessageReceived(@NonNull MessageEvent messageEvent) {
         Log.d(TAG, "Received Message: " +
                 messageEvent.getPath() + " : " + messageEvent.getData());
         if (messageEvent.getPath().equals(SPOTIFY_ADD_MARKER_MESSAGE_PATH)) {
             spotify.getPlayerState().setResultCallback(playerState -> {
                 updateMarkers(playerState.track.uri + TRACK_URI_NAME_DELIM
                                 + playerState.track.name, playerState.playbackPosition);
             });
             AudioRecorder.getInstance(getApplicationContext()).captureWindow();
         }
     }

     @Override
     /*
      * Updates the Spotify client node if capabilities of nodes in the device mesh change
      */
     public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
         for (Node node : capabilityInfo.getNodes()) {
             wearableNode = node;
             if (node.isNearby()) break;
         }
         Log.i(TAG, "Connected to " + wearableNode);
     }


    @Override
    /*
     * Called on Spotify player state change; sends nowplaying track name if changed from last event
     */
    public void onEvent(PlayerState playerState) {
        Log.i(TAG, "Received Spotify PlayerState: " + playerState);

        if (wearableNode == null) {
            Log.w(TAG, "Cannot send event - Wearable node not connected");
            return;
        }
        sendWearablePlayerState(playerState);
    }

    private void sendWearablePlayerState(PlayerState playerState) {
        if (playerState == null || (nowPlaying != null && playerState.track.uri.equals(nowPlaying.uri)))
            return;

        byte[] nameBytes = playerState.track.name.getBytes();
        Wearable.getMessageClient(this)
                .sendMessage(wearableNode.getId(), SPOTIFY_NOW_PLAYING_PATH, nameBytes)
                .addOnSuccessListener(Integer -> {
                    nowPlaying = playerState.track;
                    Log.i(TAG, String.format("Message [%s] successfully sent",
                            SPOTIFY_NOW_PLAYING_PATH));
                    Toast.makeText(this, "Wearable updated", Toast.LENGTH_SHORT)
                            .show();
                }).addOnFailureListener(Integer -> {
                    Log.w(TAG, String.format("Message [%s] failed to send",
                            SPOTIFY_NOW_PLAYING_PATH));
                    Toast.makeText(this, "Wearable update failed", Toast.LENGTH_SHORT)
                            .show();
                });
    }

    /*
     * Retrieves the current set of markers for track URI, and adds playbackPosition to it
     * If playbackPosition is null, returns only the existing markers for the track URI
     */
    public Set<Long> updateMarkers(String uriTitle, Long playbackPosition) {
        Set<String> markers = new HashSet<>();
        markers = new HashSet(sharedPreferences.getStringSet(uriTitle, markers));

        if (playbackPosition != null) {
            markers.add(String.format("%d", playbackPosition));
            sharedPreferences.edit().putStringSet(uriTitle, markers).commit();
            Log.i(TAG, String.format("Added marker at %d for entry %s", playbackPosition, uriTitle));
        }

        return markers.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }


    private void displayNotification() {
        Notification.Builder builder = new Notification.Builder(this);
        Intent nfIntent = new Intent(this, MainActivity.class);

        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0))
                .setContentTitle("Notey Audio Capture")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText("is running......")
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build();
        startForeground(110, notification);
    }
}
