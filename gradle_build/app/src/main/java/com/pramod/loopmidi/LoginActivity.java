package com.pramod.loopmidi;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends Activity {

    private static final int RC_SIGN_IN = 9001;

    // ── Firebase Realtime Database URL (Asia Southeast 1) ──
    private static final String DB_URL = "https://pramod-octapad-loop-default-rtdb.asia-southeast1.firebasedatabase.app";

    // ── Your WhatsApp number (with country code, no + or spaces) ──
    private static final String WHATSAPP_NUMBER = "916268927194";
    private static final String WHATSAPP_MESSAGE = "Hello, I want to buy Pramod Octapad Loops app.";

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth       mAuth;
    private Button             btnGoogleSignIn;
    private Button             btnWhatsApp;
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
        btnWhatsApp     = (Button) findViewById(R.id.btnWhatsApp);
        txtLoginStatus  = (TextView) findViewById(R.id.txtLoginStatus);

        btnGoogleSignIn.setOnClickListener(v -> signIn());

        btnWhatsApp.setOnClickListener(v -> {
            try {
                String url = "https://wa.me/" + WHATSAPP_NUMBER
                        + "?text=" + Uri.encode(WHATSAPP_MESSAGE);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                if (txtLoginStatus != null)
                    txtLoginStatus.setText("WhatsApp nahi mila phone mein.");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkLicense(currentUser);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Device ID = Android hardware ID (unique per device)
    // ─────────────────────────────────────────────────────────────────
    private String getAndroidDeviceId() {
        return Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);
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
        if (txtLoginStatus != null) txtLoginStatus.setText("Verifying…");
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            checkLicense(user);
                        } else {
                            showNotPurchased();
                        }
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage() : "Auth failed";
                        if (txtLoginStatus != null) txtLoginStatus.setText(msg);
                        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
                    }
                });
    }

    /**
     * ONE Firebase read does everything:
     *  1. License check  — authorizedUsers/{uid} must exist
     *  2. Device check   — authorizedUsers/{uid}/deviceToken compared with this device
     *  3. Device lock    — write this device's ID (kicks out the old device automatically)
     *
     *  DB_URL explicitly set — google-services.json mein database_url nahi tha
     *  10-second timeout — agar Firebase jawab na de
     */
    private void checkLicense(FirebaseUser user) {
        if (txtLoginStatus != null) txtLoginStatus.setText("Login ho raha hai…");

        String localDeviceId = getAndroidDeviceId();

        DatabaseReference ref = FirebaseDatabase.getInstance(DB_URL)
                .getReference("authorizedUsers")
                .child(user.getUid());

        // ── 10-second timeout ────────────────────────────────────────
        Handler timeoutHandler      = new Handler(Looper.getMainLooper());
        ValueEventListener[] holder = new ValueEventListener[1];

        Runnable timeoutTask = () -> {
            if (holder[0] != null) ref.removeEventListener(holder[0]);
            runOnUiThread(() -> {
                if (txtLoginStatus != null)
                    txtLoginStatus.setText(
                            "⚠️ Server connect nahi hua (timeout).\n" +
                            "Internet check karein aur dobara try karein.");
                if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
            });
        };

        holder[0] = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                timeoutHandler.removeCallbacks(timeoutTask);

                if (!snapshot.exists()) {
                    showNotPurchased();
                    return;
                }

                // ✅ Licensed — check device lock
                DataSnapshot tokenSnap = snapshot.child("deviceToken");
                String savedDeviceId   = tokenSnap.getValue(String.class);

                if (savedDeviceId != null && !savedDeviceId.equals(localDeviceId)) {
                    showKickedOut();
                    return;
                }

                // ✅ Lock this device
                FirebaseDatabase.getInstance(DB_URL)
                        .getReference("authorizedUsers")
                        .child(user.getUid())
                        .child("deviceToken")
                        .setValue(localDeviceId);

                // Save profile + sync settings + enter app
                CloudSync.writeProfile(user.getUid(),
                        user.getEmail(), user.getDisplayName());
                CloudSync.pullSettingsThenRun(
                        LoginActivity.this, user.getUid(), LoginActivity.this::goToLoops);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                timeoutHandler.removeCallbacks(timeoutTask);
                runOnUiThread(() -> {
                    if (txtLoginStatus != null)
                        txtLoginStatus.setText("Network error. Internet check karein.");
                    if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
                });
            }
        };

        timeoutHandler.postDelayed(timeoutTask, 10000);
        ref.addListenerForSingleValueEvent(holder[0]);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Shown when account is locked to a DIFFERENT device
    // ─────────────────────────────────────────────────────────────────
    private void showKickedOut() {
        mAuth.signOut();
        mGoogleSignInClient.signOut();
        runOnUiThread(() -> {
            if (txtLoginStatus != null)
                txtLoginStatus.setText(
                        "❌ Yeh account kisi aur device pe login hai.\n" +
                        "Pehle wahan logout karein, phir yahan login karein.");
            if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    //  Shown when user has not purchased
    // ─────────────────────────────────────────────────────────────────
    private void showNotPurchased() {
        mAuth.signOut();
        mGoogleSignInClient.signOut();
        runOnUiThread(() -> {
            if (txtLoginStatus != null)
                txtLoginStatus.setText("❌ App kharidi nahi hai.\nKharidne ke liye WhatsApp karein.");
            if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
            if (btnWhatsApp != null) btnWhatsApp.setVisibility(View.VISIBLE);
        });
    }

    private void goToLoops() {
        Intent intent = new Intent(this, LoopsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
