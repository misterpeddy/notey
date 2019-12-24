package ai.peddy.notey;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

public class WearActivity extends WearableActivity implements CapabilityClient.OnCapabilityChangedListener {

    private static final String WEAR_ACTIVITY_TAG = "WearActivity";
    private static final String SPOTIFY_CLIENT_CAPABILITY = "spotify_client";
    private static final String SPOTIFY_ADD_MARKER_MESSAGE_PATH = "/spotify/marker/add";
    private TextView connectionStatusTv;
    private Button dropMarkerButton;
    private Node spotifyClientNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear);

        dropMarkerButton = (Button) findViewById(R.id.button_drop_marker);
        connectionStatusTv = (TextView) findViewById(R.id.tv_connection_status);

        // Pre-set Spotify-capable node and set up a listener to handle changes
        Wearable.getCapabilityClient(this).
                getCapability(SPOTIFY_CLIENT_CAPABILITY, CapabilityClient.FILTER_REACHABLE).addOnSuccessListener(capabilityInfo -> {
            onCapabilityChanged(capabilityInfo);
        });
        Wearable.getCapabilityClient(this).
                addListener(this, SPOTIFY_CLIENT_CAPABILITY);

        dropMarkerButton.setOnClickListener(button -> {
            onDropMarkerClick();
        });

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    /*
     * Updates the Spotify client node if capabilities of nodes in the device mesh change
     */
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        for (Node node : capabilityInfo.getNodes()) {
            if (node.isNearby()) {
                spotifyClientNode = node;
                break;
            }
            spotifyClientNode = node;
        }

        String connectionStatus = spotifyClientNode == null ?
                getString(R.string.not_connected) :
                getString(R.string.connected_prefix) + " " + spotifyClientNode.getDisplayName();

        connectionStatusTv.setText(connectionStatus);
        Log.i(WEAR_ACTIVITY_TAG, connectionStatus);
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
