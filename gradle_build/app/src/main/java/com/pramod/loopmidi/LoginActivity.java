package com.pramod.loopmidi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends Activity {

    private static final int RC_SIGN_IN = 9001;

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth       mAuth;
    private Button             btnGoogleSignIn;
    private TextView           txtLoginStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleSignIn = (Button) findViewById(R.id.btnGoogleSignIn);
        txtLoginStatus  = (TextView) findViewById(R.id.txtLoginStatus);

        btnGoogleSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If already signed in, skip login screen
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToLoops();
        }
    }

    private void signIn() {
        if (txtLoginStatus != null) txtLoginStatus.setText("Signing in…");
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(false);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                if (txtLoginStatus != null)
                    txtLoginStatus.setText("Sign-in failed: " + e.getStatusCode());
                if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        if (txtLoginStatus != null) txtLoginStatus.setText("Authenticating…");
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            CloudSync.writeProfile(user.getUid(),
                                    user.getEmail(), user.getDisplayName());
                            // Pull cloud settings into local prefs, then launch LoopsActivity
                            CloudSync.pullSettingsThenRun(LoginActivity.this,
                                    user.getUid(), this::goToLoops);
                        } else {
                            goToLoops();
                        }
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage() : "Auth failed";
                        if (txtLoginStatus != null) txtLoginStatus.setText(msg);
                        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
                    }
                });
    }

    private void goToLoops() {
        Intent intent = new Intent(this, LoopsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
