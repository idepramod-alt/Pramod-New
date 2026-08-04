package com.pramod.audioeditor.engine;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.pramod.audioeditor.data.EditorPrefs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * SAF (Storage Access Framework) save helper with folder memory.
 */
public class SaveHelper {

    public static final int REQUEST_SAVE = 9001;

    public static Intent createSaveIntent(String mimeType, String suggestedName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        return intent;
    }

    public static void writeToUri(Activity activity, Uri uri, File cacheFile) throws IOException {
        InputStream in = new java.io.FileInputStream(cacheFile);
        OutputStream out = activity.getContentResolver().openOutputStream(uri);
        if (out == null) throw new IOException("Cannot open output stream");
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    public static boolean fileExists(Activity activity, Uri folderUri, String fileName) {
        try {
            Uri docUri = Uri.parse(folderUri.toString() + "/" + fileName);
            android.content.ContentResolver cr = activity.getContentResolver();
            android.database.Cursor cursor = cr.query(docUri, null, null, null, null);
            if (cursor != null) {
                boolean exists = cursor.getCount() > 0;
                cursor.close();
                return exists;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void rememberFolder(EditorPrefs prefs, Uri folderUri) {
        prefs.setLastFolder(folderUri);
    }

    public static Uri getLastFolder(EditorPrefs prefs) {
        return prefs.getLastFolder();
    }
}
