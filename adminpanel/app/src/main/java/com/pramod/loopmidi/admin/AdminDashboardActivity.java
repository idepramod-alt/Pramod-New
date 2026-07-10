package com.pramod.loopmidi.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference pendingRef;
    private DatabaseReference usersRef;

    private LinearLayout pendingContainer;
    private LinearLayout activeContainer;
    private TextView txtPendingEmpty;
    private TextView txtActiveEmpty;
    private EditText editManualUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = AdminFirebaseApp.auth(this);
        pendingRef = AdminFirebaseApp.database(this).getReference("pendingRequests");
        usersRef = AdminFirebaseApp.database(this).getReference("authorizedUsers");
        buildUi();
        listenPending();
        listenActive();
    }

    // ── UI construction (no XML — keeps this a self-contained single file) ──

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff111111);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(32), pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Admin Panel");
        title.setTextColor(0xff00afff);
        title.setTextSize(20);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleParams);
        Button btnLogout = new Button(this);
        btnLogout.setText("Logout");
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
        });
        header.addView(btnLogout);
        root.addView(header, matchWidth(0));

        // ── Pending requests section ──
        root.addView(sectionHeader("Pending Requests (naye users, abhi tak activate nahi)"), matchWidth(dp(20)));
        pendingContainer = new LinearLayout(this);
        pendingContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(pendingContainer, matchWidth(dp(4)));
        txtPendingEmpty = emptyText("Koi pending request nahi hai.");
        root.addView(txtPendingEmpty, matchWidth(dp(4)));

        // ── Active / licensed users section ──
        root.addView(sectionHeader("Active / Licensed Users"), matchWidth(dp(24)));
        activeContainer = new LinearLayout(this);
        activeContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeContainer, matchWidth(dp(4)));
        txtActiveEmpty = emptyText("Koi active user nahi hai.");
        root.addView(txtActiveEmpty, matchWidth(dp(4)));

        // ── Manual activate-by-UID fallback ──
        root.addView(sectionHeader("Manually Activate by UID (fallback)"), matchWidth(dp(24)));
        LinearLayout manualRow = new LinearLayout(this);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        editManualUid = new EditText(this);
        editManualUid.setHint("Firebase User UID paste karein");
        editManualUid.setTextColor(0xffffffff);
        editManualUid.setHintTextColor(0xff888888);
        manualRow.addView(editManualUid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button btnManualActivate = new Button(this);
        btnManualActivate.setText("Activate");
        btnManualActivate.setOnClickListener(v -> manualActivate());
        manualRow.addView(btnManualActivate);
        root.addView(manualRow, matchWidth(dp(4)));

        setContentView(scroll);
    }

    private TextView sectionHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xffffff00);
        t.setTextSize(14);
        return t;
    }

    private TextView emptyText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xff888888);
        t.setTextSize(12);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private LinearLayout.LayoutParams matchWidth(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = topMargin;
        return p;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    // ── Row builder shared by both lists ──

    private View buildRow(String uid, String email, String displayName, String subLine,
                          String primaryLabel, Runnable onPrimary,
                          String secondaryLabel, Runnable onSecondary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(0xff1c1c1c);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rowParams = matchWidth(dp(6));
        row.setLayoutParams(rowParams);

        TextView txtName = new TextView(this);
        txtName.setText(!TextUtils.isEmpty(displayName) ? displayName : (!TextUtils.isEmpty(email) ? email : uid));
        txtName.setTextColor(0xffffffff);
        txtName.setTextSize(14);
        row.addView(txtName);

        if (!TextUtils.isEmpty(email)) {
            TextView txtEmail = new TextView(this);
            txtEmail.setText(email);
            txtEmail.setTextColor(0xff00afff);
            txtEmail.setTextSize(11);
            row.addView(txtEmail);
        }

        TextView txtUid = new TextView(this);
        txtUid.setText("UID: " + uid);
        txtUid.setTextColor(0xff666666);
        txtUid.setTextSize(10);
        row.addView(txtUid);

        if (!TextUtils.isEmpty(subLine)) {
            TextView txtSub = new TextView(this);
            txtSub.setText(subLine);
            txtSub.setTextColor(0xffaaaaaa);
            txtSub.setTextSize(11);
            row.addView(txtSub);
        }

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        Button btnPrimary = new Button(this);
        btnPrimary.setText(primaryLabel);
        btnPrimary.setOnClickListener(v -> onPrimary.run());
        btnRow.addView(btnPrimary, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (secondaryLabel != null) {
            Button btnSecondary = new Button(this);
            btnSecondary.setText(secondaryLabel);
            LinearLayout.LayoutParams secParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            secParams.leftMargin = dp(6);
            btnSecondary.setOnClickListener(v -> onSecondary.run());
            btnRow.addView(btnSecondary, secParams);
        }

        row.addView(btnRow);
        return row;
    }

    // ── Pending requests (users who logged in but aren't licensed yet) ──

    private void listenPending() {
        pendingRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                pendingContainer.removeAllViews();
                boolean any = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    any = true;
                    final String uid = child.getKey();
                    String email = child.child("email").getValue(String.class);
                    String displayName = child.child("displayName").getValue(String.class);
                    Long timestamp = child.child("timestamp").getValue(Long.class);
                    String subLine = timestamp != null
                            ? "Request time: " + new java.util.Date(timestamp) : null;

                    View row = buildRow(uid, email, displayName, subLine,
                            "Activate",
                            () -> activateUser(uid, email, displayName),
                            "Reject",
                            () -> pendingRef.child(uid).removeValue());
                    pendingContainer.addView(row);
                }
                txtPendingEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Pending list load error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Active / licensed users ──

    private void listenActive() {
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                activeContainer.removeAllViews();
                boolean any = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    any = true;
                    final String uid = child.getKey();
                    String email = child.child("email").getValue(String.class);
                    String displayName = child.child("displayName").getValue(String.class);
                    String deviceToken = child.child("deviceToken").getValue(String.class);
                    String subLine = !TextUtils.isEmpty(deviceToken)
                            ? "Device locked" : "Kisi device pe lock nahi (agla login lock karega)";

                    View row = buildRow(uid, email, displayName, subLine,
                            "Deactivate",
                            () -> confirmDeactivate(uid),
                            !TextUtils.isEmpty(deviceToken) ? "Unlock Device" : null,
                            () -> usersRef.child(uid).child("deviceToken").removeValue());
                    activeContainer.addView(row);
                }
                txtActiveEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Active list load error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Actions ──

    private void activateUser(String uid, String email, String displayName) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email != null ? email : "");
        data.put("displayName", displayName != null ? displayName : "");
        data.put("activatedAt", System.currentTimeMillis());
        usersRef.child(uid).setValue(data);
        pendingRef.child(uid).removeValue();
        Toast.makeText(this, "Activated: " + (email != null ? email : uid), Toast.LENGTH_SHORT).show();
    }

    private void confirmDeactivate(String uid) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Deactivate karein?")
                .setMessage("Yeh user turant logout ho jayega aur app dobara nahi khulega jab tak aap use phir se activate na karein.")
                .setPositiveButton("Deactivate", (dialog, which) -> {
                    usersRef.child(uid).removeValue();
                    Toast.makeText(this, "Deactivated.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void manualActivate() {
        String uid = editManualUid.getText().toString().trim();
        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "UID daalein.", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("activatedAt", System.currentTimeMillis());
        data.put("note", "manually added");
        usersRef.child(uid).setValue(data);
        editManualUid.setText("");
        Toast.makeText(this, "Activated by UID.", Toast.LENGTH_SHORT).show();
    }
}
