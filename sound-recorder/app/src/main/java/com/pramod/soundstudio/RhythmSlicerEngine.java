package com.pramod.soundstudio;

import java.io.*;
import java.util.*;

/**
 * Ultra-Precision AI Rhythm Slicer & Drum Sample Preparation Engine
 *
 * Pure Java DSP — no NDK, no ML dependencies. Android 10-15, minSdk 23.
 *
 * ── Algorithm Pipeline ─────────────────────────────────────────────────
 *  1. WAV PCM read → mono downmix
 *  2. Energy envelope  (RMS, 512-sample windows, 128-sample hop ≈ 2.9ms)
 *  3. Onset strength   (half-wave rectified log-energy first-difference)
 *  4. Adaptive threshold (3× local median in ±200ms window)
 *  5. Peak picking     (local max + minimum gap constraint)
 *  6. Drum-type classification via spectral centroid + energy
 *  7. BPM estimation   (onset-interval histogram)
 *  8. Roll / Fill detection (onset density analysis)
 *  9. Sample-accurate slice export with pre/post roll
 * ────────────────────────────────────────────────────────────────────────
 */
public class RhythmSlicerEngine {

    // ── Config ───────────────────────────────────────────────────────────────
    private float sensitivity   = 0.5f;
    private int   preRollMs     = 5;
    private int   postRollMs    = 80;
    private int   minGapMs      = 50;
    private boolean detectRolls = true;
    private boolean detectFills = true;

    private static final int WINDOW   = 512;
    private static final int HOP      = 128;
    private static final int FFT_SIZE = 512;

    // ── Public types ─────────────────────────────────────────────────────────
    public enum DrumType {
        KICK, SNARE, HI_HAT, TOM, CYMBAL, FILL, ROLL, UNKNOWN;
        public String emoji() {
            switch (this) {
                case KICK:   return "🥁";
                case SNARE:  return "💥";
                case HI_HAT: return "🎩";
                case TOM:    return "🔵";
                case CYMBAL: return "✨";
                case FILL:   return "🎶";
                case ROLL:   return "⚡";
                default:     return "🔊";
            }
        }
    }

    public static class SlicePoint {
        public int      onsetSample;
        public int      startSample;
        public int      endSample;
        public DrumType type;
        public float    confidence;
        public float    energyDb;
        public float    durationSec;
        public boolean  isRoll;
        public boolean  isFill;
        public int      padIndex;
    }

    public static class SliceResult {
        public List<SlicePoint> slices     = new ArrayList<>();
        public float            bpm        = 0f;
        public float            confidence = 0f;
        public int              sampleRate;
        public int              channels;
        public short[]          pcmMono;
        public float            totalDurationSec;
        public String           patternSummary = "";
        public int              kickCount, snareCount, hiHatCount, rollCount;
    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setSensitivity(float s)   { this.sensitivity  = Math.max(0f, Math.min(1f, s)); }
    public void setPreRollMs(int ms)      { this.preRollMs    = Math.max(0, ms); }
    public void setPostRollMs(int ms)     { this.postRollMs   = Math.max(0, ms); }
    public void setMinGapMs(int ms)       { this.minGapMs     = Math.max(10, ms); }
    public void setDetectRolls(boolean b) { this.detectRolls  = b; }
    public void setDetectFills(boolean b) { this.detectFills  = b; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MAIN: detectSlices
    // ═══════════════════════════════════════════════════════════════════════════
    public SliceResult detectSlices(File wavFile) throws IOException {
        int[]   hdr        = AudioTrimmer.readWavHeader(wavFile);
        int     sampleRate = hdr[0];
        int     channels   = hdr[1];
        short[] pcmRaw     = AudioTrimmer.readWavPcm(wavFile);

        float[] mono   = toMono(pcmRaw, channels);
        float[] energy = energyEnvelope(mono);
        float[] onset  = onsetStrength(energy);

        int     frameRate    = sampleRate / HOP;
        int     winFrames    = Math.max(1, (int)(0.20f * frameRate));
        float[] threshold    = adaptiveThreshold(onset, winFrames, thresholdMultiplier());
        int     minGapFrames = Math.max(1, (int)((minGapMs / 1000f) * frameRate));
        List<Integer> peaks  = pickPeaks(onset, threshold, minGapFrames);

        SliceResult result   = new SliceResult();
        result.sampleRate    = sampleRate;
        result.channels      = channels;
        result.pcmMono       = toShort(mono);
        result.totalDurationSec = (float) mono.length / sampleRate;

        int preS  = (int)(preRollMs  / 1000f * sampleRate);
        int postS = (int)(postRollMs / 1000f * sampleRate);

        for (int i = 0; i < peaks.size(); i++) {
            int onsetSample = peaks.get(i) * HOP;
            SlicePoint sp   = new SlicePoint();
            sp.onsetSample  = onsetSample;
            sp.startSample  = Math.max(0, onsetSample - preS);
            sp.endSample    = (i + 1 < peaks.size())
                ? Math.min(peaks.get(i + 1) * HOP - 1, onsetSample + postS)
                : Math.min(mono.length - 1, onsetSample + postS);
            sp.durationSec  = (float)(sp.endSample - sp.startSample) / sampleRate;
            sp.energyDb     = peakDb(mono, sp.startSample, sp.endSample);
            classify(mono, sp, sampleRate);
            result.slices.add(sp);
        }

        if (result.slices.size() >= 4) estimateBpm(result);
        if (detectRolls) detectRolls(result, sampleRate);
        if (detectFills) detectFills(result, sampleRate);

        for (SlicePoint sp : result.slices) {
            if (sp.type == DrumType.KICK)   result.kickCount++;
            if (sp.type == DrumType.SNARE)  result.snareCount++;
            if (sp.type == DrumType.HI_HAT) result.hiHatCount++;
            if (sp.isRoll)                  result.rollCount++;
        }

        assignPads(result);
        result.patternSummary = buildSummary(result);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ═══════════════════════════════════════════════════════════════════════════
    public File exportSlice(SliceResult result, SlicePoint sp,
                             File outputDir, String prefix) throws IOException {
        outputDir.mkdirs();
        short[] slicePcm = Arrays.copyOfRange(result.pcmMono, sp.startSample,
                Math.min(sp.endSample, result.pcmMono.length));
        String name = String.format(Locale.US, "%s_%s_%04d.wav",
                prefix, sp.type.name(), sp.onsetSample);
        File out = new File(outputDir, name);
        AudioTrimmer.writePcmToWav(slicePcm, result.sampleRate, 1, out);
        return out;
    }

    public List<File> exportAllSlices(SliceResult result,
                                       File outputDir, String prefix) throws IOException {
        List<File> exported = new ArrayList<>();
        for (SlicePoint sp : result.slices)
            exported.add(exportSlice(result, sp, outputDir, prefix));
        return exported;
    }

    /**
     * Build Octapad kit: up to 8 representative slices (1 per drum type),
     * normalized to -1 dBFS, exported as pad_1.wav…pad_8.wav at 44100/16bit/mono.
     */
    public List<File> buildOctapadKit(SliceResult result, File outputDir) throws IOException {
        outputDir.mkdirs();
        List<File> kit = new ArrayList<>();

        DrumType[] order = {DrumType.KICK, DrumType.SNARE, DrumType.HI_HAT, DrumType.TOM,
                            DrumType.CYMBAL, DrumType.FILL, DrumType.ROLL, DrumType.UNKNOWN};
        Map<DrumType, SlicePoint> best = new LinkedHashMap<>();
        for (DrumType t : order) {
            SlicePoint top = null;
            for (SlicePoint sp : result.slices)
                if (sp.type == t && (top == null || sp.confidence > top.confidence)) top = sp;
            if (top != null) best.put(t, top);
            if (best.size() == 8) break;
        }
        // Fill remaining slots with any slice not already included
        if (best.size() < 8) {
            for (SlicePoint sp : result.slices) {
                if (best.size() >= 8) break;
                if (!best.containsValue(sp)) { best.put(DrumType.UNKNOWN, sp); }
            }
        }

        int pad = 1;
        for (SlicePoint sp : best.values()) {
            short[] slicePcm = Arrays.copyOfRange(result.pcmMono, sp.startSample,
                    Math.min(sp.endSample, result.pcmMono.length));
            slicePcm = normalize(slicePcm, -1.0f);
            if (result.sampleRate != 44100) slicePcm = resample(slicePcm, result.sampleRate, 44100);
            File out = new File(outputDir, "pad_" + pad + ".wav");
            AudioTrimmer.writePcmToWav(slicePcm, 44100, 1, out);
            kit.add(out);
            sp.padIndex = pad++;
        }
        return kit;
    }

    /** Batch prepare: auto-trim + normalize + resample list of WAV files. */
    public List<File> batchPrepare(List<File> inputFiles, File outputDir,
                                    ProgressListener listener) throws IOException {
        outputDir.mkdirs();
        List<File> results = new ArrayList<>();
        AutoTrimEngine trimmer = new AutoTrimEngine();
        trimmer.setSensitivity(0.65f);
        trimmer.setPreRollMs(3);
        trimmer.setPostRollMs(50);

        for (int i = 0; i < inputFiles.size(); i++) {
            File f = inputFiles.get(i);
            if (listener != null) listener.onProgress(i + 1, inputFiles.size(), f.getName());
            try {
                AutoTrimEngine.TrimResult trimmed = trimmer.autoTrim(f, outputDir);
                int[]   hdr = AudioTrimmer.readWavHeader(trimmed.outputFile);
                short[] pcm = AudioTrimmer.readWavPcm(trimmed.outputFile);
                pcm = normalize(pcm, -1.0f);
                if (hdr[0] != 44100) pcm = resample(pcm, hdr[0], 44100);
                File out = new File(outputDir, "prep_" + f.getName());
                AudioTrimmer.writePcmToWav(pcm, 44100, 1, out);
                results.add(out);
            } catch (Exception e) {
                if (listener != null) listener.onError(f.getName(), e.getMessage());
            }
        }
        return results;
    }

    public interface ProgressListener {
        void onProgress(int done, int total, String currentFile);
        void onError(String file, String error);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DSP INTERNALS
    // ═══════════════════════════════════════════════════════════════════════════
    private float[] toMono(short[] pcm, int channels) {
        if (channels == 1) {
            float[] f = new float[pcm.length];
            for (int i = 0; i < pcm.length; i++) f[i] = pcm[i] / 32768f;
            return f;
        }
        int frames = pcm.length / channels;
        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) sum += pcm[i * channels + c];
            mono[i] = (float)(sum / channels / 32768.0);
        }
        return mono;
    }

    private short[] toShort(float[] f) {
        short[] s = new short[f.length];
        for (int i = 0; i < f.length; i++)
            s[i] = (short) Math.max(-32768, Math.min(32767, (int)(f[i] * 32767)));
        return s;
    }

    private float[] energyEnvelope(float[] mono) {
        int numFrames = Math.max(1, (mono.length - WINDOW) / HOP + 1);
        float[] env = new float[numFrames];
        for (int i = 0; i < numFrames; i++) {
            int start = i * HOP;
            double sum = 0;
            for (int j = start; j < start + WINDOW && j < mono.length; j++) sum += mono[j] * mono[j];
            double rms = Math.sqrt(sum / WINDOW);
            env[i] = rms > 1e-10 ? (float)(20.0 * Math.log10(rms)) : -120f;
        }
        return env;
    }

    private float[] onsetStrength(float[] energy) {
        float[] onset = new float[energy.length];
        for (int i = 1; i < energy.length; i++)
            onset[i] = Math.max(0f, energy[i] - energy[i - 1]);
        return onset;
    }

    private float[] adaptiveThreshold(float[] onset, int windowFrames, float multiplier) {
        float[] threshold = new float[onset.length];
        for (int i = 0; i < onset.length; i++) {
            int lo = Math.max(0, i - windowFrames);
            int hi = Math.min(onset.length - 1, i + windowFrames);
            float[] w = Arrays.copyOfRange(onset, lo, hi + 1);
            Arrays.sort(w);
            threshold[i] = multiplier * w[w.length / 2];
        }
        return threshold;
    }

    private float thresholdMultiplier() { return 3.0f - (sensitivity * 2.5f); }

    private List<Integer> pickPeaks(float[] onset, float[] threshold, int minGap) {
        List<Integer> peaks = new ArrayList<>();
        int lastPeak = -minGap;
        for (int i = 1; i < onset.length - 1; i++) {
            if (onset[i] > threshold[i]
                    && onset[i] >= onset[i - 1]
                    && onset[i] >= onset[i + 1]
                    && (i - lastPeak) >= minGap) {
                peaks.add(i);
                lastPeak = i;
            }
        }
        return peaks;
    }

    private float spectralCentroid(float[] mono, int offset, int sampleRate) {
        int len = Math.min(FFT_SIZE, mono.length - offset);
        if (len <= 0) return 500f;
        float[] windowed = new float[FFT_SIZE];
        for (int i = 0; i < len; i++) {
            double w = 0.5 * (1 - Math.cos(2 * Math.PI * i / (len - 1)));
            windowed[i] = (float)(mono[offset + i] * w);
        }
        int halfSize = FFT_SIZE / 2;
        double sumW = 0, sumWF = 0;
        for (int k = 0; k < halfSize; k++) {
            double re = 0, im = 0;
            for (int n = 0; n < FFT_SIZE; n++) {
                double angle = 2.0 * Math.PI * k * n / FFT_SIZE;
                re += windowed[n] * Math.cos(angle);
                im -= windowed[n] * Math.sin(angle);
            }
            double mag  = Math.sqrt(re * re + im * im);
            double freq = (double) k * sampleRate / FFT_SIZE;
            sumW  += mag;
            sumWF += mag * freq;
        }
        return sumW > 1e-10 ? (float)(sumWF / sumW) : 500f;
    }

    private void classify(float[] mono, SlicePoint sp, int sampleRate) {
        int len   = Math.min((int)(0.020f * sampleRate), FFT_SIZE);
        int start = Math.max(0, Math.min(sp.onsetSample, mono.length - len - 1));
        float centroid = spectralCentroid(mono, start, sampleRate);

        if      (centroid < 250f)                           { sp.type = DrumType.KICK;   sp.confidence = 0.80f; }
        else if (centroid < 800f && sp.durationSec < 0.08f){ sp.type = DrumType.SNARE;  sp.confidence = 0.75f; }
        else if (centroid < 800f)                           { sp.type = DrumType.TOM;    sp.confidence = 0.65f; }
        else if (centroid < 3000f)                          { sp.type = DrumType.HI_HAT; sp.confidence = 0.70f; }
        else                                                { sp.type = DrumType.CYMBAL; sp.confidence = 0.70f; }

        if (sp.energyDb > -6f) sp.confidence = Math.min(1f, sp.confidence + 0.1f);
    }

    private float peakDb(float[] mono, int start, int end) {
        float peak = 0;
        for (int i = start; i < end && i < mono.length; i++) {
            float a = Math.abs(mono[i]); if (a > peak) peak = a;
        }
        return peak > 1e-10f ? (float)(20 * Math.log10(peak)) : -120f;
    }

    private void estimateBpm(SliceResult result) {
        float sr = result.sampleRate;
        List<Float> intervals = new ArrayList<>();
        for (int i = 1; i < result.slices.size(); i++) {
            float ms = (result.slices.get(i).onsetSample
                      - result.slices.get(i - 1).onsetSample) / sr * 1000f;
            if (ms > 50 && ms < 2000) intervals.add(ms);
        }
        if (intervals.isEmpty()) return;
        int[] hist = new int[200];
        for (float ms : intervals) { int b = (int)(ms / 10); if (b >= 0 && b < 200) hist[b]++; }
        int maxBin = 0;
        for (int i = 1; i < hist.length; i++) if (hist[i] > hist[maxBin]) maxBin = i;
        if (hist[maxBin] == 0) return;
        float bpm = 60000f / ((maxBin + 0.5f) * 10f);
        while (bpm < 60f) bpm *= 2; while (bpm > 240f) bpm /= 2;
        result.bpm        = Math.round(bpm * 10f) / 10f;
        result.confidence = (float) hist[maxBin] / intervals.size();
    }

    private void detectRolls(SliceResult result, int sampleRate) {
        int rollThresh = (int)(0.08f * sampleRate);
        for (int i = 1; i < result.slices.size(); i++) {
            SlicePoint prev = result.slices.get(i - 1), curr = result.slices.get(i);
            if ((curr.onsetSample - prev.onsetSample) < rollThresh) {
                prev.isRoll = curr.isRoll = true;
                if (prev.type == curr.type) { prev.type = curr.type = DrumType.ROLL; }
            }
        }
    }

    private void detectFills(SliceResult result, int sampleRate) {
        if (result.bpm <= 0) return;
        int beatSamples = (int)(60f / result.bpm * sampleRate);
        int windowSize  = beatSamples * 2;
        for (int i = 0; i < result.slices.size(); i++) {
            int start = result.slices.get(i).onsetSample, count = 0;
            for (SlicePoint sp : result.slices)
                if (sp.onsetSample >= start && sp.onsetSample < start + windowSize) count++;
            if (count >= 6) {
                result.slices.get(i).isFill = true;
                if (result.slices.get(i).type != DrumType.ROLL)
                    result.slices.get(i).type = DrumType.FILL;
            }
        }
    }

    private void assignPads(SliceResult result) {
        Map<DrumType, Integer> map = new HashMap<>();
        map.put(DrumType.KICK, 1); map.put(DrumType.SNARE, 2); map.put(DrumType.HI_HAT, 3);
        map.put(DrumType.TOM,  4); map.put(DrumType.CYMBAL, 5); map.put(DrumType.FILL, 6);
        map.put(DrumType.ROLL, 7); map.put(DrumType.UNKNOWN, 8);
        for (SlicePoint sp : result.slices) sp.padIndex = map.getOrDefault(sp.type, 8);
    }

    private String buildSummary(SliceResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.slices.size()).append(" slices detected");
        if (result.bpm > 0)
            sb.append(String.format(Locale.US, "  •  %.1f BPM (%.0f%% conf)",
                    result.bpm, result.confidence * 100));
        sb.append("\n");
        if (result.kickCount  > 0) sb.append("🥁 ").append(result.kickCount).append("  ");
        if (result.snareCount > 0) sb.append("💥 ").append(result.snareCount).append("  ");
        if (result.hiHatCount > 0) sb.append("🎩 ").append(result.hiHatCount).append("  ");
        if (result.rollCount  > 0) sb.append("⚡ ").append(result.rollCount);
        sb.append("\n");
        sb.append(String.format(Locale.US, "%.2fs  •  %dHz  •  %dch",
                result.totalDurationSec, result.sampleRate, result.channels));
        return sb.toString();
    }

    private short[] normalize(short[] pcm, float targetDb) {
        float peak = 0;
        for (short s : pcm) { float a = Math.abs(s); if (a > peak) peak = a; }
        if (peak < 1) return pcm;
        float gain = (float)(Math.pow(10, targetDb / 20.0) * 32767f) / peak;
        short[] out = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++)
            out[i] = (short) Math.max(-32768, Math.min(32767, (int)(pcm[i] * gain)));
        return out;
    }

    private short[] resample(short[] input, int fromRate, int toRate) {
        if (fromRate == toRate) return input;
        double ratio = (double) toRate / fromRate;
        int    outLen = (int)(input.length * ratio);
        short[] out   = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcPos = i / ratio;
            int    lo     = (int) srcPos;
            int    hi     = Math.min(lo + 1, input.length - 1);
            double frac   = srcPos - lo;
            out[i] = (short)(input[lo] * (1 - frac) + input[hi] * frac);
        }
        return out;
    }
}
