package com.pramod.loopmidi;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends Activity {

    private static final int RC_SIGN_IN = 9001;

    // ── Firebase Realtime Database URL (Asia Southeast 1) ──
    private static final String DB_URL =
            "https://pramod-octapad-loop-default-rtdb.asia-southeast1.firebasedatabase.app";

    // ── Your WhatsApp number (with country code, no + or spaces) ──
    private static final String WHATSAPP_NUMBER = "916268927194";
    private static final String WHATSAPP_MESSAGE = "Hello, I want to buy Pramod Octapad Loops app.";

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth       mAuth;
    private Button             btnGoogleSignIn;
    private Button             btnWhatsApp;
    private TextView           txtLoginStatus;

    // ── Real-time approval listener (waits for admin to activate the user) ──
    private ValueEventListener approvalListener;
    private DatabaseReference  approvalRef;

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
            if (isLicenseCached()) {
                // Already verified before on this device — enter instantly,
                // don't make the user wait for a network round-trip.
                goToLoops();
                // Quietly re-check license/device-lock in the background so
                // piracy protection (device-lock kickout) still keeps working.
                revalidateLicenseSilently(currentUser);
            } else {
                checkLicense(currentUser);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up real-time approval listener
        clearApprovalListener();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Local "already verified" cache — avoids re-showing the login
    //  screen / re-fetching data on every app open once the license
    //  has been confirmed once on this device.
    // ─────────────────────────────────────────────────────────────────
    private SharedPreferences authPrefs() {
        return getSharedPreferences("AuthPrefs", MODE_PRIVATE);
    }

    private boolean isLicenseCached() {
        return authPrefs().getBoolean("licensed_ok", false);
    }

    private void setLicenseCached(boolean value) {
        authPrefs().edit().putBoolean("licensed_ok", value).apply();
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
                            showPurchaseRequired(null);
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
                    // ❌ Not yet activated by admin — send pending request and wait
                    waitForApproval(user);
                    return;
                }

                // ── Per-APK access check ──────────────────────────────────────
                // allowedApps field nahi hai → purana user, teeno allowed (backward compat)
                // allowedApps hai → current flavor ka value check karo
                DataSnapshot appsSnap = snapshot.child("allowedApps");
                if (appsSnap.exists()) {
                    Boolean allowed = appsSnap.child(BuildConfig.FLAVOR).getValue(Boolean.class);
                    if (allowed == null || !allowed) {
                        runOnUiThread(() -> {
                            if (txtLoginStatus != null)
                                txtLoginStatus.setText(
                                        "❌ Aapko \"" + flavorDisplayName(BuildConfig.FLAVOR)
                                        + "\" APK ka access nahi hai.\n\n"
                                        + "Admin se contact karein aur sahi APK ka access maangein.");
                            if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
                            if (btnWhatsApp != null) btnWhatsApp.setVisibility(View.VISIBLE);
                        });
                        return;
                    }
                }

                // ✅ Licensed + APK allowed — check device lock
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

                // Remember that this device is verified so future app opens
                // can skip straight in without waiting on the network.
                setLicenseCached(true);

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
    //  Wait for admin approval:
    //  1. Write pending request to Firebase (user stays signed in so
    //     the write succeeds — token is not revoked early)
    //  2. Show "waiting" UI
    //  3. Listen to authorizedUsers/{uid} in real-time — as soon as
    //     admin clicks "Activate", the listener fires and user enters
    //     the app automatically without re-signing-in
    // ─────────────────────────────────────────────────────────────────
    private void waitForApproval(FirebaseUser user) {
        setLicenseCached(false);

        // Step 1: Write pending request while user is still authenticated
        savePendingRequest(user);

        // Step 2: Show waiting UI
        runOnUiThread(() -> {
            if (txtLoginStatus != null)
                txtLoginStatus.setText(
                        "⏳ Aapki login request admin ko bheji gayi hai.\n\n" +
                        "Admin approve karne par aap automatically app mein\n" +
                        "enter ho jayenge — yahan raho!\n\n" +
                        "Kharidne ke liye WhatsApp par message karein.");
            if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(false);
            if (btnWhatsApp != null) btnWhatsApp.setVisibility(View.VISIBLE);
        });

        // Step 3: Real-time listener — fires the moment admin activates this user
        clearApprovalListener();

        approvalRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("authorizedUsers")
                .child(user.getUid());

        approvalListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // 🎉 Admin ne approve kar diya!
                    clearApprovalListener();
                    runOnUiThread(() -> {
                        if (txtLoginStatus != null)
                            txtLoginStatus.setText("✅ Admin ne approve kar diya! App khul rahi hai…");
                        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(false);
                    });
                    // Re-run checkLicense to handle device-lock and enter app
                    checkLicense(user);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Network issue — let user retry manually
                runOnUiThread(() -> {
                    if (txtLoginStatus != null)
                        txtLoginStatus.setText(
                                "Network error. Internet check karein.\n" +
                                "Dobara login karein.");
                    if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
                });
            }
        };

        approvalRef.addValueEventListener(approvalListener);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save pending request so admin can approve from admin panel
    //  (called while user is still authenticated so the DB write succeeds)
    // ─────────────────────────────────────────────────────────────────
    private void savePendingRequest(FirebaseUser user) {
        if (user == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("email",       user.getEmail()       != null ? user.getEmail()       : "");
        data.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "");
        data.put("timestamp",   System.currentTimeMillis());
        FirebaseDatabase.getInstance(DB_URL)
                .getReference("pendingRequests")
                .child(user.getUid())
                .setValue(data);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Remove and null the approval listener safely
    // ─────────────────────────────────────────────────────────────────
    private void clearApprovalListener() {
        if (approvalListener != null && approvalRef != null) {
            approvalRef.removeEventListener(approvalListener);
        }
        approvalListener = null;
        approvalRef      = null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Shown when account is locked to a DIFFERENT device
    // ─────────────────────────────────────────────────────────────────
    private void showKickedOut() {
        setLicenseCached(false);
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
    //  Fallback: user is null (edge case) — just show sign-in option
    // ─────────────────────────────────────────────────────────────────
    private void showPurchaseRequired(FirebaseUser user) {
        setLicenseCached(false);
        runOnUiThread(() -> {
            if (txtLoginStatus != null)
                txtLoginStatus.setText(
                        "❌ Login nahi ho saka.\nDobara try karein ya WhatsApp karein.");
            if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
            if (btnWhatsApp != null) btnWhatsApp.setVisibility(View.VISIBLE);
        });
    }

    private static String flavorDisplayName(String flavor) {
        switch (flavor) {
            case "full":  return "Full";
            case "loops": return "Loops";
            case "drums": return "Drums";
            default:      return flavor;
        }
    }

    private void goToLoops() {
        // BuildConfig.FLAVOR se decide karo kahan jaana hai:
        //   "drums"  → sirf MainActivity (Drum Pad)
        //   "loops"  → sirf LoopsActivity
        //   "full"   → dialog: user choose kare Loops ya Octapad
        if (BuildConfig.FLAVOR.equals("drums")) {
            launchActivity(MainActivity.class);
        } else if (BuildConfig.FLAVOR.equals("full")) {
            showModeDialog();
        } else {
            launchActivity(LoopsActivity.class);
        }
    }

    private void launchActivity(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showModeDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Mode Select karo")
                .setMessage("Aap kaunsa mode use karna chahte hain?")
                .setCancelable(false)
                .setPositiveButton("🎵 Loops", (dialog, which) -> launchActivity(LoopsActivity.class))
                .setNegativeButton("🥁 Octapad", (dialog, which) -> launchActivity(MainActivity.class))
                .show();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Runs the same license + device-lock check as checkLicense(), but
    //  silently in the background (no UI, no goToLoops) since the user
    //  has already been let into the app via the cached license.
    //  Keeps the anti-piracy device lock enforced without ever blocking
    //  or delaying app startup for a legitimate, already-verified user.
    // ─────────────────────────────────────────────────────────────────
    private void revalidateLicenseSilently(final FirebaseUser user) {
        final String localDeviceId = getAndroidDeviceId();

        DatabaseReference ref = FirebaseDatabase.getInstance(DB_URL)
                .getReference("authorizedUsers")
                .child(user.getUid());

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // License was revoked server-side — sign out quietly.
                    // Next app open will show the login screen again.
                    setLicenseCached(false);
                    mAuth.signOut();
                    mGoogleSignInClient.signOut();
                    return;
                }

                String savedDeviceId = snapshot.child("deviceToken").getValue(String.class);
                if (savedDeviceId != null && !savedDeviceId.equals(localDeviceId)) {
                    // Account got locked to a different device — sign out quietly.
                    setLicenseCached(false);
                    mAuth.signOut();
                    mGoogleSignInClient.signOut();
                    return;
                }

                // Still valid on this device — refresh the device lock.
                FirebaseDatabase.getInstance(DB_URL)
                        .getReference("authorizedUsers")
                        .child(user.getUid())
                        .child("deviceToken")
                        .setValue(localDeviceId);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Network hiccup — ignore, keep running with the cached license.
            }
        });
    }
}
