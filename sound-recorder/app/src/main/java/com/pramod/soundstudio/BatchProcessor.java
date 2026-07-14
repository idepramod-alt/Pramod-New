package com.pramod.soundstudio;

import java.io.*;
import java.util.*;

/**
 * Batch Processing Engine
 *
 * Supports batch operations on lists of audio files:
 *  - Auto-Trim all files
 *  - Normalize all files
 *  - Convert all files to a target format
 *  - Rename files (prefix + sequential number / date pattern)
 *  - Apply effects to all files
 *  - Export all with a custom ExportConfig
 *
 * All operations run synchronously on a background thread.
 * Progress is reported via BatchProgressListener.
 */
public class BatchProcessor {

    // ── Engines ───────────────────────────────────────────────────────────────
    private final AutoTrimEngine      trimEngine   = new AutoTrimEngine();
    private final AudioEnhancer       enhancer     = new AudioEnhancer();
    private final AudioConverter      converter    = new AudioConverter();
    private final AudioEffectsEngine  fxEngine     = new AudioEffectsEngine();

    // ── Listener ──────────────────────────────────────────────────────────────
    public interface BatchProgressListener {
        /** Called before processing each file. */
        void onFileStart(int index, int total, String fileName);
        /** Called after each file is successfully processed. */
        void onFileComplete(int index, int total, File outputFile);
        /** Called if a file fails; processing continues with next. */
        void onFileError(int index, int total, String fileName, String error);
        /** Called when all files are done. */
        void onBatchComplete(int success, int failed, long totalDurationMs);
    }

    // ── Batch Auto-Trim ───────────────────────────────────────────────────────

    public void batchAutoTrim(List<File> files, File outputDir,
                               float sensitivity, int preRollMs, int postRollMs,
                               BatchProgressListener listener) {
        trimEngine.setSensitivity(sensitivity);
        trimEngine.setPreRollMs(preRollMs);
        trimEngine.setPostRollMs(postRollMs);

        long start   = System.currentTimeMillis();
        int  success = 0, failed = 0;

        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (listener != null) listener.onFileStart(i, files.size(), f.getName());
            try {
                AutoTrimEngine.TrimResult r = trimEngine.autoTrim(f, outputDir);
                success++;
                if (listener != null) listener.onFileComplete(i, files.size(), r.outputFile);
            } catch (Exception e) {
                failed++;
                if (listener != null) listener.onFileError(i, files.size(), f.getName(), e.getMessage());
            }
        }

        long ms = System.currentTimeMillis() - start;
        if (listener != null) listener.onBatchComplete(success, failed, ms);
    }

    // ── Batch Normalize ───────────────────────────────────────────────────────

    public void batchNormalize(List<File> files, File outputDir,
                                double targetDB, BatchProgressListener listener) {
        long start = System.currentTimeMillis();
        int  success = 0, failed = 0;
        AudioEnhancer.EnhancementConfig cfg = new AudioEnhancer.EnhancementConfig();
        cfg.normalize = true;
        cfg.normalizeTargetDB = targetDB;

        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (listener != null) listener.onFileStart(i, files.size(), f.getName());
            try {
                File out = enhancer.enhance(f, outputDir, cfg);
                success++;
                if (listener != null) listener.onFileComplete(i, files.size(), out);
            } catch (Exception e) {
                failed++;
                if (listener != null) listener.onFileError(i, files.size(), f.getName(), e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - start;
        if (listener != null) listener.onBatchComplete(success, failed, ms);
    }

    // ── Batch Convert ─────────────────────────────────────────────────────────

    public void batchConvert(List<File> files, File outputDir,
                              AudioConverter.OutputFormat format,
                              AudioConverter.ConvertConfig cfg,
                              BatchProgressListener listener) {
        long start = System.currentTimeMillis();
        int  success = 0, failed = 0;

        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (listener != null) listener.onFileStart(i, files.size(), f.getName());
            try {
                File out = converter.convert(f, outputDir, format, cfg, null);
                success++;
                if (listener != null) listener.onFileComplete(i, files.size(), out);
            } catch (Exception e) {
                failed++;
                if (listener != null) listener.onFileError(i, files.size(), f.getName(), e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - start;
        if (listener != null) listener.onBatchComplete(success, failed, ms);
    }

    // ── Batch Rename ──────────────────────────────────────────────────────────

    public void batchRename(List<File> files, String prefix, boolean addSequence,
                             BatchProgressListener listener) {
        long start = System.currentTimeMillis();
        int  success = 0, failed = 0;

        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (listener != null) listener.onFileStart(i, files.size(), f.getName());
            try {
                String ext = getExt(f.getName());
                String newName = addSequence
                        ? String.format("%s_%03d%s", prefix, i + 1, ext)
                        : prefix + ext;
                File newFile = new File(f.getParent(), newName);
                if (f.renameTo(newFile)) {
                    success++;
                    if (listener != null) listener.onFileComplete(i, files.size(), newFile);
                } else {
                    failed++;
                    if (listener != null) listener.onFileError(i, files.size(), f.getName(), "Rename failed");
                }
            } catch (Exception e) {
                failed++;
                if (listener != null) listener.onFileError(i, files.size(), f.getName(), e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - start;
        if (listener != null) listener.onBatchComplete(success, failed, ms);
    }

    // ── Batch Apply Effects ───────────────────────────────────────────────────

    public void batchApplyEffects(List<File> files, File outputDir,
                                   AudioEffectsEngine.EffectsConfig cfg,
                                   BatchProgressListener listener) {
        long start = System.currentTimeMillis();
        int  success = 0, failed = 0;

        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (listener != null) listener.onFileStart(i, files.size(), f.getName());
            try {
                File out = fxEngine.applyEffects(f, outputDir, cfg);
                success++;
                if (listener != null) listener.onFileComplete(i, files.size(), out);
            } catch (Exception e) {
                failed++;
                if (listener != null) listener.onFileError(i, files.size(), f.getName(), e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - start;
        if (listener != null) listener.onBatchComplete(success, failed, ms);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
