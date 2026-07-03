package com.pramod.loopmidi;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

/**
 * AudioEngine — supports any-quality WAV files:
 *   Bit depth  : 8-bit unsigned, 16-bit signed, 24-bit signed, 32-bit IEEE float
 *   Sample rate: any (8000-192000 Hz); linear resampling to 44100 Hz
 *   Channels   : mono or stereo (mixed down to mono)
 */
public class AudioEngine {
    private static final int    PAD_COUNT = 16;
    private static final int    TARGET_SR = 44100;
    private static final String TAG       = "AudioEngine";

    private Context context;
    private long    nativeHandle;

    public static class SampleData {
        public Uri    uri     = null;
        public int    soundId = 0;
        public boolean loaded = false;
    }

    private native long nativeCreateAudioEngine();
    private native void nativeDestroyAudioEngine();
    private native void nativeLoadSample(int padIdx, short[] pcm, int len);
    private native void nativePlaySample(int padIdx, float volume, float pitch,
            boolean delayOn, float delayMs, float delayLevel,
            float eqLow, float eqMid, float eqHigh,
            int chokeGroup, float attackMs, float releaseMs);
    private native void nativeStopAll();
    private native void nativeStopPad(int padIdx);

    static {
        try {
            System.loadLibrary("oboe_audio_engine");
            Log.i(TAG, "Oboe audio engine library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load Oboe audio engine library", e);
        }
    }

    public AudioEngine(Context ctx) {
        this.nativeHandle = 0L;
        this.context      = ctx;
        long handle       = nativeCreateAudioEngine();
        this.nativeHandle = handle;
        if (handle != 0) Log.i(TAG, "Audio engine initialised (Oboe)");
        else             Log.e(TAG, "Failed to initialise audio engine");
    }

    public void start() {}

    public void stop() {
        if (nativeHandle != 0) { nativeDestroyAudioEngine(); nativeHandle = 0L; }
    }

    public SampleData loadWavFromUri(int padIndex, Uri uri) throws IOException {
        if (!checkPad(padIndex)) return null;
        try {
            AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) return null;
            byte[] wav = readAfd(afd);
            afd.close();
            return loadDecoded(padIndex, wav, uri, 0);
        } catch (Exception e) { Log.e(TAG, "loadWavFromUri error", e); return null; }
    }

    public SampleData loadRawSound(int padIndex, int resId)
            throws Resources.NotFoundException, IOException {
        if (!checkPad(padIndex)) return null;
        try {
            InputStream is = context.getResources().openRawResource(resId);
            byte[] wav     = readStream(is);
            is.close();
            return loadDecoded(padIndex, wav, null, resId);
        } catch (Exception e) { Log.e(TAG, "loadRawSound error", e); return null; }
    }

    public SampleData loadWavFromAsset(int padIndex, String assetPath) throws IOException {
        if (!checkPad(padIndex)) return null;
        try {
            InputStream is = context.getAssets().open(assetPath);
            byte[] wav     = readStream(is);
            is.close();
            return loadDecoded(padIndex, wav, null, padIndex);
        } catch (Exception e) { Log.e(TAG, "loadWavFromAsset error", e); return null; }
    }

    public void unloadSample(SampleData s) {
        if (s != null) { s.soundId = 0; s.loaded = false; s.uri = null; }
    }

    public void preloadSample(SampleData s) {}

    public void playSample(int padIndex, SampleData sample, float volume, float pitch,
            int loopMode, boolean delayOn, float delayMs, float delayLevel,
            float eqLow, float eqMid, float eqHigh, int chokeGroup,
            float attackMs, float releaseMs) {
        try {
            if (nativeHandle != 0 && sample != null && sample.loaded) {
                float vol  = Math.max(0f, Math.min(1f, volume));
                float rate = Math.max(0.25f, Math.min(4f, pitch));
                nativePlaySample(padIndex, vol, rate, delayOn, delayMs, delayLevel,
                        eqLow, eqMid, eqHigh, chokeGroup, attackMs, releaseMs);
            }
        } catch (Exception e) { Log.e(TAG, "playSample error", e); }
    }

    public void playSample(int padIndex, SampleData sample, float volume, float pitch, int loopMode) {
        playSample(padIndex, sample, volume, pitch, loopMode,
                false, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0f);
    }

    public void stopPad(int padIndex)  { if (nativeHandle != 0) nativeStopPad(padIndex); }
    public void stopAll()              { if (nativeHandle != 0) nativeStopAll(); }

    private boolean checkPad(int padIndex) {
        if (nativeHandle == 0) return false;
        if (padIndex < 0 || padIndex >= PAD_COUNT) {
            Log.e(TAG, "Invalid pad index: " + padIndex); return false;
        }
        return true;
    }

    private SampleData loadDecoded(int padIndex, byte[] wav, Uri uri, int soundId) {
        short[] pcm = decodeWav(wav);
        if (pcm == null || pcm.length == 0) {
            Log.e(TAG, "Failed to decode WAV for pad " + padIndex); return null;
        }
        SampleData sd = new SampleData();
        sd.uri     = uri;
        sd.soundId = soundId;
        sd.loaded  = true;
        nativeLoadSample(padIndex, pcm, pcm.length);
        Log.i(TAG, "Loaded pad " + padIndex + ": " + pcm.length + " frames @ " + TARGET_SR + " Hz");
        return sd;
    }

    /**
     * Decodes ANY WAV to mono 16-bit PCM at 44100 Hz.
     * Bit depths: 8-bit unsigned, 16-bit signed, 24-bit signed, 32-bit float
     * Sample rates: any — linear resampled to TARGET_SR
     * Channels: mono or stereo (averaged to mono)
     */
    private short[] decodeWav(byte[] wav) {
        if (wav == null || wav.length < 44) { Log.e(TAG, "WAV too short"); return null; }
        if (wav[0]!='R'||wav[1]!='I'||wav[2]!='F'||wav[3]!='F') { Log.e(TAG,"Not RIFF"); return null; }
        if (wav[8]!='W'||wav[9]!='A'||wav[10]!='V'||wav[11]!='E') { Log.e(TAG,"Not WAVE"); return null; }

        int offset = 12;
        int audioFormat  = 1;
        int channels     = 1;
        int sampleRate   = 44100;
        int bitsPerSample= 16;
        int dataOffset   = -1;
        int dataSize     = -1;

        while (offset + 8 <= wav.length) {
            int chunkSize = le32(wav, offset + 4);
            char c0=(char)wav[offset], c1=(char)wav[offset+1],
                 c2=(char)wav[offset+2], c3=(char)wav[offset+3];

            if (c0=='f'&&c1=='m'&&c2=='t'&&c3==' ') {
                if (offset + 24 <= wav.length) {
                    audioFormat   = le16(wav, offset + 8);
                    channels      = le16(wav, offset + 10);
                    sampleRate    = le32(wav, offset + 12);
                    bitsPerSample = le16(wav, offset + 22);
                }
            } else if (c0=='d'&&c1=='a'&&c2=='t'&&c3=='a') {
                dataOffset = offset + 8;
                dataSize   = chunkSize;
                break;
            }
            offset += 8 + chunkSize + (chunkSize & 1);
        }

        if (dataOffset < 0 || dataSize <= 0 || dataOffset + dataSize > wav.length) {
            Log.e(TAG, "No valid data chunk"); return null;
        }
        if (channels < 1) channels = 1;

        Log.i(TAG, "WAV: fmt=" + audioFormat + " ch=" + channels
                + " sr=" + sampleRate + " bits=" + bitsPerSample
                + " dataBytes=" + dataSize);

        int bytesPerSample = Math.max(1, bitsPerSample / 8);
        int bytesPerFrame  = bytesPerSample * channels;
        int frameCount     = dataSize / bytesPerFrame;
        float[] mono       = new float[frameCount];

        for (int i = 0; i < frameCount; i++) {
            int p = dataOffset + i * bytesPerFrame;
            float sum = 0f;
            for (int ch = 0; ch < channels; ch++) {
                int s = p + ch * bytesPerSample;
                if (s + bytesPerSample > wav.length) break;
                float samp;
                if (audioFormat == 3 && bitsPerSample == 32) {
                    // 32-bit IEEE float
                    samp = Float.intBitsToFloat(le32(wav, s));
                } else if (bitsPerSample == 8) {
                    // 8-bit unsigned (0-255 -> -1..+1)
                    samp = ((wav[s] & 0xFF) - 128) / 128.0f;
                } else if (bitsPerSample == 16) {
                    // 16-bit signed
                    samp = (short)(le16(wav, s)) / 32768.0f;
                } else if (bitsPerSample == 24) {
                    // 24-bit signed little-endian
                    int raw = (wav[s] & 0xFF)
                            | ((wav[s+1] & 0xFF) << 8)
                            | (wav[s+2] << 16);
                    samp = raw / 8388608.0f;
                } else if (bitsPerSample == 32 && audioFormat == 1) {
                    // 32-bit signed PCM
                    samp = le32(wav, s) / 2147483648.0f;
                } else {
                    samp = 0f;
                }
                sum += samp;
            }
            mono[i] = sum / channels;
        }

        if (sampleRate == TARGET_SR) {
            short[] out = new short[frameCount];
            for (int i = 0; i < frameCount; i++) out[i] = clampToShort(mono[i]);
            return out;
        }

        // Linear interpolation resampler
        double ratio  = (double) sampleRate / TARGET_SR;
        int    outLen = (int) Math.ceil(frameCount / ratio);
        short[] out   = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcPos = i * ratio;
            int    s0     = (int) srcPos;
            int    s1     = Math.min(s0 + 1, frameCount - 1);
            float  frac   = (float)(srcPos - s0);
            out[i]        = clampToShort(mono[s0] * (1f - frac) + mono[s1] * frac);
        }
        Log.i(TAG, "Resampled " + sampleRate + "Hz -> " + TARGET_SR + "Hz: "
                + frameCount + " -> " + outLen + " frames");
        return out;
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o+1] & 0xFF) << 8);
    }
    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o+1] & 0xFF) << 8)
             | ((b[o+2] & 0xFF) << 16) | ((b[o+3] & 0xFF) << 24);
    }
    private static short clampToShort(float v) {
        v = Math.max(-1f, Math.min(1f, v));
        return (short)(v * 32767);
    }
    private static byte[] readStream(InputStream is) throws IOException {
        byte[] buf = new byte[Math.max(is.available(), 4096)];
        int total = 0, read;
        while ((read = is.read(buf, total, buf.length - total)) != -1) {
            total += read;
            if (total == buf.length) {
                byte[] bigger = new byte[buf.length * 2];
                System.arraycopy(buf, 0, bigger, 0, total);
                buf = bigger;
            }
        }
        byte[] result = new byte[total];
        System.arraycopy(buf, 0, result, 0, total);
        return result;
    }
    private static byte[] readAfd(AssetFileDescriptor afd) throws Exception {
        InputStream is = afd.createInputStream();
        byte[] data    = readStream(is);
        is.close();
        return data;
    }
}
