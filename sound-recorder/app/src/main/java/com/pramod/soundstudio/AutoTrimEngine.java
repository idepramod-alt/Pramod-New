package com.pramod.soundstudio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ultra Precision Drum Sample Auto Trim Engine
 *
 * Features:
 *  - Energy-based silence detection (adaptive RMS threshold)
 *  - Transient/onset detection via spectral energy difference
 *  - Attack protection (never cuts into the attack transient)
 *  - Decay tail protection (configurable release time)
 *  - Sample-accurate trim points
 *  - Adjustable sensitivity (0.0 – 1.0)
 *  - Pre-roll padding (ms)
 *  - Post-roll (decay tail) padding (ms)
 *  - Undo/redo history
 *  - Batch auto-trim support
 */
public class AutoTrimEngine {

    // ── Config ────────────────────────────────────────────────────────────────

    /** Sensitivity 0.0 = very aggressive (trim tight), 1.0 = very loose. */
    private float sensitivity    = 0.5f;

    /** Pre-roll added before the first detected transient (ms). */
    private int   preRollMs      = 5;

    /** Post-roll / decay tail added after last sound above threshold (ms). */
    private int   postRollMs     = 50;

    /** Minimum silence between two hits to consider them separate (ms). */
    private int   minSilenceMs   = 30;

    /** RMS analysis window size in samples. */
    private static final int RMS_WINDOW = 512;

    // ── Undo / redo history ───────────────────────────────────────────────────

    private final List<TrimState> undoStack = new ArrayList<>();
    private final List<TrimState> redoStack = new ArrayList<>();

    private static class TrimState {
        final int startSample, endSample;
        TrimState(int s, int e) { startSample = s; endSample = e; }
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSensitivity(float s)  { sensitivity  = Math.max(0f, Math.min(1f, s)); }
    public void setPreRollMs(int ms)     { preRollMs    = Math.max(0, ms); }
    public void setPostRollMs(int ms)    { postRollMs   = Math.max(0, ms); }
    public void setMinSilenceMs(int ms)  { minSilenceMs = Math.max(0, ms); }

    // ── Core analysis ─────────────────────────────────────────────────────────

    /**
     * Compute RMS energy for each window of samples.
     * Returns an array of length ceil(samples.length / RMS_WINDOW).
     */
    public static float[] computeRmsWindows(short[] samples) {
        int numWindows = (int) Math.ceil((double) samples.length / RMS_WINDOW);
        float[] rms    = new float[numWindows];
        for (int w = 0; w < numWindows; w++) {
            int from = w * RMS_WINDOW;
            int to   = Math.min(from + RMS_WINDOW, samples.length);
            double sum = 0;
            for (int i = from; i < to; i++) {
                double s = samples[i] / 32768.0;
                sum += s * s;
            }
            rms[w] = (float) Math.sqrt(sum / (to - from));
        }
        return rms;
    }

    /**
     * Compute adaptive silence threshold based on the quietest 20% of RMS windows.
     * Sensitivity shifts this threshold up/down.
     * sensitivity=0 → very aggressive; sensitivity=1 → very loose.
     */
    public float computeThreshold(float[] rms) {
        float[] sorted = rms.clone();
        java.util.Arrays.sort(sorted);
        // Take median of lowest 20% as "noise floor"
        int n         = Math.max(1, sorted.length / 5);
        float noiseFloor = 0;
        for (int i = 0; i < n; i++) noiseFloor += sorted[i];
        noiseFloor /= n;

        // Peak RMS
        float peak = 0;
        for (float v : sorted) peak = Math.max(peak, v);

        // Threshold = noise_floor + sensitivity * (peak - noise_floor) * 0.15
        float range = peak - noiseFloor;
        return noiseFloor + (1f - sensitivity) * range * 0.15f + 0.002f;
    }

    /**
     * Find the sample-accurate trim start point.
     * Uses RMS windows to detect noise floor then scans forward
     * sample-by-sample once we're in the attack region for maximum precision.
     *
     * @param samples    PCM 16-bit samples
     * @param sampleRate audio sample rate (Hz)
     * @return trim start sample index (0-based)
     */
    public int findTrimStart(short[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) return 0;
        float[] rms       = computeRmsWindows(samples);
        float   threshold = computeThreshold(rms);

        // Find first RMS window above threshold
        int windowIdx = -1;
        for (int w = 0; w < rms.length; w++) {
            if (rms[w] > threshold) { windowIdx = w; break; }
        }
        if (windowIdx < 0) return 0;

        // Fine-scan: start one window before, sample-by-sample
        int coarseStart = Math.max(0, (windowIdx - 1) * RMS_WINDOW);
        for (int i = coarseStart; i < samples.length; i++) {
            if (Math.abs(samples[i]) > threshold * 32768f) {
                // Apply pre-roll
                int preRollSamples = preRollMs * sampleRate / 1000;
                return Math.max(0, i - preRollSamples);
            }
        }
        return 0;
    }

    /**
     * Find the sample-accurate trim end point.
     * Scans backward from the end to find the last sample above threshold,
     * then applies post-roll (decay tail) padding.
     *
     * @param samples    PCM 16-bit samples
     * @param sampleRate audio sample rate (Hz)
     * @return trim end sample index (exclusive)
     */
    public int findTrimEnd(short[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) return 0;
        float[] rms       = computeRmsWindows(samples);
        float   threshold = computeThreshold(rms);

        // Find last RMS window above threshold
        int windowIdx = -1;
        for (int w = rms.length - 1; w >= 0; w--) {
            if (rms[w] > threshold) { windowIdx = w; break; }
        }
        if (windowIdx < 0) return samples.length;

        // Fine-scan backward from end of that window
        int coarseEnd = Math.min(samples.length - 1, (windowIdx + 2) * RMS_WINDOW);
        for (int i = coarseEnd; i >= 0; i--) {
            if (Math.abs(samples[i]) > threshold * 32768f) {
                // Apply post-roll (decay protection)
                int postRollSamples = postRollMs * sampleRate / 1000;
                return Math.min(samples.length, i + postRollSamples + 1);
            }
        }
        return samples.length;
    }

    /**
     * Perform full auto-trim: find start + end, write trimmed WAV.
     *
     * @param input       source WAV file
     * @param outputDir   directory for trimmed output
     * @return TrimResult with output file and metadata
     */
    public TrimResult autoTrim(File input, File outputDir) throws IOException {
        int[]   header     = AudioTrimmer.readWavHeader(input);
        int     sampleRate = header[0];
        int     channels   = header[1];
        short[] samples    = AudioTrimmer.readWavPcm(input);

        int startSample = findTrimStart(samples, sampleRate * channels);
        int endSample   = findTrimEnd(samples, sampleRate * channels);

        // Push to undo stack
        undoStack.add(new TrimState(startSample, endSample));
        redoStack.clear();

        // Write trimmed file
        short[] trimmed = new short[Math.max(0, endSample - startSample)];
        if (trimmed.length > 0) System.arraycopy(samples, startSample, trimmed, 0, trimmed.length);

        String outName = input.getName().replace(".wav", "_autotrimmed.wav");
        File   outFile = new File(outputDir, outName);
        AudioTrimmer.writePcmToWav(trimmed, sampleRate, channels, outFile);

        double removedStartSec = (double) startSample / (sampleRate * channels);
        double removedEndSec   = (double)(samples.length - endSample) / (sampleRate * channels);
        double newDurSec       = (double) trimmed.length / (sampleRate * channels);

        return new TrimResult(outFile, removedStartSec, removedEndSec, newDurSec,
                startSample, endSample, samples.length);
    }

    /**
     * Batch auto-trim multiple WAV files.
     *
     * @param inputs    list of WAV files
     * @param outputDir directory for trimmed outputs
     * @param listener  progress callback (nullable)
     * @return list of TrimResults
     */
    public List<TrimResult> batchAutoTrim(List<File> inputs, File outputDir,
                                           BatchListener listener) {
        List<TrimResult> results = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            try {
                TrimResult r = autoTrim(inputs.get(i), outputDir);
                results.add(r);
                if (listener != null) listener.onProgress(i + 1, inputs.size(), r.outputFile.getName());
            } catch (IOException e) {
                if (listener != null) listener.onError(inputs.get(i).getName(), e.getMessage());
            }
        }
        if (listener != null) listener.onComplete(results.size());
        return results;
    }

    // ── Transient detection ───────────────────────────────────────────────────

    /**
     * Detect all onset/transient positions in a sample array.
     * Used by DrumSplitter to find individual hits.
     *
     * Algorithm: onset detection function = RMS energy difference between
     * consecutive windows. Peaks above adaptive threshold = onsets.
     *
     * @param samples    PCM 16-bit samples
     * @param sampleRate effective rate (hz * channels)
     * @return list of onset sample positions
     */
    public List<Integer> detectOnsets(short[] samples, int sampleRate) {
        List<Integer> onsets = new ArrayList<>();
        if (samples == null || samples.length == 0) return onsets;

        float[] rms        = computeRmsWindows(samples);
        float   threshold  = computeThreshold(rms);
        int     minGapWin  = minSilenceMs * sampleRate / (1000 * RMS_WINDOW);

        // Onset detection function: positive RMS flux
        float[] odf = new float[rms.length];
        for (int w = 1; w < rms.length; w++) {
            float diff = rms[w] - rms[w - 1];
            odf[w] = Math.max(0, diff);  // half-wave rectify
        }

        // Adaptive threshold on ODF
        float odfMean = 0;
        for (float v : odf) odfMean += v;
        odfMean /= odf.length;
        float odfThreshold = odfMean * (1.5f + (1f - sensitivity) * 3f);

        // Pick peaks separated by minGap
        int lastOnset = -minGapWin;
        for (int w = 1; w < odf.length; w++) {
            if (odf[w] > odfThreshold && rms[w] > threshold && (w - lastOnset) > minGapWin) {
                // Fine-scan to exact sample
                int coarseStart = w * RMS_WINDOW;
                int fineSample  = coarseStart;
                for (int i = coarseStart; i < Math.min(samples.length, coarseStart + RMS_WINDOW * 2); i++) {
                    if (Math.abs(samples[i]) > threshold * 32768f) { fineSample = i; break; }
                }
                // Apply pre-roll
                int preRoll = preRollMs * sampleRate / 1000;
                onsets.add(Math.max(0, fineSample - preRoll));
                lastOnset = w;
            }
        }

        // Always include sample 0 as a potential onset if there's content
        if (onsets.isEmpty() && rms.length > 0 && rms[0] > threshold) {
            onsets.add(0, 0);
        }

        return onsets;
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    /** Undo the last auto-trim operation. Returns the previous state, or null. */
    public TrimState undo() {
        if (undoStack.isEmpty()) return null;
        TrimState last = undoStack.remove(undoStack.size() - 1);
        redoStack.add(last);
        return undoStack.isEmpty() ? null : undoStack.get(undoStack.size() - 1);
    }

    /** Redo the last undone operation. Returns the restored state, or null. */
    public TrimState redo() {
        if (redoStack.isEmpty()) return null;
        TrimState next = redoStack.remove(redoStack.size() - 1);
        undoStack.add(next);
        return next;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    // ── Result types ──────────────────────────────────────────────────────────

    public static class TrimResult {
        public final File   outputFile;
        public final double removedStartSec;
        public final double removedEndSec;
        public final double newDurationSec;
        public final int    startSample;
        public final int    endSample;
        public final int    totalSamples;

        TrimResult(File f, double rs, double re, double dur, int ss, int se, int ts) {
            outputFile = f; removedStartSec = rs; removedEndSec = re;
            newDurationSec = dur; startSample = ss; endSample = se; totalSamples = ts;
        }
    }

    public interface BatchListener {
        void onProgress(int done, int total, String fileName);
        void onError(String fileName, String error);
        void onComplete(int successCount);
    }
}
