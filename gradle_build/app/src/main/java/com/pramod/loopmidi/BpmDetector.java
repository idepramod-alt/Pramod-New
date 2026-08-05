package com.pramod.loopmidi;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Standalone BPM detection utility for audio files.
 * Decodes audio via MediaCodec → mono PCM → onset autocorrelation → BPM.
 * Fallback: 120 BPM if detection fails or confidence is too low.
 */
public class BpmDetector {

    private static final int HOP = 512;           // samples per analysis frame
    private static final float MIN_BPM = 60f;
    private static final float MAX_BPM = 240f;

    /** Result container holding detected BPM and sample rate used. */
    public static class BpmResult {
        public final int bpm;
        public final int sampleRate;
        public BpmResult(int bpm, int sampleRate) {
            this.bpm = bpm;
            this.sampleRate = sampleRate;
        }
    }

    /**
     * Detect BPM from an audio URI. Runs on any thread (blocks for decode).
     * Returns detected BPM as integer, or 120 on failure.
     */
    public static int detectBpm(Context context, Uri uri) {
        try {
            BpmResult result = decodeAndDetect(context, uri);
            return result.bpm;
        } catch (Exception e) {
            return 120;
        }
    }

    /** Decode audio URI to mono float PCM and detect BPM. */
    private static BpmResult decodeAndDetect(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int audioTrack = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat fmt = extractor.getTrackFormat(i);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrack = i;
                break;
            }
        }
        if (audioTrack < 0) return new BpmResult(120, 44100);

        extractor.selectTrack(audioTrack);
        MediaFormat format = extractor.getTrackFormat(audioTrack);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
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
                        byte[] bytes = new byte[info.size];
                        outBuf.get(bytes);
                        int nSamples = info.size / 2; // 16-bit
                        // Convert to mono: average channels or take left
                        for (int i = 0; i < nSamples; i += channels) {
                            int idx = i * 2;
                            if (idx + 1 < bytes.length) {
                                short s = (short) ((bytes[idx] & 0xFF) | (bytes[idx + 1] << 8));
                                float val = s / 32768f;
                                // If stereo, average both channels for better onset detection
                                if (channels == 2 && idx + 3 < bytes.length) {
                                    short s2 = (short) ((bytes[idx + 2] & 0xFF) | (bytes[idx + 3] << 8));
                                    val = (val + s2 / 32768f) / 2f;
                                }
                                samples.add(val);
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

        float[] pcm = new float[samples.size()];
        for (int i = 0; i < pcm.length; i++) pcm[i] = samples.get(i);

        int bpm = detectFromPcm(pcm, sampleRate);
        return new BpmResult(bpm, sampleRate);
    }

    /** Detect BPM from mono PCM via onset autocorrelation. */
    private static int detectFromPcm(float[] samples, int sampleRate) {
        int nHops = samples.length / HOP;
        if (nHops < 30) return 120;

        // RMS energy per hop
        float[] energy = new float[nHops];
        for (int i = 0; i < nHops; i++) {
            int start = i * HOP;
            float sum = 0;
            int end = Math.min(start + HOP, samples.length);
            for (int j = start; j < end; j++) {
                sum += samples[j] * samples[j];
            }
            energy[i] = (float) Math.sqrt(sum / HOP);
        }

        // Onset envelope: half-wave rectified first-order difference
        // Also apply a small noise gate to suppress quiet sections
        float noiseGate = 0.005f;
        float[] onset = new float[nHops];
        for (int i = 1; i < nHops; i++) {
            if (energy[i] < noiseGate && energy[i - 1] < noiseGate) {
                onset[i] = 0;
            } else {
                float d = energy[i] - energy[i - 1] * 0.9f; // leaky integrator
                onset[i] = d > 0 ? d : 0;
            }
        }

        // Autocorrelation over BPM range 60-240
        // lag (in hops) = sampleRate / (HOP * bpm / 60)
        int minLag = Math.max(2, (int) (sampleRate * 60.0 / (HOP * MAX_BPM)));
        int maxLag = Math.min(nHops / 3, (int) (sampleRate * 60.0 / (HOP * MIN_BPM)));

        if (minLag >= maxLag) return 120;

        float bestScore = -1;
        int bestLag = -1;

        for (int lag = minLag; lag <= maxLag; lag++) {
            float sum = 0;
            float normA = 0;
            float normB = 0;
            int count = nHops - lag;
            for (int i = 0; i < count; i++) {
                sum += onset[i] * onset[i + lag];
                normA += onset[i] * onset[i];
                normB += onset[i + lag] * onset[i + lag];
            }
            float denom = (float) Math.sqrt(normA * normB);
            if (denom > 0) sum /= denom;
            if (sum > bestScore) {
                bestScore = sum;
                bestLag = lag;
            }
        }

        if (bestLag <= 0 || bestScore < 0.10f) return 120;

        float bpm = 60.0f * sampleRate / (HOP * bestLag);

        // Harmonic preference: check if BPM/2 or BPM*2 has better on-beat energy
        float bpm2 = bpm * 2f;
        float bpmHalf = bpm / 2f;
        if (bpm2 <= MAX_BPM && bpm2 >= MIN_BPM) {
            int lag2 = (int) (sampleRate * 60.0 / (HOP * bpm2));
            float score2 = autocorrelationScore(onset, nHops, lag2);
            if (score2 > bestScore * 1.15f) bpm = bpm2;
        }
        if (bpmHalf >= MIN_BPM && bpmHalf <= MAX_BPM) {
            int lagHalf = (int) (sampleRate * 60.0 / (HOP * bpmHalf));
            float scoreHalf = autocorrelationScore(onset, nHops, lagHalf);
            if (scoreHalf > bestScore * 1.15f) bpm = bpmHalf;
        }

        if (bpm < MIN_BPM || bpm > MAX_BPM) return 120;

        // Snap to common BPM rounding (prefer round numbers)
        int rounded = Math.round(bpm);
        if (Math.abs(bpm - rounded) < 1.5f && rounded >= MIN_BPM && rounded <= MAX_BPM) {
            return rounded;
        }
        return Math.round(bpm);
    }

    /** Compute normalized autocorrelation score for a specific lag. */
    private static float autocorrelationScore(float[] onset, int nHops, int lag) {
        if (lag <= 0 || lag >= nHops) return 0;
        float sum = 0, normA = 0, normB = 0;
        int count = nHops - lag;
        for (int i = 0; i < count; i++) {
            sum += onset[i] * onset[i + lag];
            normA += onset[i] * onset[i];
            normB += onset[i + lag] * onset[i + lag];
        }
        float denom = (float) Math.sqrt(normA * normB);
        return denom > 0 ? sum / denom : 0;
    }
}
