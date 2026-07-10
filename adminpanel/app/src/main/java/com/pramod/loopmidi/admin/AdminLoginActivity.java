package com.pramod.loopmidi.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AdminLoginActivity extends AppCompatActivity {

    // Only this Gmail is allowed into the admin panel, even if someone else
    // somehow ends up with valid Firebase credentials.
    private static final String ADMIN_EMAIL = "ps152390@gmail.com";

    private FirebaseAuth auth;
    private EditText editEmail;
    private EditText editPassword;
    private TextView txtStatus;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = AdminFirebaseApp.auth(this);
        buildUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            goToDashboard();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff111111);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = dp(24);
        root.setPadding(pad, dp(64), pad, pad);
        root.setLayoutParams(new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Pramod Octapad \u2014 Admin Panel");
        title.setTextColor(0xff00afff);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sirf admin ke Gmail se login hoga");
        sub.setTextColor(0xffaaaaaa);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(24));
        root.addView(sub);

        editEmail = new EditText(this);
        editEmail.setHint("Admin Gmail");
        editEmail.setText(ADMIN_EMAIL);
        editEmail.setTextColor(0xffffffff);
        editEmail.setHintTextColor(0xff888888);
        root.addView(editEmail, matchWidthParams(0));

        editPassword = new EditText(this);
        editPassword.setHint("Password");
        editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editPassword.setTextColor(0xffffffff);
        editPassword.setHintTextColor(0xff888888);
        root.addView(editPassword, matchWidthParams(dp(8)));

        btnLogin = new Button(this);
        btnLogin.setText("LOGIN");
        btnLogin.setOnClickListener(v -> attemptLogin());
        root.addView(btnLogin, matchWidthParams(dp(16)));

        txtStatus = new TextView(this);
        txtStatus.setTextColor(0xffff6b6b);
        txtStatus.setGravity(Gravity.CENTER);
        txtStatus.setPadding(0, dp(16), 0, 0);
        root.addView(txtStatus, matchWidthParams(0));

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWidthParams(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = topMargin;
        return p;
    }

    private void attemptLogin() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (!ADMIN_EMAIL.equalsIgnoreCase(email)) {
            txtStatus.setText("Sirf admin email allowed hai.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            txtStatus.setText("Password daalein.");
            return;
        }

        btnLogin.setEnabled(false);
        txtStatus.setTextColor(0xffaaaaaa);
        txtStatus.setText("Login ho raha hai\u2026");

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnLogin.setEnabled(true);
                    if (task.isSuccessful()) {
                        goToDashboard();
                    } else {
                        String reason = task.getException() != null
                                ? task.getException().getMessage() : "Login fail ho gaya";
                        txtStatus.setTextColor(0xffff6b6b);
                        txtStatus.setText(reason
                                + "\n\nPehli baar use kar rahe ho? Firebase Console \u2192 "
                                + "Authentication \u2192 Sign-in method me 'Email/Password' "
                                + "enable karein, phir Users tab me is Gmail ("
                                + ADMIN_EMAIL + ") ke liye ek password wala user add karein.");
                    }
                });
    }

    private void goToDashboard() {
        startActivity(new Intent(this, AdminDashboardActivity.class));
        finish();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
