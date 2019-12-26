package ai.peddy.notey;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

public class WearActivity extends WearableActivity implements CapabilityClient.OnCapabilityChangedListener, MessageClient.OnMessageReceivedListener {

    private static final String WEAR_ACTIVITY_TAG = "WearActivity";
    private static final String SPOTIFY_CLIENT_CAPABILITY = "spotify_client";
    private static final String SPOTIFY_ADD_MARKER_MESSAGE_PATH = "/spotify/marker/add";
    private static final String SPOTIFY_NOW_PLAYING_PATH = "/spotify/track/get";

    private TextView nowplayingTv;
    private Button dropMarkerButton;
    private Node spotifyClientNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear);

        dropMarkerButton = (Button) findViewById(R.id.button_drop_marker);
        nowplayingTv = (TextView) findViewById(R.id.tv_now_playing);

        // Make a continuously-updating 2-way connection with wearable
        Wearable.getMessageClient(this)
                .addListener(this);
        Wearable.getCapabilityClient(this).
                addListener(this, SPOTIFY_CLIENT_CAPABILITY);
        Wearable.getCapabilityClient(this).
                getCapability(SPOTIFY_CLIENT_CAPABILITY, CapabilityClient.FILTER_REACHABLE).addOnSuccessListener(capabilityInfo -> {
            onCapabilityChanged(capabilityInfo);
        });

        dropMarkerButton.setOnClickListener(button -> {
            onDropMarkerClick();
        });

        // Enable Always-on
        setAmbientEnabled();
    }

    @Override
    /*
     * Updates the Spotify client node if capabilities of nodes in the device mesh change
     */
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        for (Node node : capabilityInfo.getNodes()) {
            spotifyClientNode = node;
            if (node.isNearby()) break;
        }
        Log.i(WEAR_ACTIVITY_TAG, "Connected to " + spotifyClientNode.toString());
    }

    @Override
    /*
     * Called on receiving message from phone; updates the nowplaying TV with track name
     */
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.i(WEAR_ACTIVITY_TAG, "Received message: " + messageEvent);
        if (messageEvent.getPath().equals(SPOTIFY_NOW_PLAYING_PATH)) {
            String name = new String(messageEvent.getData());
            nowplayingTv.setText(name);
        }
    }

    /*
     * Tries to send a message to drop marker to Spotify client node
     */
    private void onDropMarkerClick() {
        if (spotifyClientNode == null) {
            Log.w(WEAR_ACTIVITY_TAG, "Not connected to Spotify Client Node");
            return;
        }

        byte[] data = new byte[]{};
        Task<Integer> sendTask = Wearable.getMessageClient(this).
                sendMessage(spotifyClientNode.getId(), SPOTIFY_ADD_MARKER_MESSAGE_PATH, data);

        sendTask.addOnSuccessListener(Integer -> {
            Log.d(WEAR_ACTIVITY_TAG, String.format("Message [%s] successfully sent", SPOTIFY_ADD_MARKER_MESSAGE_PATH));
            Toast.makeText(this, "Marker dropped", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(Integer -> {
            Log.d(WEAR_ACTIVITY_TAG, String.format("Message [%s] failed to send", SPOTIFY_ADD_MARKER_MESSAGE_PATH));
            Toast.makeText(this, "Marker drop failed", Toast.LENGTH_SHORT).show();
        });

    }
}
