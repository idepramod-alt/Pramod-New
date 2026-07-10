package com.pramod.soundstudio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility: read/write WAV files, auto-detect silence, trim.
 */
public class AudioTrimmer {

    public static final int WAV_HEADER_SIZE = 44;

    /** Read raw PCM 16-bit samples from a WAV file (skips 44-byte header). */
    public static short[] readWavPcm(File wavFile) throws IOException {
        long fileLen = wavFile.length();
        int  dataLen = (int) (fileLen - WAV_HEADER_SIZE);
        if (dataLen <= 0) return new short[0];

        byte[]  raw     = new byte[dataLen];
        try (FileInputStream fis = new FileInputStream(wavFile)) {
            //noinspection ResultOfMethodCallIgnored
            fis.skip(WAV_HEADER_SIZE);
            int read = 0;
            while (read < dataLen) {
                int n = fis.read(raw, read, dataLen - read);
                if (n < 0) break;
                read += n;
            }
        }

        short[] samples = new short[raw.length / 2];
        ByteBuffer buf  = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        buf.asShortBuffer().get(samples);
        return samples;
    }

    /** Read WAV header to get sample rate, channels, bits/sample. */
    public static int[] readWavHeader(File wavFile) throws IOException {
        // Returns [sampleRate, channels, bitsPerSample]
        byte[] hdr = new byte[44];
        try (FileInputStream fis = new FileInputStream(wavFile)) {
            int n = 0;
            while (n < 44) {
                int r = fis.read(hdr, n, 44 - n);
                if (r < 0) break;
                n += r;
            }
        }
        ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
        int channels      = buf.getShort(22) & 0xFFFF;
        int sampleRate    = buf.getInt(24);
        int bitsPerSample = buf.getShort(34) & 0xFFFF;
        return new int[]{sampleRate, channels, bitsPerSample};
    }

    /**
     * Auto-detect silence trim points.
     * @param samples   PCM 16-bit samples
     * @param threshold silence threshold (0–32767, e.g. 500)
     * @return int[]{startSample, endSample} — indices of first/last non-silent sample
     */
    public static int[] findTrimPoints(short[] samples, int threshold) {
        if (samples == null || samples.length == 0) return new int[]{0, 0};

        int start = 0;
        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i]) > threshold) { start = i; break; }
        }

        int end = samples.length - 1;
        for (int i = samples.length - 1; i >= 0; i--) {
            if (Math.abs(samples[i]) > threshold) { end = i; break; }
        }

        // Add 100ms padding on each side if possible
        // padding handled by caller; here just return the raw indices
        return new int[]{start, end};
    }

    /**
     * Write a standard 44-byte WAV header.
     */
    /**
     * Write WAV header. numSamples = total interleaved PCM words (already includes all channels).
     * dataSize = numSamples * (bitsPerSample/8) — do NOT multiply by channels again here.
     */
    public static void writeWavHeader(FileOutputStream fos, int sampleRate,
                                      int channels, int bitsPerSample,
                                      int numSamples) throws IOException {
        int dataSize   = numSamples * (bitsPerSample / 8); // samples already interleaved
        int byteRate   = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        ByteBuffer buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        // RIFF
        buf.put(new byte[]{'R','I','F','F'});
        buf.putInt(36 + dataSize);
        buf.put(new byte[]{'W','A','V','E'});
        // fmt chunk
        buf.put(new byte[]{'f','m','t',' '});
        buf.putInt(16);                 // chunk size
        buf.putShort((short) 1);        // PCM
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);
        // data chunk
        buf.put(new byte[]{'d','a','t','a'});
        buf.putInt(dataSize);

        fos.write(buf.array());
    }

    /**
     * Write PCM 16-bit samples as a WAV file.
     * samples[] is interleaved (all channels), so numFrames = samples.length / channels.
     */
    public static void writePcmToWav(short[] samples, int sampleRate,
                                     int channels, File outFile) throws IOException {
        // numSamples in WAV header means total interleaved samples (not per-channel frames)
        int numSamples = samples.length; // total PCM words (already includes all channels)
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            writeWavHeader(fos, sampleRate, channels, 16, numSamples);
            ByteBuffer buf = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : samples) buf.putShort(s);
            fos.write(buf.array());
        }
    }

    /**
     * Trim a WAV file between startSec and endSec.
     * Returns the output file.
     */
    public static File trimWav(File input, double startSec, double endSec,
                               File outputDir) throws IOException {
        int[] hdr      = readWavHeader(input);
        int sampleRate = hdr[0];
        int channels   = hdr[1];
        int bps        = hdr[2];

        short[] all    = readWavPcm(input);
        int startIdx   = (int) (startSec * sampleRate) * channels;
        int endIdx     = (int) (endSec   * sampleRate) * channels;
        startIdx = Math.max(0,   Math.min(startIdx, all.length));
        endIdx   = Math.max(0,   Math.min(endIdx,   all.length));

        if (endIdx <= startIdx) throw new IOException("Trim range is empty");

        int    trimLen     = endIdx - startIdx;
        short[] trimmed    = new short[trimLen];
        System.arraycopy(all, startIdx, trimmed, 0, trimLen);

        String outName = input.getName().replace(".wav", "_trimmed.wav");
        File   outFile = new File(outputDir, outName);
        writePcmToWav(trimmed, sampleRate, channels, outFile);
        return outFile;
    }

    /**
     * Auto-trim silence from start/end of WAV file.
     * threshold: 0–32767 (e.g. 300 = -40dB approx)
     * paddingMs: milliseconds of silence to keep as padding
     */
    public static TrimResult autoTrimWav(File input, int threshold,
                                         int paddingMs, File outputDir) throws IOException {
        int[] hdr      = readWavHeader(input);
        int sampleRate = hdr[0];
        int channels   = hdr[1];

        short[] samples = readWavPcm(input);
        int[]   pts     = findTrimPoints(samples, threshold);

        int paddingSamples = (int) (paddingMs * sampleRate / 1000.0) * channels;
        int startIdx = Math.max(0,              pts[0] - paddingSamples);
        int endIdx   = Math.min(samples.length, pts[1] + paddingSamples);

        double removedStart = (double) pts[0] / (sampleRate * channels);
        double removedEnd   = (double)(samples.length - pts[1] - 1) / (sampleRate * channels);

        double totalSec     = (double) samples.length / (sampleRate * channels);

        String outName = input.getName().replace(".wav", "_trimmed.wav");
        File   outFile = new File(outputDir, outName);

        int trimLen    = endIdx - startIdx;
        short[] trimmed = new short[Math.max(0, trimLen)];
        if (trimLen > 0) System.arraycopy(samples, startIdx, trimmed, 0, trimLen);
        writePcmToWav(trimmed, sampleRate, channels, outFile);

        return new TrimResult(outFile, removedStart, removedEnd,
                              (double) trimmed.length / (sampleRate * channels));
    }

    /** Convert normalized float[] (0.0–1.0) from short[] samples for waveform display. */
    public static float[] toWaveformAmplitudes(short[] samples, int targetBars) {
        if (samples == null || samples.length == 0 || targetBars == 0)
            return new float[0];

        float[] bars  = new float[targetBars];
        int chunk     = Math.max(1, samples.length / targetBars);
        for (int i = 0; i < targetBars; i++) {
            int from = i * chunk;
            int to   = Math.min(from + chunk, samples.length);
            long sum = 0;
            for (int j = from; j < to; j++) sum += Math.abs(samples[j]);
            bars[i] = (float)(sum / (to - from)) / 32767f;
        }
        return bars;
    }

    public static class TrimResult {
        public final File   outputFile;
        public final double removedStartSec;
        public final double removedEndSec;
        public final double newDurationSec;
        TrimResult(File f, double s, double e, double d) {
            outputFile = f; removedStartSec = s; removedEndSec = e; newDurationSec = d;
        }
    }
}
