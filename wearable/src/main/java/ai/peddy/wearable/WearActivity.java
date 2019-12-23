package ai.peddy.wearable;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;

public class WearActivity extends WearableActivity implements CapabilityClient.OnCapabilityChangedListener {

    private static final String WEAR_ACTIVITY_TAG = "WearActivity";
    private static final String SPOTIFY_CLIENT_CAPABILITY = "spotify_client";
    private static final String SPOTIFY_ADD_MARKER_MESSAGE_PATH = "/spotify/marker/add";
    private TextView playbackPositionTv;
    private Button getPlaybackPositionButton;
    private String spotifyClientNodeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear);

        getPlaybackPositionButton = (Button) findViewById(R.id.button_playback_position);
        playbackPositionTv = (TextView) findViewById(R.id.tv_playback_time);

        // Pre-set Spotify-capable node and set up a listener to handle changes
        Wearable.getCapabilityClient(this).
                getCapability(SPOTIFY_CLIENT_CAPABILITY, CapabilityClient.FILTER_ALL).addOnSuccessListener(capabilityInfo -> {
            onCapabilityChanged(capabilityInfo);
        });
        Wearable.getCapabilityClient(this).
                addListener(this, SPOTIFY_CLIENT_CAPABILITY);

        getPlaybackPositionButton.setOnClickListener(button -> {
            byte[] data = "string".getBytes();
            Task<Integer> sendTask = Wearable.getMessageClient(this).
                    sendMessage(spotifyClientNodeId, SPOTIFY_ADD_MARKER_MESSAGE_PATH, data);
            sendTask.addOnSuccessListener(Integer -> {
                Log.d(WEAR_ACTIVITY_TAG, "Message successfully sent");
            });
            sendTask.addOnFailureListener(Integer -> {
                Log.d(WEAR_ACTIVITY_TAG, "Message was not sent");
            });
        });

        /*
        getPlaybackPositionButton.setOnClickListener(view -> {
            CallResult<PlayerState> stateResult = spotify.getPlaybackState();
            Log.d("MainActivity", "playerstate: " + stateResult.toString());

            stateResult.setResultCallback(playerState -> {
                playbackPositionTv.setText("" + playerState.playbackPosition);
            });
        });
*/

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Set<Node> nodes = capabilityInfo.getNodes();
        for (Node node : nodes) {
            Log.i(WEAR_ACTIVITY_TAG, String.format("Examining node %s which is (%b) nearby", node.getDisplayName(), node.isNearby()));
            if (node.isNearby()) {
                spotifyClientNodeId = node.getId();
                break;
            }
            spotifyClientNodeId = node.getId();
        }

        Log.i(WEAR_ACTIVITY_TAG, "Connected to node " + spotifyClientNodeId);
    }
}
