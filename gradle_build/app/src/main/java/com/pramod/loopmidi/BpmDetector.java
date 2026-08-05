package com.pramod.loopmidi;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * BPM detection utility — two-method consensus algorithm.
 * Decodes audio to mono PCM, then runs peak-interval + autocorrelation detection.
 * Fast WAV path avoids MediaCodec entirely.
 */
public class BpmDetector {

    private static final int HOP = 1024;
    private static final float MIN_BPM = 60f;
    private static final float MAX_BPM = 240f;

    /** Detect BPM from an audio URI. Returns integer 60-240, or 120 fallback. */
    public static int detectBpm(Context context, Uri uri) {
        try {
            float[] pcm = decodeToMono(context, uri);
            if (pcm == null || pcm.length < 22050) return 120; // need at least 0.5s
            int sr = getSampleRate(context, uri);
            return detectBest(pcm, sr);
        } catch (Exception e) {
            return 120;
        }
    }

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

    // ═══════════════════════════════════════════════════════════════════════
    // DECODE — fast WAV path + MediaCodec fallback
    // ═══════════════════════════════════════════════════════════════════════

    private static float[] decodeToMono(Context context, Uri uri) throws Exception {
        // Try fast WAV path first (no MediaCodec needed)
        float[] wav = tryDecodeWav(context, uri);
        if (wav != null && wav.length > 0) return wav;

        // MediaCodec path for compressed formats
        return decodeWithMediaCodec(context, uri);
    }

    /** Fast decoder for 16-bit WAV files — parses header directly. */
    private static float[] tryDecodeWav(Context context, Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            byte[] header = new byte[44];
            int read = is.read(header, 0, 44);
            if (read < 44) { is.close(); return null; }

            // Check RIFF/WAVE magic
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') { is.close(); return null; }
            if (header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E') { is.close(); return null; }

            int channels = (header[22] & 0xFF) | ((header[23] & 0xFF) << 8);
            int bitsPerSample = (header[34] & 0xFF) | ((header[35] & 0xFF) << 8);
            int dataSize = (header[40] & 0xFF) | ((header[41] & 0xFF) << 8)
                    | ((header[42] & 0xFF) << 16) | ((header[43] & 0xFF) << 24);

            if (bitsPerSample != 16 || channels < 1 || channels > 2) { is.close(); return null; }
            if (dataSize <= 0 || dataSize > 100_000_000) { is.close(); return null; } // sanity check

            int bytesPerFrame = channels * 2;
            int totalFrames = dataSize / bytesPerFrame;

            // Read all PCM data at once
            byte[] pcmBytes = new byte[dataSize];
            int offset = 0;
            while (offset < dataSize) {
                int n = is.read(pcmBytes, offset, dataSize - offset);
                if (n <= 0) break;
                offset += n;
            }
            is.close();

            // Convert to mono float using ShortBuffer (efficient, no Float boxing)
            ShortBuffer sb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            short[] shortBuf = new short[totalFrames * channels];
            sb.get(shortBuf);

            float[] mono = new float[totalFrames];
            if (channels == 2) {
                for (int i = 0; i < totalFrames; i++) {
                    mono[i] = (shortBuf[i * 2] + shortBuf[i * 2 + 1]) / (2f * 32768f);
                }
            } else {
                for (int i = 0; i < totalFrames; i++) {
                    mono[i] = shortBuf[i] / 32768f;
                }
            }
            return mono;
        } catch (Exception e) {
            return null;
        }
    }

    /** MediaCodec decoder for compressed formats (MP3, OGG, FLAC, AAC, etc). */
    private static float[] decodeWithMediaCodec(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int audioTrack = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat fmt = extractor.getTrackFormat(i);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { audioTrack = i; break; }
        }
        if (audioTrack < 0) { extractor.release(); return null; }

        extractor.selectTrack(audioTrack);
        MediaFormat format = extractor.getTrackFormat(audioTrack);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime == null) { extractor.release(); return null; }

        // Estimate output size for pre-allocation
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION) : 30_000_000L; // 30s default
        int estFrames = (int) (44100L * durationUs / 1_000_000L);

        // Use primitive float array instead of ArrayList<Float>
        float[] pcm = new float[Math.max(1024, Math.min(estFrames, 2_000_000))]; // cap at 2M frames
        int pcmPos = 0;

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean done = false;

        while (!done) {
            // Feed input
            int inIdx = decoder.dequeueInputBuffer(10000);
            if (inIdx >= 0) {
                ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                if (inBuf != null) {
                    int sz = extractor.readSampleData(inBuf, 0);
                    if (sz < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, sz, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            // Read output
            int outIdx = decoder.dequeueOutputBuffer(info, 10000);
            if (outIdx >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true;
                if (info.size > 0) {
                    ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                    if (outBuf != null) {
                        outBuf.position(info.offset);
                        outBuf.limit(info.offset + info.size);

                        // Read as short array via ShortBuffer (efficient)
                        int nShorts = info.size / 2;
                        short[] sbuf = new short[nShorts];
                        outBuf.asShortBuffer().get(sbuf);

                        int bytesPerFrame = channels * 2;
                        int nFrames = info.size / bytesPerFrame;

                        // Ensure pcm array is big enough
                        if (pcmPos + nFrames > pcm.length) {
                            float[] newPcm = new float[Math.min(pcm.length * 2, pcmPos + nFrames + 1024)];
                            System.arraycopy(pcm, 0, newPcm, 0, pcmPos);
                            pcm = newPcm;
                        }

                        // Convert to mono
                        if (channels == 2) {
                            for (int f = 0; f < nFrames; f++) {
                                int si = f * 2;
                                if (si + 1 < nShorts) {
                                    pcm[pcmPos++] = (sbuf[si] + sbuf[si + 1]) / (2f * 32768f);
                                }
                            }
                        } else {
                            for (int f = 0; f < nFrames; f++) {
                                if (f < nShorts) {
                                    pcm[pcmPos++] = sbuf[f] / 32768f;
                                }
                            }
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIdx, false);
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();

        // Trim to actual size
        if (pcmPos < pcm.length) {
            float[] trimmed = new float[pcmPos];
            System.arraycopy(pcm, 0, trimmed, 0, pcmPos);
            return trimmed;
        }
        return pcm;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BPM DETECTION — dual-method consensus
    // ═══════════════════════════════════════════════════════════════════════

    private static int detectBest(float[] pcm, int sampleRate) {
        int bpm1 = detectByPeakInterval(pcm, sampleRate);
        int bpm2 = detectByAutocorrelation(pcm, sampleRate);

        if (bpm1 == 120 && bpm2 == 120) return 120;
        if (Math.abs(bpm1 - bpm2) <= 3) return bpm1;
        if (bpm1 >= 90 && bpm1 <= 150) return bpm1;
        if (bpm2 >= 90 && bpm2 <= 150) return bpm2;
        return (Math.abs(bpm1 - 120) <= Math.abs(bpm2 - 120)) ? bpm1 : bpm2;
    }

    // ── Method 1: Peak Interval ──────────────────────────────────────────

    private static int detectByPeakInterval(float[] samples, int sampleRate) {
        float[] energy = computeEnergy(samples);
        float[] onset = computeOnset(energy);

        // Adaptive threshold
        float med = computeMedian(onset);
        float threshold = med * 1.5f;
        if (threshold < 0.001f) threshold = computeMean(onset) * 2f;
        if (threshold < 0.0005f) return 120;

        // Find peaks
        java.util.ArrayList<Integer> peaks = new java.util.ArrayList<>();
        for (int i = 2; i < onset.length - 1; i++) {
            if (onset[i] > threshold && onset[i] > onset[i - 1] && onset[i] >= onset[i + 1]) {
                peaks.add(i);
            }
        }
        if (peaks.size() < 4) return 120;

        // Compute intervals
        java.util.ArrayList<Float> intervals = new java.util.ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            intervals.add((float) (peaks.get(i) - peaks.get(i - 1)));
        }

        float bestInterval = findModeInterval(intervals);
        if (bestInterval <= 0) return 120;

        float bpm = 60.0f * sampleRate / (HOP * bestInterval);
        return clampBpm(bpm);
    }

    private static float findModeInterval(java.util.ArrayList<Float> intervals) {
        if (intervals.isEmpty()) return 0;
        java.util.Collections.sort(intervals);
        float bestInterval = intervals.get(0);
        int bestCount = 0;
        for (int i = 0; i < intervals.size(); i++) {
            float center = intervals.get(i);
            int count = 0;
            for (float v : intervals) {
                if (Math.abs(v - center) <= 0.5f) count++;
            }
            if (count > bestCount) { bestCount = count; bestInterval = center; }
        }
        return bestCount >= 3 ? bestInterval : 0;
    }

    // ── Method 2: Autocorrelation ────────────────────────────────────────

    private static int detectByAutocorrelation(float[] samples, int sampleRate) {
        float[] energy = computeEnergy(samples);
        float[] onset = computeOnset(energy);
        int nHops = onset.length;

        int minLag = Math.max(2, (int) (sampleRate * 60.0 / (HOP * MAX_BPM)));
        int maxLag = Math.min(nHops / 2, (int) (sampleRate * 60.0 / (HOP * MIN_BPM)));
        if (minLag >= maxLag) return 120;

        float bestScore = -1;
        int bestLag = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            float sum = 0, normA = 0, normB = 0;
            int count = nHops - lag;
            for (int i = 0; i < count; i++) {
                sum += onset[i] * onset[i + lag];
                normA += onset[i] * onset[i];
                normB += onset[i + lag] * onset[i + lag];
            }
            float denom = (float) Math.sqrt(normA * normB);
            float acf = denom > 0 ? sum / denom : 0;
            if (acf > bestScore) { bestScore = acf; bestLag = lag; }
        }

        if (bestLag <= 0 || bestScore < 0.08f) return 120;
        float bpm = 60.0f * sampleRate / (HOP * bestLag);

        // Check harmonics
        float best = bpm;
        float bestBeatE = beatEnergy(onset, nHops, sampleRate, bpm);
        for (int m = 2; m <= 3; m++) {
            float c = bpm * m;
            if (c <= MAX_BPM) {
                float e = beatEnergy(onset, nHops, sampleRate, c);
                if (e > bestBeatE * 1.15f) { best = c; bestBeatE = e; }
            }
        }
        for (int d = 2; d <= 3; d++) {
            float c = bpm / d;
            if (c >= MIN_BPM) {
                float e = beatEnergy(onset, nHops, sampleRate, c);
                if (e > bestBeatE * 1.15f) { best = c; bestBeatE = e; }
            }
        }
        return clampBpm(best);
    }

    private static float beatEnergy(float[] onset, int nHops, int sr, float bpm) {
        float lag = (float) sr / (HOP * bpm / 60.0f);
        if (lag < 1) return 0;
        float total = 0;
        for (int i = 0; i < nHops; i += Math.max(1, (int) lag)) total += onset[i];
        return total;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Shared utilities
    // ═══════════════════════════════════════════════════════════════════════

    private static float[] computeEnergy(float[] samples) {
        int nHops = samples.length / HOP;
        float[] energy = new float[nHops];
        for (int i = 0; i < nHops; i++) {
            int start = i * HOP;
            float sum = 0;
            int end = Math.min(start + HOP, samples.length);
            for (int j = start; j < end; j++) sum += samples[j] * samples[j];
            energy[i] = (float) Math.sqrt(sum / (end - start));
        }
        return energy;
    }

    private static float[] computeOnset(float[] energy) {
        int n = energy.length;
        float[] onset = new float[n];
        for (int i = 1; i < n; i++) {
            float diff = energy[i] - energy[i - 1];
            onset[i] = diff > 0 ? diff : 0;
        }
        // Adaptive median threshold
        int window = 16;
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - window);
            int hi = Math.min(n, i + window + 1);
            float[] nb = new float[hi - lo];
            System.arraycopy(onset, lo, nb, 0, hi - lo);
            java.util.Arrays.sort(nb);
            float threshold = nb[nb.length / 2] * 1.5f;
            if (onset[i] < threshold) onset[i] = 0;
        }
        return onset;
    }

    private static float computeMedian(float[] arr) {
        float[] s = arr.clone();
        java.util.Arrays.sort(s);
        return s[s.length / 2];
    }

    private static float computeMean(float[] arr) {
        float sum = 0;
        for (float v : arr) sum += v;
        return sum / arr.length;
    }

    private static int clampBpm(float bpm) {
        if (bpm < MIN_BPM || bpm > MAX_BPM) return 120;
        int r = Math.round(bpm);
        return (r >= MIN_BPM && r <= MAX_BPM) ? r : 120;
    }
}
