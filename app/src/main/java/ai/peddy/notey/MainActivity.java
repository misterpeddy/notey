package ai.peddy.notey;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.spotify.protocol.client.Subscription;

import java.util.Locale;

public class MainActivity extends Activity implements MessageClient.OnMessageReceivedListener {

    private static final String MAIN_ACTIVITY_TAG = "MainActivity";
    private SpotifyClient spotify;
    private TextView playbackPositionTv;

    Subscription.EventCallback callback = event -> {
        Log.d("MainActivity", "Received: " + event.toString());
        // TODO(peddy): Filter events and on track change flush tracker buffer to remote storage
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        playbackPositionTv = (TextView) findViewById(R.id.playback_position);

        Wearable.getMessageClient(this).
                addListener(this);

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
            playbackPositionTv.setText(String.format(Locale.ENGLISH, "%d", playerState.playbackPosition ));
        });

        Log.d(MAIN_ACTIVITY_TAG, "Received Message from device in mesh: " + messageEvent.getPath() + " : " + messageEvent.getData());
    }
}
