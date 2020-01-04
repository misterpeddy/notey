package ai.peddy.notey;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private Intent startServiceIntent;
    private DriveClient drive;
    private TextView signedInAccountTv;
    private Button exportNotesBtn;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.google_sign_in_btn).setOnClickListener(view -> drive.requestSignIn());
        signedInAccountTv = findViewById(R.id.google_sign_in_account_tv);
        exportNotesBtn = findViewById(R.id.export_notes_btn);
    }

    @Override
    protected void onStart() {
        super.onStart();

        startServiceIntent = new Intent(this, NoteyService.class);
        startService(startServiceIntent);

        drive = DriveClient.getInstance(this);
        if (drive.getSignedInAccount() != null)
            signedInAccountTv.setText(drive.getSignedInAccount().getEmail());

        exportNotesBtn.setOnClickListener(view -> {
            Task<FileList> fileListTask = drive.getFiles();
            fileListTask
                    .addOnSuccessListener(fileList -> {
                        for (File f : fileList.getFiles()) {
                            Log.i(TAG, f.getName());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Could not get files:", e);
                        Toast.makeText(
                                this, getString(R.string.toast_text_export_failed),
                                Toast.LENGTH_SHORT).show();
                    });
        });
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
