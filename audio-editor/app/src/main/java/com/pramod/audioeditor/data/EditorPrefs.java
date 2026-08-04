package com.pramod.audioeditor.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * SharedPreferences wrapper for editor settings.
 */
public class EditorPrefs {
    private static final String PREFS = "audio_editor_prefs";
    private static final String KEY_LAST_FOLDER = "last_folder_uri";
    private static final String KEY_LAST_FILE = "last_file_name";
    private static final String KEY_CLIP_GUARD = "clip_guard_default";
    private static final String KEY_EQ_BYPASS = "eq_bypass_default";

    private final SharedPreferences prefs;

    public EditorPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setLastFolder(Uri uri) {
        prefs.edit().putString(KEY_LAST_FOLDER, uri != null ? uri.toString() : null).apply();
    }
    public Uri getLastFolder() {
        String s = prefs.getString(KEY_LAST_FOLDER, null);
        return s != null ? Uri.parse(s) : null;
    }

    public void setLastFileName(String name) {
        prefs.edit().putString(KEY_LAST_FILE, name).apply();
    }
    public String getLastFileName() {
        return prefs.getString(KEY_LAST_FILE, null);
    }

    public void setClipGuard(boolean on) {
        prefs.edit().putBoolean(KEY_CLIP_GUARD, on).apply();
    }
    public boolean getClipGuard() {
        return prefs.getBoolean(KEY_CLIP_GUARD, true);
    }

    public void setEqBypass(boolean on) {
        prefs.edit().putBoolean(KEY_EQ_BYPASS, on).apply();
    }
    public boolean getEqBypass() {
        return prefs.getBoolean(KEY_EQ_BYPASS, false);
    }
}
