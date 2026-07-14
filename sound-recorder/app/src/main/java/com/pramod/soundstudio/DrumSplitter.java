package com.pramod.soundstudio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Drum Sample Detector & Splitter
 *
 * Splits a multi-hit audio file into individual drum samples using
 * precision transient/onset detection. Each detected hit is saved
 * as a separate WAV file.
 *
 * Features:
 *  - Onset detection via RMS energy flux (same engine as AutoTrimEngine)
 *  - Auto-trim each split segment (silence removed from head/tail)
 *  - Configurable minimum gap between hits
 *  - Configurable sensitivity (tight vs. loose)
 *  - Batch export of all splits
 *  - Custom output naming (prefix + sequential number)
 */
public class DrumSplitter {

    // ── Engine reference ──────────────────────────────────────────────────────
    private final AutoTrimEngine engine = new AutoTrimEngine();

    // ── Config ────────────────────────────────────────────────────────────────
    private float sensitivity      = 0.6f;
    private int   minHitGapMs      = 40;    // ignore hits closer than this
    private int   postRollMs       = 80;    // decay tail to keep after last sample
    private boolean autoTrimEach   = true;  // apply auto-trim to each split segment
    private String outputPrefix    = "hit"; // output file naming: hit_001.wav, hit_002.wav

    public void setSensitivity(float s)       { sensitivity    = s; engine.setSensitivity(s); }
    public void setMinHitGapMs(int ms)        { minHitGapMs    = ms; engine.setMinSilenceMs(ms); }
    public void setPostRollMs(int ms)         { postRollMs     = ms; engine.setPostRollMs(ms); }
    public void setAutoTrimEach(boolean trim) { autoTrimEach   = trim; }
    public void setOutputPrefix(String pfx)   { outputPrefix   = pfx; }

    // ── Main split method ─────────────────────────────────────────────────────

    /**
     * Detect all hits in the input WAV file and export each as a separate WAV.
     *
     * @param input      source multi-hit WAV file
     * @param outputDir  directory to write split files
     * @param listener   progress callbacks (nullable)
     * @return list of SplitResult for each detected hit
     */
    public List<SplitResult> splitFile(File input, File outputDir,
                                        SplitListener listener) throws IOException {
        // Read header + PCM
        int[]   header     = AudioTrimmer.readWavHeader(input);
        int     sampleRate = header[0];
        int     channels   = header[1];
        short[] samples    = AudioTrimmer.readWavPcm(input);

        int effectiveRate = sampleRate * channels;

        // Configure engine
        engine.setSensitivity(sensitivity);
        engine.setMinSilenceMs(minHitGapMs);
        engine.setPreRollMs(5);
        engine.setPostRollMs(postRollMs);

        // Detect onset positions
        List<Integer> onsets = engine.detectOnsets(samples, effectiveRate);

        if (onsets.isEmpty()) {
            // Treat entire file as one hit
            onsets.add(0);
        }

        // Build segments: [onset[i], onset[i+1]) or end-of-file
        List<int[]> segments = new ArrayList<>();
        for (int i = 0; i < onsets.size(); i++) {
            int segStart = onsets.get(i);
            int segEnd;
            if (i + 1 < onsets.size()) {
                // End at next onset minus a tiny pre-roll so we don't overlap
                int preRoll = 5 * effectiveRate / 1000;
                segEnd = Math.max(segStart + 1, onsets.get(i + 1) - preRoll);
            } else {
                // Last segment: extend to end + post-roll already embedded
                segEnd = samples.length;
            }
            if (segEnd > segStart) segments.add(new int[]{segStart, segEnd});
        }

        // Make sure output dir exists
        if (!outputDir.exists()) outputDir.mkdirs();

        List<SplitResult> results = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            int[] seg = segments.get(i);
            int len   = seg[1] - seg[0];
            short[] segSamples = new short[len];
            System.arraycopy(samples, seg[0], segSamples, 0, len);

            // Auto-trim head/tail of this segment
            if (autoTrimEach && segSamples.length > effectiveRate / 10) {
                segSamples = autoTrimSegment(segSamples, sampleRate, channels);
            }

            // Write file
            String fname = String.format("%s_%03d.wav", outputPrefix, i + 1);
            File   out   = new File(outputDir, fname);
            AudioTrimmer.writePcmToWav(segSamples, sampleRate, channels, out);

            double startSec = (double) seg[0] / effectiveRate;
            double durSec   = (double) segSamples.length / effectiveRate;
            SplitResult r   = new SplitResult(out, i + 1, startSec, durSec);
            results.add(r);

            if (listener != null) {
                listener.onHitExported(i + 1, segments.size(), out.getName(), durSec);
            }
        }

        if (listener != null) listener.onComplete(results.size());
        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Trim silence from head and tail of a short segment.
     */
    private short[] autoTrimSegment(short[] samples, int sampleRate, int channels) {
        float[] rms       = AutoTrimEngine.computeRmsWindows(samples);
        float   threshold = 0;
        for (float v : rms) threshold = Math.max(threshold, v);
        threshold *= 0.03f; // 3% of peak = silence

        int start = 0;
        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i]) > threshold * 32768f) {
                start = Math.max(0, i - 5 * sampleRate * channels / 1000); // 5ms pre-roll
                break;
            }
        }
        int end = samples.length;
        for (int i = samples.length - 1; i >= 0; i--) {
            if (Math.abs(samples[i]) > threshold * 32768f) {
                end = Math.min(samples.length, i + postRollMs * sampleRate * channels / 1000 + 1);
                break;
            }
        }
        if (end <= start) return samples;
        short[] trimmed = new short[end - start];
        System.arraycopy(samples, start, trimmed, 0, trimmed.length);
        return trimmed;
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public static class SplitResult {
        public final File   outputFile;
        public final int    hitNumber;
        public final double startSec;
        public final double durationSec;

        SplitResult(File f, int num, double start, double dur) {
            outputFile = f; hitNumber = num; startSec = start; durationSec = dur;
        }
    }

    public interface SplitListener {
        void onHitExported(int hitNum, int total, String fileName, double durationSec);
        void onComplete(int totalHits);
    }
}
