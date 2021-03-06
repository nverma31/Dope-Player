package com.example.jamesb.dopeplayer;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.design.widget.BottomNavigationView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.util.ArrayList;
import java.util.List;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyCallback;
import kaaes.spotify.webapi.android.SpotifyError;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.Track;
import retrofit.client.Response;

/*
 * Mike, 05-MAY-2017
 * This base activity provides the navigation functionality that all other activities will inherit.
 * Derived from http://stackoverflow.com/questions/41744219/bottomnavigationview-between-activities
 */

public abstract class BaseActivity extends AppCompatActivity implements
        BottomNavigationView.OnNavigationItemSelectedListener, SpotifyPlayer.NotificationCallback,
        ConnectionStateCallback {
    protected BottomNavigationView navigationView;

    public static Player mPlayer;
    public static List<Track> tracks;
    public static String token;
    public static final int REQUEST_CODE = 1337;
    public static Player.NotificationCallback notificationCallback;
    TextView artist;
    TextView song;
    TextView time;
    TextView track;

    /*public static Player.OperationCallback mOperationCallback= = new Player.OperationCallback() {
        @Override
        public void onSuccess() {
            Log.d("Callback", "OperationCallback success");
        }

        @Override
        public void onError(Error error) {
            Log.d("Callback", "OperationCallack error: " + error);
        }
    };*/

    //on create, set the view and add listener to bottom navigation bar
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(getContentViewId());


        //set listener for bottom navigation bar by id
        navigationView = (BottomNavigationView) findViewById(R.id.navigation);
        navigationView.setOnNavigationItemSelectedListener(this);

        artist = (TextView) findViewById(R.id.textViewArtist);
        song = (TextView) findViewById(R.id.textViewSong);
        time = (TextView) findViewById(R.id.text_timecode);
        track = (TextView) findViewById(R.id.text_trackno);
    }

    //on start, update the navigation bar state
    @Override
    protected void onStart() {
        super.onStart();
        updateNavigationBarState();
    }

    // Remove transition between activities to avoid screen tossing on tapping bottom navigation item
    @Override
    public void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        navigationView.postDelayed(() -> {

            int itemId = item.getItemId();

            if (itemId == R.id.menu_traditional) {
                startActivity(new Intent(this, TraditionalActivity.class));
            } else if (itemId == R.id.menu_playing) {
                startActivity(new Intent(this, MainActivity.class));
            }
            else if ((itemId == R.id.menu_queue)) {
                startActivity(new Intent(this, PlaylistLauncher.class));

            }
            //else if (itemId == R.id.menu_queue) {
            //startActivity(new Intent(this, NotificationsActivity.class));
            //}
            finish();
        }, 300);
        return true;
    }



    private void updateNavigationBarState(){
        int actionId = getNavigationMenuItemId();
        selectBottomNavigationBarItem(actionId);
    }

    void selectBottomNavigationBarItem(int itemId) {
        Menu menu = navigationView.getMenu();
        for (int i = 0, size = menu.size(); i < size; i++) {
            MenuItem item = menu.getItem(i);
            boolean shouldBeChecked = item.getItemId() == itemId;
            if (shouldBeChecked) {
                item.setChecked(true);
                break;
            }
        }
    }

    public void spotifyLogin() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SpotifyConstants.cID,
                AuthenticationResponse.Type.TOKEN,
                SpotifyConstants.cRedirectURI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }

    @Override
    public void onTemporaryError() {
        Log.d("TraditionalActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onLoggedIn() {
        Log.d("SpotifyConnect", "User logged in");

        BaseActivity.mPlayer.playUri(null, "spotify:user:hendemic:playlist:4fWo8AAMu5GMnLtAhtPktC", 0, 0);

    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("MainActivity", "Login Failed: " + error.toString());
    }

    @Override
    public void onPlaybackError(Error error) {

    }


    public void setSongAndArtist(){
        Metadata.Track track = BaseActivity.mPlayer.getMetadata().currentTrack;
        String s = track.name;
        String a = track.artistName;
        artist.setText(getResources().getText(R.string.artist) + "  " + a);
        song.setText(getResources().getText(R.string.song) + "  " + s);
    }

    public Runnable mUpdateTime = new Runnable() {
        public void run() {
            int currentDuration;
            if (mPlayer.getPlaybackState().isPlaying) {
                currentDuration = (int) mPlayer.getPlaybackState().positionMs;
                updateTimeText(currentDuration);
                time.postDelayed(this, 1000);
            }else {
                time.removeCallbacks(this);
            }
        }
    };

    public void updateTimeText(int currentDuration){
        time.setText("" + milliSecondsToTimer((long) currentDuration));
    }

    public  String milliSecondsToTimer(long milliseconds) {
        String finalTimerString = "";
        String secondsString = "";

        // Convert total duration into time
        int hours = (int) (milliseconds / (1000 * 60 * 60));
        int minutes = (int) (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = (int) ((milliseconds % (1000 * 60 * 60)) % (1000 * 60) / 1000);
        // Add hours if there
        if (hours > 0) {
            finalTimerString = hours + ":";
        }

        // Prepending 0 to seconds if it is one digit
        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;
        }

        finalTimerString = finalTimerString + minutes + ":" + secondsString;

        // return timer string
        return finalTimerString;
    }

    //TODO stub
    public void setTrackNumber() {
        int trackNumber = 0;
        int outOf = tracks.size();

        //breaks if there is more than one track with the same name...
        for(int i = 0 ; i < outOf ; i++) {
            if (mPlayer.getMetadata().currentTrack.name.equals(tracks.get(i).name)) {
                trackNumber = i + 1;
                break;
            }
        }
        track.setText(getResources().getString(R.string.track) + " " + trackNumber + "/" + outOf);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                BaseActivity.token = response.getAccessToken();
                Config playerConfig = new Config(this, response.getAccessToken(), SpotifyConstants.cID);

                SpotifyApi api = new SpotifyApi();

                api.setAccessToken(response.getAccessToken());

                SpotifyService spotify = api.getService();

                spotify.getPlaylistTracks("hendemic", "4fWo8AAMu5GMnLtAhtPktC", new SpotifyCallback<Pager<PlaylistTrack>>() {
                    @Override
                    public void failure(SpotifyError spotifyError) {


                    }

                    @Override
                    public void success(Pager<PlaylistTrack> playlistTrackPager, Response response) {
                       tracks = new ArrayList<Track>();
                        for (PlaylistTrack playlistTrack : playlistTrackPager.items) {
                            tracks.add(playlistTrack.track);
                            Log.d("Tracks", playlistTrack.track.name);
                        }
                        //tracks.add(playlistTrackPager.items.get(0).track);

                    }
                });
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        BaseActivity.mPlayer = spotifyPlayer;
                        BaseActivity.mPlayer.addConnectionStateCallback(BaseActivity.this);
                        BaseActivity.mPlayer.addNotificationCallback(BaseActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("BaseActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    abstract int getContentViewId();
    abstract int getNavigationMenuItemId();

}
