package ai.peddy.notey;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.spotify.protocol.types.Track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private Intent startServiceIntent;
    private DriveClient drive;
    private SharedPreferences sharedPreferences;

    private TextView signedInAccountTv;
    private ListView localNotesLv;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.google_sign_in_btn).setOnClickListener(view -> drive.requestSignIn());
        signedInAccountTv = findViewById(R.id.google_sign_in_account_tv);
        localNotesLv = findViewById(R.id.local_notes_lv);

        sharedPreferences = getSharedPreferences(
                getString(R.string.session_markers_prefs_file), MODE_PRIVATE);

        Map<String, Set<Long>> notes = getAllNotes();
        localNotesLv.setAdapter(new SharedPreferencesListViewAdapter(notes));
    }

    /*
     * Called when a "Upload Note" button is clicked
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

    @Override
    protected void onStart() {
        super.onStart();

        startServiceIntent = new Intent(this, NoteyService.class);
        startService(startServiceIntent);

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
                if (resultCode == Activity.RESULT_OK && intentData != null)
                    drive = DriveClient.getInstance(this);
                drive.handleSignIn(intentData).addOnSuccessListener(account -> {
                    signedInAccountTv.setText(account.getEmail());
                });
                break;
        }
    }

}
