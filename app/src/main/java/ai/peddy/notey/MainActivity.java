package ai.peddy.notey;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

public class MainActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 1002;

    private static final String TAG = "MainActivity";

    private DriveClient drive;
    private SharedPreferences sharedPreferences;
    private MediaProjectionManager mediaProjectionManager;

    private TextView signedInAccountTv;
    private ListView localNotesLv;
    private Button captureAudioBtn;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.google_sign_in_btn).setOnClickListener(view -> drive.requestSignIn());
        signedInAccountTv = findViewById(R.id.google_sign_in_account_tv);

        sharedPreferences = getSharedPreferences(
                getString(R.string.session_markers_prefs_file), MODE_PRIVATE);

        Map<String, Set<Long>> notes = getAllNotes();
        localNotesLv = findViewById(R.id.local_notes_lv);
        localNotesLv.setAdapter(new SharedPreferencesListViewAdapter(notes));

        captureAudioBtn = findViewById(R.id.capture_audio_btn);
        if (AudioRecorder.getInstance(getApplicationContext()).isRecording())
            captureAudioBtn.setText(R.string.stop_audio_capture_button_text);

        if (mediaProjectionManager == null)
            mediaProjectionManager = (MediaProjectionManager) getSystemService
                    (Context.MEDIA_PROJECTION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = new String[] { Manifest.permission.RECORD_AUDIO };
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_RECORD_AUDIO_PERMISSION);
        }
    }

    /*
     * Called when a "Upload Note" button is clicked.
     */
    public void uploadNote(View view) {
        String key = (String) view.getTag();
        String name = key;

        // Check for backward-compatibility with notes exported before adding delim
        if (key.split(NoteyService.TRACK_URI_NAME_DELIM).length == 2)
            name = key.split(NoteyService.TRACK_URI_NAME_DELIM)[1];

        drive.uploadFile(name, getSerializedNote(key))
                .addOnSuccessListener(file -> {
                    Log.i(TAG, "File created: " + file.getName());
                    Toast.makeText(this, "Uploaded file: " + file.getName(), Toast.LENGTH_SHORT).show();
                    deleteNote(key);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not create file: " + e);
                    Toast.makeText(this, "File upload failed", Toast.LENGTH_SHORT).show();
                });
    }

    /*
     * Called when the "Start/Stop Audio Capture" button is clicked.
     */
    public void toggleAudioCapture(View view) {
        AudioRecorder recorder = AudioRecorder.getInstance(getApplicationContext());
        if (recorder.isRecording()) {
            recorder.stopRecording();
            captureAudioBtn.setText(R.string.start_audio_capture_button_text);
        } else {
            Intent intent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_CODE_MEDIA_PROJECTION);
            captureAudioBtn.setText(R.string.stop_audio_capture_button_text);
        }
    }

    public void takeNote(View view) {
        AudioRecorder.getInstance(this).captureWindow();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent noteyServiceIntent = new Intent(this, NoteyService.class);
        startForegroundService(noteyServiceIntent);

        drive = DriveClient.getInstance(this);
        if (drive.getSignedInAccount() != null)
            signedInAccountTv.setText(drive.getSignedInAccount().getEmail());
    }

    /*
     * Returns a map of track name-URIs to Note positions
     */
    public Map<String, Set<Long>> getAllNotes() {
        Map<String, Set<Long>> notesMap = new HashMap<>();

        Map<String, ?> entries = sharedPreferences.getAll();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            if (entry.getValue() instanceof Set) {
                Set<Long> longSet =
                        ((Set<String>) entry.getValue())
                                .stream().map(Long::parseLong)
                                .collect(Collectors.toSet());
                notesMap.put(entry.getKey(), longSet);
            }
        }

        /*
        TODO(peddy): use streams for outer iteration as well
        return entries.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> ((Set<String>) entry).getValue()
                                .stream()
                                .map(Long::parseLong)
                                .collect(Collectors.toSet())
                ));
        */

        return notesMap;
    }


    private String getSerializedNote(String key) {
        Set<String> notes = new HashSet<>();
        notes = sharedPreferences.getStringSet(key, notes);
        return String.join("\n", notes.toArray(new String[notes.size()]));
    }

    private void deleteNote(String key) {
        sharedPreferences.edit().remove(key).apply();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intentData) {
        switch (requestCode) {
            // Handle callback from Google sign in
            case DriveClient.REQUEST_CODE_SIGN_IN:
                if (resultCode == RESULT_OK && intentData != null)
                    drive = DriveClient.getInstance(this);
                drive.handleSignIn(intentData).addOnSuccessListener(account -> {
                    signedInAccountTv.setText(account.getEmail());
                });
                break;
            case REQUEST_CODE_MEDIA_PROJECTION:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Obtained permission to record screen");
                    AudioRecorder recorder = AudioRecorder.getInstance(getApplicationContext());
                    if (recorder.initialize(resultCode, intentData)) {
                        recorder.startRecording();
                    } else {
                        Toast.makeText(this, "Could not start recording audio", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "Did not obtain permission to record audio");
                }
        }
    }

    public void onRequestPermissionsResults(int requestCode, String[] permissions,
                                            int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Audio record permission granted by user.");
                }  else {

                    Log.i(TAG, "Audio record permission granted by user.");
                    Toast.makeText(this, "Notey needs to record audio to take notes", Toast.LENGTH_SHORT).show();
                }
        }
    }
}
