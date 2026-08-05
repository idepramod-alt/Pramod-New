package com.pramod.loopmidi;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Standalone BPM detection utility for audio files.
 * Uses two independent methods and picks the best result:
 *   Method 1: Peak interval clustering (robust for drum loops)
 *   Method 2: Autocorrelation (robust for melodic content)
 * Fallback: 120 BPM if both fail.
 */
public class BpmDetector {

    private static final int HOP = 1024;          // ~23ms at 44100
    private static final float MIN_BPM = 60f;
    private static final float MAX_BPM = 240f;

    /** Public API: detect BPM from URI, returns integer (60-240 or 120 fallback). */
    public static int detectBpm(Context context, Uri uri) {
        try {
            float[] pcm = decodeToMono(context, uri);
            if (pcm == null || pcm.length < 44100) return 120; // < 1 second
            int sr = getSampleRate(context, uri);
            return detectBest(pcm, sr);
        } catch (Exception e) {
            return 120;
        }
    }

    /** Get sample rate without full decode. */
    private static int getSampleRate(Context context, Uri uri) {
        try {
            MediaExtractor ext = new MediaExtractor();
            ext.setDataSource(context, uri, null);
            for (int i = 0; i < ext.getTrackCount(); i++) {
                MediaFormat fmt = ext.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    ext.release();
                    return sr;
                }
            }
            ext.release();
        } catch (Exception ignored) {}
        return 44100;
    }

    /** Decode audio URI to mono float PCM [-1,1]. */
    private static float[] decodeToMono(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int audioTrack = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat fmt = extractor.getTrackFormat(i);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { audioTrack = i; break; }
        }
        if (audioTrack < 0) return null;

        extractor.selectTrack(audioTrack);
        MediaFormat format = extractor.getTrackFormat(audioTrack);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        String mime = format.getString(MediaFormat.KEY_MIME);

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();

        ArrayList<Float> samples = new ArrayList<>();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean done = false;

        while (!done) {
            int inIdx = decoder.dequeueInputBuffer(10000);
            if (inIdx >= 0) {
                ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                int sz = extractor.readSampleData(inBuf, 0);
                if (sz < 0) {
                    decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    decoder.queueInputBuffer(inIdx, 0, sz, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            int outIdx = decoder.dequeueOutputBuffer(info, 10000);
            if (outIdx >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true;
                if (info.size > 0) {
                    ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                    if (outBuf != null) {
                        // CRITICAL: position to data offset
                        outBuf.position(info.offset);
                        byte[] bytes = new byte[info.size];
                        outBuf.get(bytes);

                        int bytesPerFrame = channels * 2; // 16-bit
                        int nFrames = info.size / bytesPerFrame;

                        for (int f = 0; f < nFrames; f++) {
                            int base = f * bytesPerFrame;
                            // Read all channels and average for mono
                            float sum = 0;
                            for (int ch = 0; ch < channels; ch++) {
                                int idx = base + ch * 2;
                                if (idx + 1 < bytes.length) {
                                    short s = (short) ((bytes[idx] & 0xFF) | (bytes[idx + 1] << 8));
                                    sum += s / 32768f;
                                }
                            }
                            samples.add(sum / channels);
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIdx, false);
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();

        float[] pcm = new float[samples.size()];
        for (int i = 0; i < pcm.length; i++) pcm[i] = samples.get(i);
        return pcm;
    }

    /** Run both detection methods and pick best. */
    private static int detectBest(float[] pcm, int sampleRate) {
        int bpm1 = detectByPeakInterval(pcm, sampleRate);
        int bpm2 = detectByAutocorrelation(pcm, sampleRate);

        // Both failed
        if (bpm1 == 120 && bpm2 == 120) return 120;

        // They agree (within 3 BPM)
        if (Math.abs(bpm1 - bpm2) <= 3) return bpm1;

        // Pick the one closer to a "normal" BPM (100-140 range is most common)
        if (bpm1 >= 100 && bpm1 <= 140) return bpm1;
        if (bpm2 >= 100 && bpm2 <= 140) return bpm2;

        // Pick the one closer to 120 (most common default)
        return (Math.abs(bpm1 - 120) <= Math.abs(bpm2 - 120)) ? bpm1 : bpm2;
    }

    // ════════════════════════════════════════════════════════════════════════
    // METHOD 1: Peak Interval Clustering
    // ════════════════════════════════════════════════════════════════════════

    private static int detectByPeakInterval(float[] samples, int sampleRate) {
        float[] energy = computeEnergy(samples);
        float[] onset = computeOnset(energy);

        // Find peaks (local maxima above adaptive threshold)
        float threshold = computeMedian(onset) * 1.5f;
        if (threshold < 0.001f) threshold = computeMean(onset) * 2f;

        ArrayList<Integer> peaks = new ArrayList<>();
        for (int i = 2; i < onset.length - 1; i++) {
            if (onset[i] > threshold && onset[i] > onset[i - 1] && onset[i] >= onset[i + 1]) {
                peaks.add(i);
            }
        }

        if (peaks.size() < 4) return 120;

        // Compute intervals between consecutive peaks
        ArrayList<Float> intervals = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            intervals.add((float) (peaks.get(i) - peaks.get(i - 1)));
        }

        // Cluster intervals: find the most common interval within tolerance
        float bestInterval = findModeInterval(intervals);
        if (bestInterval <= 0) return 120;

        float bpm = 60.0f * sampleRate / (HOP * bestInterval);
        return clampBpm(bpm);
    }

    /** Find the mode interval with clustering. */
    private static float findModeInterval(ArrayList<Float> intervals) {
        if (intervals.isEmpty()) return 0;
        Collections.sort(intervals);

        float bestInterval = intervals.get(0);
        int bestCount = 0;

        // Sliding window: count intervals within ±0.5 of each candidate
        for (int i = 0; i < intervals.size(); i++) {
            float center = intervals.get(i);
            int count = 0;
            for (float v : intervals) {
                if (Math.abs(v - center) <= 0.5f) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                bestInterval = center;
            }
        }

        return bestCount >= 3 ? bestInterval : 0;
    }

    // ════════════════════════════════════════════════════════════════════════
    // METHOD 2: Autocorrelation (improved)
    // ════════════════════════════════════════════════════════════════════════

    private static int detectByAutocorrelation(float[] samples, int sampleRate) {
        float[] energy = computeEnergy(samples);
        float[] onset = computeOnset(energy);

        int nHops = onset.length;
        int minLag = Math.max(2, (int) (sampleRate * 60.0 / (HOP * MAX_BPM)));
        int maxLag = Math.min(nHops / 2, (int) (sampleRate * 60.0 / (HOP * MIN_BPM)));
        if (minLag >= maxLag) return 120;

        // Compute autocorrelation
        float[] acf = new float[maxLag + 1];
        for (int lag = minLag; lag <= maxLag; lag++) {
            float sum = 0, normA = 0, normB = 0;
            int count = nHops - lag;
            for (int i = 0; i < count; i++) {
                sum += onset[i] * onset[i + lag];
                normA += onset[i] * onset[i];
                normB += onset[i + lag] * onset[i + lag];
            }
            float denom = (float) Math.sqrt(normA * normB);
            acf[lag] = denom > 0 ? sum / denom : 0;
        }

        // Find peak in ACF (skip lags near 0 and very short periods)
        float bestScore = -1;
        int bestLag = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            if (acf[lag] > bestScore) {
                bestScore = acf[lag];
                bestLag = lag;
            }
        }

        if (bestLag <= 0 || bestScore < 0.08f) return 120;

        float bpm = 60.0f * sampleRate / (HOP * bestLag);

        // Check harmonics: prefer the one where on-beat energy is strongest
        float best = bpm;
        float bestBeatEnergy = beatEnergy(onset, nHops, sampleRate, bpm);

        for (int mult = 2; mult <= 3; mult++) {
            float candidate = bpm * mult;
            if (candidate <= MAX_BPM) {
                float e = beatEnergy(onset, nHops, sampleRate, candidate);
                if (e > bestBeatEnergy * 1.2f) { best = candidate; bestBeatEnergy = e; }
            }
        }
        for (int div = 2; div <= 3; div++) {
            float candidate = bpm / div;
            if (candidate >= MIN_BPM) {
                float e = beatEnergy(onset, nHops, sampleRate, candidate);
                if (e > bestBeatEnergy * 1.2f) { best = candidate; bestBeatEnergy = e; }
            }
        }

        return clampBpm(best);
    }

    /** Measure how much onset energy falls on the beat grid for a given BPM. */
    private static float beatEnergy(float[] onset, int nHops, int sr, float bpm) {
        float lag = (float) (sr) / (HOP * bpm / 60.0f);
        if (lag < 1) return 0;
        float total = 0;
        for (int i = 0; i < nHops; i += Math.max(1, (int) lag)) {
            total += onset[i];
        }
        return total;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Shared utilities
    // ════════════════════════════════════════════════════════════════════════

    /** Compute RMS energy per hop. */
    private static float[] computeEnergy(float[] samples) {
        int nHops = samples.length / HOP;
        float[] energy = new float[nHops];
        for (int i = 0; i < nHops; i++) {
            int start = i * HOP;
            float sum = 0;
            int end = Math.min(start + HOP, samples.length);
            for (int j = start; j < end; j++) {
                sum += samples[j] * samples[j];
            }
            energy[i] = (float) Math.sqrt(sum / (end - start));
        }
        return energy;
    }

    /** Compute onset strength: spectral flux approximation with adaptive threshold. */
    private static float[] computeOnset(float[] energy) {
        int n = energy.length;
        float[] onset = new float[n];

        // Raw onset: half-wave rectified energy increase
        for (int i = 1; i < n; i++) {
            float diff = energy[i] - energy[i - 1];
            onset[i] = diff > 0 ? diff : 0;
        }

        // Adaptive threshold: local median over 16 frames
        int window = 16;
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - window);
            int hi = Math.min(n, i + window + 1);
            float[] neighborhood = new float[hi - lo];
            System.arraycopy(onset, lo, neighborhood, 0, hi - lo);
            Arrays.sort(neighborhood);
            float median = neighborhood[neighborhood.length / 2];
            float threshold = median * 1.5f;
            if (onset[i] < threshold) onset[i] = 0;
        }

        return onset;
    }

    private static float computeMedian(float[] arr) {
        float[] sorted = arr.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static float computeMean(float[] arr) {
        float sum = 0;
        for (float v : arr) sum += v;
        return sum / arr.length;
    }

    private static int clampBpm(float bpm) {
        if (bpm < MIN_BPM || bpm > MAX_BPM) return 120;
        int rounded = Math.round(bpm);
        if (rounded >= MIN_BPM && rounded <= MAX_BPM) return rounded;
        return 120;
    }
}
