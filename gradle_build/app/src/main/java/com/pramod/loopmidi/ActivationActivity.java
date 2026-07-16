package com.pramod.loopmidi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * ActivationActivity — license/activation gate.
 *
 * Shows an activation-code entry field.  When a valid code is entered the
 * "activated" flag is written to SharedPreferences and the user is forwarded
 * to LoopsActivity.  If the device is already activated this screen is
 * skipped automatically.
 */
public class ActivationActivity extends Activity {

    private static final String PREFS        = "LoopPrefs";
    private static final String KEY_ACTIVATED = "activated";
    // Hard-coded demo code — replace with server-side validation as needed.
    private static final String DEMO_CODE    = "PRAMOD2024";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If already activated, go straight to loops.
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ACTIVATED, false)) {
            launchLoops();
            return;
        }

        buildUI(prefs);
    }

    // ── UI ────────────────────────────────────────────────────────────────

    private void buildUI(SharedPreferences prefs) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(64, 120, 64, 64);
        root.setBackgroundColor(0xFF000000);

        TextView title = new TextView(this);
        title.setText("Pramod Octapad Loops");
        title.setTextSize(22f);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Enter your activation code to continue:");
        sub.setTextSize(14f);
        sub.setTextColor(0xFFAAAAAA);
        sub.setPadding(0, 0, 0, 24);
        root.addView(sub);

        final EditText codeField = new EditText(this);
        codeField.setHint("Activation code");
        codeField.setHintTextColor(0xFF888888);
        codeField.setTextColor(0xFFFFFFFF);
        codeField.setBackgroundColor(0xFF1A1A1A);
        codeField.setPadding(24, 16, 24, 16);
        root.addView(codeField);

        Button activateBtn = new Button(this);
        activateBtn.setText("Activate");
        activateBtn.setTextColor(0xFFFFFFFF);
        activateBtn.setBackgroundColor(0xFF1565C0);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 24, 0, 0);
        activateBtn.setLayoutParams(btnParams);
        root.addView(activateBtn);

        activateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String entered = codeField.getText().toString().trim();
                if (DEMO_CODE.equalsIgnoreCase(entered)) {
                    prefs.edit().putBoolean(KEY_ACTIVATED, true).apply();
                    Toast.makeText(ActivationActivity.this,
                            "Activated! Enjoy Pramod Octapad Loops.", Toast.LENGTH_SHORT).show();
                    launchLoops();
                } else {
                    Toast.makeText(ActivationActivity.this,
                            "Invalid activation code. Please try again.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        setContentView(root);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void launchLoops() {
        Intent intent = new Intent(this, LoopsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
