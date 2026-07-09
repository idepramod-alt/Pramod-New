package com.pramod.loopmidi;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Static helper that syncs loop/pad settings between local SharedPreferences
 * and Firebase Realtime Database under users/{uid}/loopSettings.
 */
public class CloudSync {

    private static final String PREFS_NAME = "LoopPrefs";
    private static final String DB_PATH    = "users";

    // -------------------------------------------------------------------------
    // Write user profile (called once after successful Google Sign-In)
    // -------------------------------------------------------------------------
    public static void writeProfile(String uid, String email, String displayName) {
        if (uid == null) return;
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference(DB_PATH).child(uid).child("profile");
        Map<String, Object> profile = new HashMap<>();
        profile.put("email",       email       != null ? email       : "");
        profile.put("displayName", displayName != null ? displayName : "");
        ref.setValue(profile);
    }

    // -------------------------------------------------------------------------
    // Pull cloud settings into local SharedPreferences, then run onDone
    // -------------------------------------------------------------------------
    public static void pullSettingsThenRun(final Context context,
                                           final String uid,
                                           final Runnable onDone) {
        if (uid == null) {
            if (onDone != null) onDone.run();
            return;
        }
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference(DB_PATH).child(uid).child("loopSettings");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    SharedPreferences prefs =
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    for (DataSnapshot entry : snapshot.getChildren()) {
                        Object val = entry.getValue();
                        if (val instanceof Boolean) {
                            editor.putBoolean(entry.getKey(), (Boolean) val);
                        } else if (val instanceof Long) {
                            editor.putInt(entry.getKey(), ((Long) val).intValue());
                        } else if (val instanceof Double) {
                            editor.putFloat(entry.getKey(), ((Double) val).floatValue());
                        } else if (val instanceof String) {
                            editor.putString(entry.getKey(), (String) val);
                        }
                    }
                    editor.apply();
                }
                if (onDone != null) onDone.run();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Network/permission error — proceed anyway with local prefs
                if (onDone != null) onDone.run();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Push all local SharedPreferences to Firebase for a given uid
    // -------------------------------------------------------------------------
    public static void pushSettings(final Context context, final String uid) {
        if (uid == null) return;
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Boolean || v instanceof Integer
                    || v instanceof Float || v instanceof String) {
                data.put(entry.getKey(), v);
            }
        }
        if (data.isEmpty()) return;
        FirebaseDatabase.getInstance()
                .getReference(DB_PATH).child(uid).child("loopSettings")
                .setValue(data);
    }

    // -------------------------------------------------------------------------
    // Convenience: push for the currently-signed-in Firebase user
    // -------------------------------------------------------------------------
    public static void pushCurrentUserSettings(final Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            pushSettings(context, user.getUid());
        }
    }
}
