package com.pramod.soundstudio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles audio recording via AudioRecord (WAV) or MediaRecorder (compressed).
 * USB OTG audio interfaces are auto-detected and can be explicitly preferred.
 */
public class AudioRecorderHelper {

    private static final String TAG = "AudioRecorder";

    // ── Config ──────────────────────────────────────────────────────────────
    public enum Format { WAV, COMPRESSED }

    private final Context context;
    private int    sampleRate  = 44100;
    private int    bitDepth    = 16;           // 16 or 24
    private int    channels    = 1;            // mono
    private Format format      = Format.WAV;
    private AudioDeviceInfo preferredDevice = null;

    // ── State ────────────────────────────────────────────────────────────────
    private AudioRecord    audioRecord;
    private MediaRecorder  mediaRecorder;
    private File           outputFile;
    private AtomicBoolean  recording = new AtomicBoolean(false);
    private Thread         recordThread;

    // ── Callbacks ────────────────────────────────────────────────────────────
    public interface AmplitudeCallback { void onAmplitude(float normalized); }
    public interface DoneCallback      { void onDone(File file, String error); }

    private AmplitudeCallback onAmplitude;
    private DoneCallback      onDone;

    public AudioRecorderHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── Configuration ────────────────────────────────────────────────────────
    public void setSampleRate(int hz)          { this.sampleRate = hz; }
    public void setBitDepth(int bits)          { this.bitDepth   = bits; }
    public void setFormat(Format fmt)          { this.format     = fmt; }
    public void setPreferredDevice(AudioDeviceInfo d) { this.preferredDevice = d; }
    public void setOnAmplitude(AmplitudeCallback cb)  { this.onAmplitude = cb; }
    public void setOnDone(DoneCallback cb)             { this.onDone = cb; }

    // ── USB device detection ─────────────────────────────────────────────────

    /** List all available audio input devices (built-in + USB). */
    public static List<AudioDeviceInfo> getInputDevices(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        List<AudioDeviceInfo> result = new ArrayList<>();
        if (am == null) return result;
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo d : devices) {
            result.add(d);
        }
        return result;
    }

    /** Get first connected USB audio input device, or null. */
    public static AudioDeviceInfo getUsbInputDevice(Context ctx) {
        for (AudioDeviceInfo d : getInputDevices(ctx)) {
            if (d.getType() == AudioDeviceInfo.TYPE_USB_DEVICE
             || d.getType() == AudioDeviceInfo.TYPE_USB_HEADSET
             || d.getType() == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                return d;
            }
        }
        return null;
    }

    /** True if any USB audio input is connected. */
    public static boolean isUsbAudioConnected(Context ctx) {
        return getUsbInputDevice(ctx) != null;
    }

    // ── Recording ────────────────────────────────────────────────────────────

    public void startRecording(File recordingsDir, String fileName) {
        if (recording.get()) return;
        recording.set(true);

        if (format == Format.WAV) {
            startWavRecording(recordingsDir, fileName);
        } else {
            startCompressedRecording(recordingsDir, fileName);
        }
    }

    private void startWavRecording(File dir, String name) {
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding);
        int bufSize = Math.max(minBuf, 8192);

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, encoding, bufSize);

            // Prefer USB device if available
            if (Build.VERSION.SDK_INT >= 23 && preferredDevice != null) {
                audioRecord.setPreferredDevice(preferredDevice);
            } else if (Build.VERSION.SDK_INT >= 23) {
                AudioDeviceInfo usb = getUsbInputDevice(context);
                if (usb != null) audioRecord.setPreferredDevice(usb);
            }

            outputFile = new File(dir, name + ".wav");
            dir.mkdirs();

            recordThread = new Thread(() -> writeWavLoop(bufSize));
            recordThread.start();

        } catch (Exception e) {
            recording.set(false);
            Log.e(TAG, "startWavRecording failed", e);
            if (onDone != null) onDone.onDone(null, e.getMessage());
        }
    }

    private void writeWavLoop(int bufSize) {
        byte[] buf = new byte[bufSize];
        long totalBytes = 0;

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            // Write placeholder header first; we'll overwrite it at the end
            AudioTrimmer.writeWavHeader(fos, sampleRate, channels, 16, 0);
            audioRecord.startRecording();

            while (recording.get()) {
                int n = audioRecord.read(buf, 0, bufSize);
                if (n > 0) {
                    fos.write(buf, 0, n);
                    totalBytes += n;

                    // Emit amplitude for waveform (peak of this buffer)
                    if (onAmplitude != null) {
                        short peak = 0;
                        for (int i = 0; i + 1 < n; i += 2) {
                            short s = (short)((buf[i+1] << 8) | (buf[i] & 0xFF));
                            if (Math.abs(s) > Math.abs(peak)) peak = s;
                        }
                        final float amp = Math.abs(peak) / 32767f;
                        // Post on main thread happens in Activity
                        if (onAmplitude != null) onAmplitude.onAmplitude(amp);
                    }
                }
            }

            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            // Patch the WAV header with correct sample count
            int numSamples = (int)(totalBytes / 2); // 16-bit → 2 bytes/sample
            patchWavHeader(outputFile, numSamples, sampleRate, channels, 16);

            if (onDone != null) onDone.onDone(outputFile, null);

        } catch (IOException e) {
            Log.e(TAG, "WAV write loop error", e);
            if (onDone != null) onDone.onDone(null, e.getMessage());
        }
    }

    private void startCompressedRecording(File dir, String name) {
        outputFile = new File(dir, name + ".m4a");
        dir.mkdirs();

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(Math.min(sampleRate, 48000));
            mediaRecorder.setAudioEncodingBitRate(320000);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            // Amplitude poll thread
            recordThread = new Thread(() -> {
                while (recording.get()) {
                    if (onAmplitude != null && mediaRecorder != null) {
                        try {
                            int max = mediaRecorder.getMaxAmplitude();
                            onAmplitude.onAmplitude(max / 32767f);
                        } catch (Exception ignored) {}
                    }
                    try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                }
            });
            recordThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Compressed recording failed", e);
            recording.set(false);
            if (onDone != null) onDone.onDone(null, e.getMessage());
        }
    }

    public void stopRecording() {
        if (!recording.get()) return;
        recording.set(false);

        if (format == Format.COMPRESSED && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Stop compressed", e);
            }
            mediaRecorder = null;
            if (onDone != null) onDone.onDone(outputFile, null);
        }
        // WAV: the loop exits naturally when recording=false
    }

    public boolean isRecording() { return recording.get(); }

    // ── WAV header patch ─────────────────────────────────────────────────────

    private static void patchWavHeader(File f, int numSamples,
                                       int sampleRate, int channels,
                                       int bps) throws IOException {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw")) {
            int dataSize = numSamples * channels * (bps / 8);
            // RIFF chunk size at offset 4
            raf.seek(4);
            writeLEInt(raf, 36 + dataSize);
            // data chunk size at offset 40
            raf.seek(40);
            writeLEInt(raf, dataSize);
        }
    }

    private static void writeLEInt(java.io.RandomAccessFile raf, int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >> 8) & 0xFF);
        raf.write((v >> 16) & 0xFF);
        raf.write((v >> 24) & 0xFF);
    }
}
