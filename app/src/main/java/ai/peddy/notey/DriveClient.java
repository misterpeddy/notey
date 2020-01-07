package ai.peddy.notey;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DriveClient {

    final static public int REQUEST_CODE_SIGN_IN = 1;

    final static private String TAG = "DriveClient";
    final static private String PLAIN_TEXT_TYPE = "text/plain";
    final static private String GOOGLE_DOC_MIMETYPE = "application/vnd.google-apps.document";


    static private DriveClient instance;

    private Drive service;
    private Activity activity;
    private Executor mExecutor = Executors.newSingleThreadExecutor();

    private DriveClient(Activity activity){
        this.activity= activity;
    }

    public static DriveClient getInstance(Activity activity){
        if (instance == null)
            instance = new DriveClient(activity);
        if (instance.getSignedInAccount() != null)
            instance.service = instance.getDriveServiceForAccount(instance.getSignedInAccount());
        return instance;
    }


    public GoogleSignInAccount getSignedInAccount() {
        return GoogleSignIn.getLastSignedInAccount(activity);
    }

    public void requestSignIn() {
        // TODO(peddy): Figure out how to avoid the /list and change to DRIVE_FILE which is
        // a much more restrictive set of permissions
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE))
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(activity, signInOptions);
        activity.startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    public Task<GoogleSignInAccount> handleSignIn(Intent resultData) {
        return GoogleSignIn.getSignedInAccountFromIntent(resultData)
                .addOnSuccessListener(this::getDriveServiceForAccount)
                .addOnFailureListener(e -> Log.e(TAG, "Unable to sign in: ", e));
    }

    public Task<FileList> getFiles() {
        if (service == null)
            return Tasks.forException(new IllegalStateException("Drive Service not yet initialized"));

        return Tasks.call(mExecutor, () -> service.files().list().setSpaces("drive").execute());
    }

    public Task<File> uploadFile(String name, String content) {
        if (service == null)
            return Tasks.forException(new IllegalStateException("Drive Service not yet initialized"));

        ByteArrayContent contentStream = ByteArrayContent.fromString(PLAIN_TEXT_TYPE, content);
        File fileMetadata = new File()
                .setName(name)
                .setMimeType(GOOGLE_DOC_MIMETYPE);

        return Tasks.call(mExecutor, () -> service.files().create(fileMetadata, contentStream).execute());
    }

    private Drive getDriveServiceForAccount(GoogleSignInAccount account) {
        Log.i(TAG, "Connecting to Drive on behalf of user: " + account.getEmail());
        GoogleAccountCredential credentials =
                GoogleAccountCredential.usingOAuth2(
                        activity, Collections.singleton(DriveScopes.DRIVE));
        credentials.setSelectedAccount(account.getAccount());
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credentials)
                .setApplicationName(activity.getString(R.string.app_name))
                .build();
    }
}
