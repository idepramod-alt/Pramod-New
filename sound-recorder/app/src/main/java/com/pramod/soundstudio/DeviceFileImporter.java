package com.pramod.soundstudio;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DeviceFileImporter — universal "browse device storage" file picker.
 *
 * Every screen that used to only list WAV files from the app's internal
 * recordings/ folder can now also let the user pick ANY audio file from
 * ANYWHERE on the device (Downloads, WhatsApp, Music, SD card, cloud-backed
 * folders, etc.) via Android's Storage Access Framework, and have it copied
 * into the app so the existing File-based processing code keeps working
 * unchanged.
 *
 * Usage from an Activity:
 *   DeviceFileImporter.launchPicker(this, REQUEST_CODE, allowMultiple);
 *
 *   @Override
 *   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
 *       super.onActivityResult(requestCode, resultCode, data);
 *       if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
 *           List<File> imported = DeviceFileImporter.handleResult(this, data, importDestDir());
 *           // use imported files...
 *       }
 *   }
 */
public final class DeviceFileImporter {

    private DeviceFileImporter() {}

    /** Broad set of audio mime types so the system picker shows common audio formats
     *  even on devices where a generic "audio/*" filter hides some file types. */
    private static final String[] AUDIO_MIME_TYPES = new String[]{
            "audio/*",
            "audio/x-wav",
            "audio/wav",
            "audio/mpeg",
            "audio/mp4",
            "audio/aac",
            "audio/ogg",
            "audio/flac",
            "audio/x-flac",
            "application/octet-stream" // some file managers expose audio under this generic type
    };

    /**
     * Opens the system-wide document picker so the user can browse ANY folder /
     * storage provider on the device (internal storage, SD card, Downloads,
     * WhatsApp, Google Drive, etc.) and pick one or more audio files.
     */
    public static void launchPicker(Activity activity, int requestCode, boolean allowMultiple) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, AUDIO_MIME_TYPES);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            // Some devices / OEM skins don't ship a document picker for ACTION_OPEN_DOCUMENT.
            // Fall back to the simpler, near-universally-supported GET_CONTENT picker.
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                fallback.setType("audio/*");
                fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
                activity.startActivityForResult(fallback, requestCode);
            } catch (Exception e2) {
                Toast.makeText(activity, "No file picker app found on this device", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Call from onActivityResult once you've matched the request code. Copies every
     * picked document into {@code destDir} (created if needed) and returns the
     * resulting Files, ready to use exactly like any other recording on disk.
     * Import errors for individual files are reported via Toast and skipped
     * rather than aborting the whole batch.
     */
    public static List<File> handleResult(Context ctx, Intent data, File destDir) {
        List<File> imported = new ArrayList<>();
        if (data == null) return imported;

        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                uris.add(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        if (!destDir.exists()) destDir.mkdirs();

        for (Uri uri : uris) {
            try {
                imported.add(importOne(ctx, uri, destDir));
            } catch (Exception e) {
                Toast.makeText(ctx, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        return imported;
    }

    /** Copies a single content:// (or file://) Uri into destDir and returns the new File. */
    private static File importOne(Context ctx, Uri uri, File destDir) throws IOException {
        String displayName = queryDisplayName(ctx, uri);
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "imported_" + System.currentTimeMillis() + guessExtension(ctx, uri);
        }
        displayName = sanitizeFileName(displayName);

        File outFile = uniqueFile(destDir, displayName);

        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open input stream for " + uri);
            try (OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.flush();
            }
        }
        return outFile;
    }

    private static String queryDisplayName(Context ctx, Uri uri) {
        if (!"content".equals(uri.getScheme())) {
            String path = uri.getLastPathSegment();
            return path;
        }
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String guessExtension(Context ctx, Uri uri) {
        String type = ctx.getContentResolver().getType(uri);
        if (type == null) return ".wav";
        if (type.contains("wav"))  return ".wav";
        if (type.contains("mpeg") || type.contains("mp3")) return ".mp3";
        if (type.contains("mp4") || type.contains("m4a")) return ".m4a";
        if (type.contains("aac"))  return ".aac";
        if (type.contains("ogg"))  return ".ogg";
        if (type.contains("flac")) return ".flac";
        return ".wav";
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? ("imported_" + System.currentTimeMillis() + ".wav") : cleaned;
    }

    /** Avoids clobbering an existing file with the same name by appending (1), (2), ... */
    private static File uniqueFile(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) return candidate;

        String base = name;
        String ext  = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }

        int i = 1;
        File next;
        do {
            next = new File(dir, base + "(" + i + ")" + ext);
            i++;
        } while (next.exists());
        return next;
    }
}
