package ai.peddy.notey;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MainActivity extends Activity implements
        MessageClient.OnMessageReceivedListener,
        Subscription.EventCallback<PlayerState>,
        CapabilityClient.OnCapabilityChangedListener {

    private static final String MAIN_ACTIVITY_TAG = "MainActivity";
    private static final String SPOTIFY_ADD_MARKER_MESSAGE_PATH = "/spotify/marker/add";
    private static final String SPOTIFY_NOW_PLAYING_PATH = "/spotify/track/get";
    private static final String MARKER_CLIENT_CAPABILITY = "marker_client";

    private SpotifyClient spotify;
    private TextView playbackPositionTv;
    private SharedPreferences sharedPreferences;
    private Track nowPlaying;
    private Node wearableNode;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        playbackPositionTv = findViewById(R.id.playback_position);
        sharedPreferences = getSharedPreferences(
                getString(R.string.session_markers_prefs_file), MODE_PRIVATE);

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

        // Update the markers TextView if returning to a session
        if (nowPlaying != null) {
            Set<Long> markers = getOrUpdateMarkers(nowPlaying.uri, null);
            playbackPositionTv.setText(Arrays.toString(markers.toArray()));
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
        Log.i(MAIN_ACTIVITY_TAG, "Connected to " + wearableNode.toString());
    }

    @Override
    /*
     * Called when another device on the mesh sends a message to this device
     */
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.d(MAIN_ACTIVITY_TAG, "Received Message: " +
                messageEvent.getPath() + " : " + messageEvent.getData());
        if (messageEvent.getPath().equals(SPOTIFY_ADD_MARKER_MESSAGE_PATH)) {
            spotify.getPlayerState().setResultCallback(playerState -> {
                Set<Long> markers = getOrUpdateMarkers(playerState.track.uri,
                        playerState.playbackPosition);
                playbackPositionTv.setText(Arrays.toString(markers.toArray()));
            });
        }
    }

    @Override
    /*
     * Called on Spotify player state change; sends nowplaying track name if changed from last event
     */
    public void onEvent(PlayerState playerState) {
        Log.i(MAIN_ACTIVITY_TAG, "Received Spotify PlayerState: " + playerState);

        if (wearableNode == null) {
            Log.w(MAIN_ACTIVITY_TAG, "Cannot send event - Wearable node not connected");
            return;
        }

        if (nowPlaying == null || !playerState.track.uri.equals(nowPlaying.uri)) {
            byte[] nameBytes = playerState.track.name.getBytes();
            Wearable.getMessageClient(this)
                    .sendMessage(wearableNode.getId(), SPOTIFY_NOW_PLAYING_PATH, nameBytes)
                    .addOnSuccessListener(Integer -> {
                        nowPlaying = playerState.track;
                        Log.i(MAIN_ACTIVITY_TAG, String.format("Message [%s] successfully sent",
                                SPOTIFY_ADD_MARKER_MESSAGE_PATH));
                        Toast.makeText(this, "Marker dropped", Toast.LENGTH_SHORT)
                                .show();
                    }).addOnFailureListener(Integer -> {
                Log.w(MAIN_ACTIVITY_TAG, String.format("Message [%s] failed to send",
                        SPOTIFY_ADD_MARKER_MESSAGE_PATH));
                Toast.makeText(this, "Marker drop failed", Toast.LENGTH_SHORT)
                        .show();
            });
            Log.i(MAIN_ACTIVITY_TAG, String.format("sent NowPlaying event: %s", nowPlaying.name));
        }
    }

    /*
     * Retrieves the current set of markers for track URI, and adds playbackPosition to it
     * If playbackPosition is null, returns only the existing markers for the track URI
     * TODO(peddy): Avoid reconstruction of set on every addition
     */
    private Set<Long> getOrUpdateMarkers(String uri, Long playbackPosition) {
        Set<String> markers = new HashSet<>();
        markers = sharedPreferences.getStringSet(uri, markers);

        if (playbackPosition != null) {
            markers.add(String.format("%d", playbackPosition));
            sharedPreferences.edit().putStringSet(uri, markers).commit();
        }

        Log.i(MAIN_ACTIVITY_TAG,
                String.format("Markers for track %s : %s", uri, markers.toString()));

        return markers.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

}
