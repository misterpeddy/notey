package ai.peddy.notey;


import android.content.Context;
import android.util.Log;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;

public class SpotifyClient {

    private static final String SPOTIFY_CLIENT_TAG = "SpotifyClient";
    private static final String CLIENT_ID = "5fbdf1fb00b94108a62c428afb146d56";
    private static final String REDIRECT_URI = "http://notey.peddy.ai/callback";
    private static final String SPOTIFY_NOW_PLAYING_PATH = "/spotify/track/get";
    private static SpotifyClient mSpotifyClient;
    private SpotifyAppRemote mSpotifyAppRemote;
    private Context mContext;

    private SpotifyClient(Context context) {
        mContext = context;
    }

    public static SpotifyClient getSpotifyClient(Context context) {
        if (mSpotifyClient != null) return mSpotifyClient;
        mSpotifyClient = new SpotifyClient(context);
        return mSpotifyClient;
    }

    /*
     * Connects to Spotify remote and registers eventCallback to listen to player state changes
     */
    public void registerPlayerStateListener(Subscription.EventCallback<PlayerState> eventCallback) {
        Connector.ConnectionListener listener = new Connector.ConnectionListener() {

            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                mSpotifyAppRemote = spotifyAppRemote;
                Log.d(SPOTIFY_CLIENT_TAG, "Connected to Spotify");
                subscribeToPlayerState(eventCallback);
            }

            @Override
            public void onFailure(Throwable throwable) {
                Log.e(SPOTIFY_CLIENT_TAG, "Could not connect to Spotify: " +
                        throwable.getMessage(), throwable);
            }
        };

        connect(listener);
    }

    public CallResult<PlayerState> getPlayerState() {
        if (mSpotifyAppRemote == null || !mSpotifyAppRemote.isConnected())
            return null;

        return mSpotifyAppRemote.getPlayerApi().getPlayerState();
    }

    public void disconnect() {
        if (mSpotifyAppRemote != null && mSpotifyAppRemote.isConnected())
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
    }

    /*
     * Connects to Spotify remote if not already connected and registers the given callback.
     */
    private void connect(Connector.ConnectionListener connectionListener) {
        if (mSpotifyAppRemote != null && mSpotifyAppRemote.isConnected())
            return;

        ConnectionParams connectionParams = new ConnectionParams.Builder(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
                .showAuthView(true)
                .build();
        Log.i(SPOTIFY_CLIENT_TAG, "Created ConnectionParams for client with ID: " +
                connectionParams.getClientId());

        SpotifyAppRemote.connect(mContext, connectionParams, connectionListener);
    }

    private boolean subscribeToPlayerState(Subscription.EventCallback<PlayerState> eventCallback) {
        if (mSpotifyAppRemote == null || mSpotifyAppRemote.getPlayerApi() == null) return false;

        mSpotifyAppRemote.getPlayerApi().
                subscribeToPlayerState().
                setEventCallback(eventCallback);

        return true;
    }
}
