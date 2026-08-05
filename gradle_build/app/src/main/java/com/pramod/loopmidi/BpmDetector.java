package com.pramod.loopmidi;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Standalone BPM detection utility for audio files.
 * Decodes audio via MediaCodec → mono PCM → onset autocorrelation → BPM.
 * Fallback: 120 BPM if detection fails or confidence is too low.
 */
public class BpmDetector {

    private static final int TARGET_SR = 22050;  // Low SR for efficiency
    private static final int HOP = 512;           // ~23ms hop at 22050 Hz
    private static final float MIN_BPM = 60f;
    private static final float MAX_BPM = 240f;

    /**
     * Detect BPM from an audio URI. Runs on any thread (blocks for decode).
     * Returns detected BPM as integer, or 120 on failure.
     */
    public static int detectBpm(Context context, Uri uri) {
        try {
            float[] pcm = decodeToMono(context, uri);
            if (pcm == null || pcm.length < TARGET_SR) return 120;
            return detectFromPcm(pcm);
        } catch (Exception e) {
            return 120;
        }
    }

    /** Decode audio URI to mono float PCM at TARGET_SR. */
    private static float[] decodeToMono(Context context, Uri uri) throws Exception {
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
                        byte[] bytes = new byte[info.size];
                        outBuf.get(bytes);
                        int nSamples = info.size / 2; // 16-bit
                        for (int i = 0; i < nSamples; i += channels) {
                            int idx = i * 2;
                            if (idx + 1 < bytes.length) {
                                short s = (short) ((bytes[idx] & 0xFF) | (bytes[idx + 1] << 8));
                                samples.add(s / 32768f);
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
        return pcm;
    }

    /** Detect BPM from mono PCM via onset autocorrelation. */
    private static int detectFromPcm(float[] samples) {
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

        // Onset envelope (half-wave rectified diff)
        float[] onset = new float[nHops];
        for (int i = 1; i < nHops; i++) {
            float d = energy[i] - energy[i - 1];
            onset[i] = d > 0 ? d : 0;
        }

        // Autocorrelation over BPM range 60-240
        int minLag = Math.max(2, (int) (TARGET_SR / (HOP * MAX_BPM / 60.0)));
        int maxLag = Math.min(nHops / 2, (int) (TARGET_SR / (HOP * MIN_BPM / 60.0)));

        float bestScore = -1;
        int bestLag = -1;

        for (int lag = minLag; lag <= maxLag; lag++) {
            float sum = 0;
            float denom = 0;
            for (int i = 0; i < nHops - lag; i++) {
                sum += onset[i] * onset[i + lag];
                denom += onset[i + lag] * onset[i + lag];
            }
            if (denom > 0) sum /= Math.sqrt(denom);
            if (sum > bestScore) {
                bestScore = sum;
                bestLag = lag;
            }
        }

        if (bestLag <= 0 || bestScore < 0.05f) return 120;

        float bpm = 60.0f * TARGET_SR / (HOP * bestLag);
        if (bpm < MIN_BPM || bpm > MAX_BPM) return 120;
        return Math.round(bpm);
    }
}
