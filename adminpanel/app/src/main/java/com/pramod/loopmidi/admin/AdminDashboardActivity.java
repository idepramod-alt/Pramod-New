package com.pramod.loopmidi.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

    // Checkboxes for the manual-activate section
    private CheckBox cbManualFull;
    private CheckBox cbManualLoops;
    private CheckBox cbManualDrums;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = AdminFirebaseApp.auth(this);
        pendingRef = AdminFirebaseApp.database(this).getReference("pendingRequests");
        usersRef   = AdminFirebaseApp.database(this).getReference("authorizedUsers");
        buildUi();
        listenPending();
        listenActive();
    }

    // ── UI construction (no XML) ──────────────────────────────────────────────

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
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button btnLogout = new Button(this);
        btnLogout.setText("Logout");
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
        });
        header.addView(btnLogout);
        root.addView(header, matchWidth(0));

        // Pending section
        root.addView(sectionHeader("Pending Requests (naye users, abhi tak activate nahi)"), matchWidth(dp(20)));
        pendingContainer = new LinearLayout(this);
        pendingContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(pendingContainer, matchWidth(dp(4)));
        txtPendingEmpty = emptyText("Koi pending request nahi hai.");
        root.addView(txtPendingEmpty, matchWidth(dp(4)));

        // Active section
        root.addView(sectionHeader("Active / Licensed Users"), matchWidth(dp(24)));
        activeContainer = new LinearLayout(this);
        activeContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(activeContainer, matchWidth(dp(4)));
        txtActiveEmpty = emptyText("Koi active user nahi hai.");
        root.addView(txtActiveEmpty, matchWidth(dp(4)));

        // Manual activate section
        root.addView(sectionHeader("Manually Activate by UID (fallback)"), matchWidth(dp(24)));
        buildManualSection(root);

        setContentView(scroll);
    }

    private void buildManualSection(LinearLayout root) {
        // UID input
        LinearLayout uidRow = new LinearLayout(this);
        uidRow.setOrientation(LinearLayout.HORIZONTAL);
        editManualUid = new EditText(this);
        editManualUid.setHint("Firebase User UID paste karein");
        editManualUid.setTextColor(0xffffffff);
        editManualUid.setHintTextColor(0xff888888);
        uidRow.addView(editManualUid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(uidRow, matchWidth(dp(4)));

        // APK checkboxes (all checked by default)
        TextView apkLabel = new TextView(this);
        apkLabel.setText("Kis APK ka access dena hai:");
        apkLabel.setTextColor(0xffaaaaaa);
        apkLabel.setTextSize(11);
        apkLabel.setPadding(0, dp(4), 0, dp(2));
        root.addView(apkLabel, matchWidth(0));

        LinearLayout cbRow = new LinearLayout(this);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbManualFull  = makeCheckBox("Full",  true);
        cbManualLoops = makeCheckBox("Loops", true);
        cbManualDrums = makeCheckBox("Drums", true);
        cbRow.addView(cbManualFull);
        cbRow.addView(cbManualLoops);
        cbRow.addView(cbManualDrums);
        root.addView(cbRow, matchWidth(dp(2)));

        Button btnManualActivate = new Button(this);
        btnManualActivate.setText("Activate");
        btnManualActivate.setOnClickListener(v -> manualActivate());
        root.addView(btnManualActivate, matchWidth(dp(4)));
    }

    // ── Pending list ──────────────────────────────────────────────────────────

    private void listenPending() {
        pendingRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                pendingContainer.removeAllViews();
                boolean any = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    any = true;
                    final String uid = child.getKey();
                    String email       = child.child("email").getValue(String.class);
                    String displayName = child.child("displayName").getValue(String.class);
                    Long ts            = child.child("timestamp").getValue(Long.class);
                    String subLine     = ts != null ? "Request: " + new java.util.Date(ts) : null;
                    pendingContainer.addView(buildPendingRow(uid, email, displayName, subLine));
                }
                txtPendingEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
            }
            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Pending list error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Pending row — 3 APK checkboxes (Full / Loops / Drums, all ticked by default)
     * so admin can choose which APKs to grant before clicking Activate.
     */
    private View buildPendingRow(String uid, String email, String displayName, String subLine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(0xff1c1c1c);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        row.setLayoutParams(matchWidth(dp(6)));

        addUserInfoViews(row, uid, email, displayName, subLine);

        // APK selection label
        TextView apkLabel = new TextView(this);
        apkLabel.setText("Kis APK ka access dena hai:");
        apkLabel.setTextColor(0xffaaaaaa);
        apkLabel.setTextSize(11);
        apkLabel.setPadding(0, dp(6), 0, dp(2));
        row.addView(apkLabel);

        // Checkboxes — all ticked by default
        LinearLayout cbRow = new LinearLayout(this);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox cbFull  = makeCheckBox("Full",  true);
        CheckBox cbLoops = makeCheckBox("Loops", true);
        CheckBox cbDrums = makeCheckBox("Drums", true);
        cbRow.addView(cbFull);
        cbRow.addView(cbLoops);
        cbRow.addView(cbDrums);
        row.addView(cbRow);

        // Action buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        Button btnActivate = new Button(this);
        btnActivate.setText("Activate");
        btnActivate.setOnClickListener(v ->
                activateUser(uid, email, displayName,
                        cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked()));
        btnRow.addView(btnActivate, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnReject = new Button(this);
        btnReject.setText("Reject");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(6);
        btnReject.setOnClickListener(v -> pendingRef.child(uid).removeValue());
        btnRow.addView(btnReject, rp);

        row.addView(btnRow);
        return row;
    }

    // ── Active list ───────────────────────────────────────────────────────────

    private void listenActive() {
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                activeContainer.removeAllViews();
                boolean any = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    any = true;
                    final String uid       = child.getKey();
                    String email           = child.child("email").getValue(String.class);
                    String displayName     = child.child("displayName").getValue(String.class);
                    String deviceToken     = child.child("deviceToken").getValue(String.class);

                    // allowedApps — agar field nahi hai to purana user (teeno allowed)
                    DataSnapshot appsSnap  = child.child("allowedApps");
                    boolean hasFull  = !appsSnap.exists()
                            || Boolean.TRUE.equals(appsSnap.child("full").getValue(Boolean.class));
                    boolean hasLoops = !appsSnap.exists()
                            || Boolean.TRUE.equals(appsSnap.child("loops").getValue(Boolean.class));
                    boolean hasDrums = !appsSnap.exists()
                            || Boolean.TRUE.equals(appsSnap.child("drums").getValue(Boolean.class));

                    String appsLine = "APK: "
                            + (hasFull  ? "✅Full "  : "❌Full ")
                            + (hasLoops ? "✅Loops " : "❌Loops ")
                            + (hasDrums ? "✅Drums"  : "❌Drums");
                    String deviceLine = !TextUtils.isEmpty(deviceToken) ? "🔒 Device locked" : "🔓 Unlocked";

                    activeContainer.addView(buildActiveRow(
                            uid, email, displayName,
                            appsLine + "  |  " + deviceLine,
                            deviceToken, hasFull, hasLoops, hasDrums));
                }
                txtActiveEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
            }
            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Active list error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Active row — shows current APK access + Deactivate / Edit Apps / Unlock Device buttons.
     */
    private View buildActiveRow(String uid, String email, String displayName,
                                String subLine, String deviceToken,
                                boolean curFull, boolean curLoops, boolean curDrums) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(0xff1c1c1c);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        row.setLayoutParams(matchWidth(dp(6)));

        addUserInfoViews(row, uid, email, displayName, subLine);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        // Deactivate
        Button btnDeactivate = new Button(this);
        btnDeactivate.setText("Deactivate");
        btnDeactivate.setOnClickListener(v -> confirmDeactivate(uid));
        btnRow.addView(btnDeactivate, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Edit Apps
        Button btnEdit = new Button(this);
        btnEdit.setText("Edit Apps");
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ep.leftMargin = dp(6);
        btnEdit.setOnClickListener(v ->
                showEditAppsDialog(uid, email, displayName, curFull, curLoops, curDrums));
        btnRow.addView(btnEdit, ep);

        // Unlock Device (only shown if device is locked)
        if (!TextUtils.isEmpty(deviceToken)) {
            Button btnUnlock = new Button(this);
            btnUnlock.setText("Unlock");
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            up.leftMargin = dp(6);
            btnUnlock.setOnClickListener(v ->
                    usersRef.child(uid).child("deviceToken").removeValue());
            btnRow.addView(btnUnlock, up);
        }

        row.addView(btnRow);
        return row;
    }

    /** Dialog: change which APKs this user can access (without resetting device lock). */
    private void showEditAppsDialog(String uid, String email, String displayName,
                                    boolean curFull, boolean curLoops, boolean curDrums) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        CheckBox cbFull  = makeCheckBox("Full  (com.pramod.loopmidi)",        curFull);
        CheckBox cbLoops = makeCheckBox("Loops (com.pramod.loopmidi.loops)",  curLoops);
        CheckBox cbDrums = makeCheckBox("Drums (com.pramod.loopmidi.drums)",  curDrums);
        layout.addView(cbFull);
        layout.addView(cbLoops);
        layout.addView(cbDrums);

        new AlertDialog.Builder(this)
                .setTitle("APK Access — " + (!TextUtils.isEmpty(email) ? email : uid))
                .setView(layout)
                .setPositiveButton("Save", (d, w) ->
                        saveAllowedApps(uid, email,
                                cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Firebase actions ──────────────────────────────────────────────────────

    /** New user activate — saves allowedApps from checkboxes. */
    private void activateUser(String uid, String email, String displayName,
                              boolean full, boolean loops, boolean drums) {
        Map<String, Object> data = new HashMap<>();
        data.put("email",       email != null ? email : "");
        data.put("displayName", displayName != null ? displayName : "");
        data.put("activatedAt", System.currentTimeMillis());

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  full);
        appsMap.put("loops", loops);
        appsMap.put("drums", drums);
        data.put("allowedApps", appsMap);

        usersRef.child(uid).setValue(data);
        pendingRef.child(uid).removeValue();
        Toast.makeText(this, "Activated: " + (email != null ? email : uid), Toast.LENGTH_SHORT).show();
    }

    /** Update only allowedApps — does NOT reset deviceToken or activatedAt. */
    private void saveAllowedApps(String uid, String email,
                                 boolean full, boolean loops, boolean drums) {
        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  full);
        appsMap.put("loops", loops);
        appsMap.put("drums", drums);
        usersRef.child(uid).child("allowedApps").setValue(appsMap);
        Toast.makeText(this, "Access updated: " + (email != null ? email : uid), Toast.LENGTH_SHORT).show();
    }

    private void confirmDeactivate(String uid) {
        new AlertDialog.Builder(this)
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

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  cbManualFull.isChecked());
        appsMap.put("loops", cbManualLoops.isChecked());
        appsMap.put("drums", cbManualDrums.isChecked());
        data.put("allowedApps", appsMap);

        usersRef.child(uid).setValue(data);
        editManualUid.setText("");
        Toast.makeText(this, "Activated by UID.", Toast.LENGTH_SHORT).show();
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private void addUserInfoViews(LinearLayout row, String uid, String email,
                                  String displayName, String subLine) {
        TextView txtName = new TextView(this);
        txtName.setText(!TextUtils.isEmpty(displayName)
                ? displayName : (!TextUtils.isEmpty(email) ? email : uid));
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
    }

    private CheckBox makeCheckBox(String label, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setChecked(checked);
        cb.setTextColor(0xffffffff);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(16);
        cb.setLayoutParams(p);
        return cb;
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
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
