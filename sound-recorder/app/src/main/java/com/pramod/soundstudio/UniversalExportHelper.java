package com.pramod.soundstudio;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * UniversalExportHelper — Centralised export/share utility for all modules.
 *
 * • Share single audio file via system share sheet
 * • Share multiple files as ZIP via share sheet
 * • Save file to public Music folder (MediaStore API on Android 10+)
 * • Create ZIP archive
 * • Copy file utility
 * • Human-readable file size
 *
 * All heavy IO should be called from a background thread.
 * Start share-sheet Intents on the main thread.
 */
public class UniversalExportHelper {

    private static final String AUTHORITY = "com.pramod.soundstudio.fileprovider";

    // ── Share single file ─────────────────────────────────────────────────────
    public static void shareFile(Context ctx, File file, String subject) {
        if (file == null || !file.exists()) {
            Toast.makeText(ctx, "File not found", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(ctx, AUTHORITY, file);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeForFile(file));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (subject != null) i.putExtra(Intent.EXTRA_SUBJECT, subject);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, "Share " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(ctx, "Share error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Share multiple files as ZIP ───────────────────────────────────────────
    public interface ZipReadyCallback {
        void onReady(Intent shareIntent);
        void onError(String message);
    }

    public static void shareFilesAsZip(Context ctx, List<File> files,
                                        String zipName, ZipReadyCallback callback) {
        File cacheDir = new File(ctx.getCacheDir(), "export_zips");
        cacheDir.mkdirs();
        File zipFile = new File(cacheDir, zipName);
        try {
            createZip(files, zipFile);
            Uri uri = FileProvider.getUriForFile(ctx, AUTHORITY, zipFile);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, zipName);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (callback != null) callback.onReady(Intent.createChooser(i, "Share " + zipName));
        } catch (Exception e) {
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    // ── Save to public Music folder ───────────────────────────────────────────
    public static String saveToDownloads(Context ctx, File file, String subfolder) {
        if (file == null || !file.exists()) return null;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? saveViaMediaStore(ctx, file, subfolder)
                : saveToExternalLegacy(file, subfolder);
    }

    private static String saveViaMediaStore(Context ctx, File file, String subfolder) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            cv.put(MediaStore.Audio.Media.MIME_TYPE, mimeForFile(file));
            cv.put(MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + File.separator + subfolder);
            Uri uri = ctx.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) return null;
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            cv.clear(); cv.put(MediaStore.Audio.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, cv, null, null);
            return "Music/" + subfolder + "/" + file.getName();
        } catch (Exception e) { return null; }
    }

    private static String saveToExternalLegacy(File src, String subfolder) {
        try {
            File music  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File outDir = new File(music, subfolder);
            outDir.mkdirs();
            File dest = new File(outDir, src.getName());
            copyFile(src, dest);
            return dest.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    public static int saveBatchToDownloads(Context ctx, List<File> files, String subfolder) {
        int count = 0;
        for (File f : files) if (saveToDownloads(ctx, f, subfolder) != null) count++;
        return count;
    }

    // ── ZIP creation ──────────────────────────────────────────────────────────
    public static void createZip(List<File> files, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            byte[] buf = new byte[8192];
            for (File f : files) {
                if (!f.exists()) continue;
                zos.putNextEntry(new ZipEntry(f.getName()));
                try (FileInputStream fis = new FileInputStream(f)) {
                    int n; while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            }
        }
    }

    // ── File copy ─────────────────────────────────────────────────────────────
    public static void copyFile(File src, File dst) throws IOException {
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    // ── MIME type ─────────────────────────────────────────────────────────────
    public static String mimeForFile(File f) {
        String name = f.getName().toLowerCase(Locale.US);
        if (name.endsWith(".wav"))                         return "audio/wav";
        if (name.endsWith(".mp3"))                         return "audio/mpeg";
        if (name.endsWith(".m4a") || name.endsWith(".aac"))return "audio/mp4";
        if (name.endsWith(".flac"))                        return "audio/flac";
        if (name.endsWith(".ogg"))                         return "audio/ogg";
        if (name.endsWith(".zip"))                         return "application/zip";
        return "audio/*";
    }

    // ── Human-readable size ───────────────────────────────────────────────────
    public static String formatSize(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1048576)     return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        if (bytes < 1073741824)  return String.format(Locale.US, "%.2f MB", bytes / 1048576f);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824f);
    }

    // ── Clear directory ───────────────────────────────────────────────────────
    public static void clearDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }
}
