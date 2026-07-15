package com.pramod.soundstudio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EchoRecordingEngine — Real-time Echo Recording DSP
 *
 * Records from mic while applying echo in real-time; writes processed WAV.
 *
 * Echo DSP (circular delay buffer):
 *   delayBuf[writePos] = input + feedback × delayBuf[readPos]
 *   output             = dry × input       + wet × delayBuf[readPos]
 *
 * Parameters:  delayMs 50–2000 ms | feedback 0–0.85 | dryMix/wetMix 0–1
 * No NDK, no third-party libs. Android 10-15, minSdk 23.
 */
public class EchoRecordingEngine {

    private static final String TAG        = "EchoRecordingEngine";
    private static final int    SR         = 44100;
    private static final int    CHANNEL    = AudioFormat.CHANNEL_IN_MONO;
    private static final int    ENCODING   = AudioFormat.ENCODING_PCM_16BIT;

    // ── Echo parameters ──────────────────────────────────────────────────────
    private int   delayMs  = 250;
    private float feedback = 0.35f;
    private float dryMix   = 0.70f;
    private float wetMix   = 0.40f;

    // ── State ────────────────────────────────────────────────────────────────
    private final Context       context;
    private AudioRecord         audioRecord;
    private File                outputFile;
    private Thread              recordThread;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private AudioDeviceInfo     preferredDevice;

    // ── Callbacks ────────────────────────────────────────────────────────────
    public interface AmplitudeCallback { void onAmplitude(float normalized); }
    public interface DoneCallback      { void onDone(File file, String error); }
    private AmplitudeCallback onAmplitude;
    private DoneCallback      onDone;

    public EchoRecordingEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setDelayMs(int ms)                    { delayMs  = Math.max(50,  Math.min(2000, ms)); }
    public void setFeedback(float fb)                 { feedback = Math.max(0f,  Math.min(0.85f, fb)); }
    public void setDryMix(float d)                    { dryMix   = Math.max(0f,  Math.min(1f, d)); }
    public void setWetMix(float w)                    { wetMix   = Math.max(0f,  Math.min(1f, w)); }
    public void setPreferredDevice(AudioDeviceInfo d) { preferredDevice = d; }
    public void setOnAmplitude(AmplitudeCallback cb)  { onAmplitude = cb; }
    public void setOnDone(DoneCallback cb)             { onDone = cb; }
    public int   getDelayMs()    { return delayMs; }
    public float getFeedback()   { return feedback; }
    public float getDryMix()     { return dryMix; }
    public float getWetMix()     { return wetMix; }
    public boolean isRecording() { return recording.get(); }

    // ── Start / Stop ──────────────────────────────────────────────────────────
    public void startRecording(File dir, String fileName) {
        if (recording.get()) return;
        dir.mkdirs();
        outputFile = new File(dir, fileName + "_echo.wav");
        recording.set(true);

        int minBuf  = AudioRecord.getMinBufferSize(SR, CHANNEL, ENCODING);
        int bufSize = Math.max(minBuf, 4096);
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SR, CHANNEL, ENCODING, bufSize);
            if (Build.VERSION.SDK_INT >= 23) {
                AudioDeviceInfo dev = (preferredDevice != null) ? preferredDevice
                        : AudioRecorderHelper.getUsbInputDevice(context);
                if (dev != null) audioRecord.setPreferredDevice(dev);
            }
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("AudioRecord init failed");
            final int finalBuf = bufSize;
            recordThread = new Thread(() -> recordLoop(finalBuf));
            recordThread.start();
        } catch (Exception e) {
            recording.set(false);
            Log.e(TAG, "start failed", e);
            if (onDone != null) onDone.onDone(null, e.getMessage());
        }
    }

    public void stopRecording() { recording.set(false); }

    // ── Record loop ───────────────────────────────────────────────────────────
    private void recordLoop(int bufSize) {
        int     delaySamples = (int)((delayMs / 1000f) * SR);
        float[] delayBuf     = new float[Math.max(1, delaySamples)];
        int     writePos     = 0;
        short[] readBuf      = new short[bufSize / 2];
        long    totalBytes   = 0;

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            AudioTrimmer.writeWavHeader(fos, SR, 1, 16, 0);
            audioRecord.startRecording();

            while (recording.get()) {
                int n = audioRecord.read(readBuf, 0, readBuf.length);
                if (n <= 0) continue;

                short[] outBuf = new short[n];
                float   peak   = 0f;
                for (int i = 0; i < n; i++) {
                    float in     = readBuf[i] / 32768f;
                    int   readPos = (writePos - delaySamples + delayBuf.length) % delayBuf.length;
                    float echoed  = delayBuf[readPos];
                    delayBuf[writePos] = in + feedback * echoed;
                    float out    = dryMix * in + wetMix * echoed;
                    writePos     = (writePos + 1) % delayBuf.length;
                    out          = softClip(out);
                    outBuf[i]    = (short) Math.max(-32768, Math.min(32767, (int)(out * 32767f)));
                    float a = Math.abs(out); if (a > peak) peak = a;
                }
                byte[] bytes = shortsToBytes(outBuf, n);
                fos.write(bytes, 0, n * 2);
                totalBytes += n * 2L;
                AmplitudeCallback cb = onAmplitude;
                if (cb != null) { final float p = peak; cb.onAmplitude(p); }
            }

            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            patchWavHeader(outputFile, (int)(totalBytes / 2));
            DoneCallback cb = onDone;
            if (cb != null) cb.onDone(outputFile, null);
        } catch (Exception e) {
            Log.e(TAG, "recordLoop error", e);
            DoneCallback cb = onDone;
            if (cb != null) cb.onDone(null, e.getMessage());
        }
    }

    private float softClip(float x) {
        if (x > 1f) return (float) Math.tanh(x);
        if (x < -1f) return (float)-Math.tanh(-x);
        return x;
    }

    private byte[] shortsToBytes(short[] s, int count) {
        byte[] b = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            b[i*2]   = (byte)(s[i] & 0xFF);
            b[i*2+1] = (byte)((s[i] >> 8) & 0xFF);
        }
        return b;
    }

    private void patchWavHeader(File f, int numSamples) throws IOException {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw")) {
            int dataSize = numSamples * 2;
            raf.seek(4);  writeLEInt(raf, 36 + dataSize);
            raf.seek(40); writeLEInt(raf, dataSize);
        }
    }

    private static void writeLEInt(java.io.RandomAccessFile r, int v) throws IOException {
        r.write(v & 0xFF); r.write((v>>8)&0xFF); r.write((v>>16)&0xFF); r.write((v>>24)&0xFF);
    }
}
