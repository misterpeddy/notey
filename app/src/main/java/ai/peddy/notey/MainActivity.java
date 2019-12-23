package ai.peddy.notey;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;

public class MainActivity extends Activity implements MessageClient.OnMessageReceivedListener {

    private static final String MAIN_ACTIVITY_TAG = "MainActivity";
    private SpotifyClient spotify;
    private TextView playbackPositionTv;

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
        Toast.makeText(this, "Start", Toast.LENGTH_LONG).show();


        playbackPositionTv = (TextView) findViewById(R.id.playback_position);

        spotify = SpotifyClient.getSpotifyClient(this);
        spotify.registerPlayerStateListener(callback);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        spotify.getPlayerState().setResultCallback(playerState -> {
            playbackPositionTv.setText(playerState.playbackPosition + "");
        });

        Toast.makeText(this, "GOT IT", Toast.LENGTH_LONG).show();

        Log.d(MAIN_ACTIVITY_TAG, "Received Message: " + messageEvent.getPath() + " : " + messageEvent.getData());
    }
}
