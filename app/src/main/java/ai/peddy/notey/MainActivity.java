package ai.peddy.notey;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;

public class MainActivity extends Activity {

    SpotifyClient spotify;
    Button button;
    TextView playbackTv;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Subscription.EventCallback callback = event -> {
                Log.d("MainActivity", "Received: " + event.toString());
                // TODO(peddy): Filter events and on track change flush tracker buffer to remote storage
            };

        spotify = SpotifyClient.getSpotifyClient(this);
        spotify.registerPlayerStateListener(callback);

        button = (Button) findViewById(R.id.time_button);
        playbackTv = (TextView) findViewById(R.id.playback_time);

        button.setOnClickListener(view -> {
            CallResult<PlayerState> stateResult = spotify.getPlaybackState();
            Log.d("MainActivity", "playerstate: " + stateResult.toString());

            stateResult.setResultCallback(playerState -> {
                playbackTv.setText("" + playerState.playbackPosition);
            });
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
