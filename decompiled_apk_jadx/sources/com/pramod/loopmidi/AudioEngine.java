package com.pramod.loopmidi;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
/* loaded from: classes3.dex */
public class AudioEngine {
    private static final int PAD_COUNT = 16;
    private static final String TAG = "AudioEngine";
    private Context context;
    private long nativeHandle;
    private byte[] waveCache;

    /* loaded from: classes3.dex */
    public static class SampleData {
        public Uri uri;
        public int soundId = 0;
        public boolean loaded = false;
    }

    private native long nativeCreateAudioEngine();

    private native void nativeDestroyAudioEngine();

    private native void nativeLoadSample(int i, short[] sArr, int i2);

    private native void nativePlaySample(int i, float f, float f2, boolean z, float f3, float f4, float f5, float f6, float f7, int i2, float f8, float f9);

    private native void nativeStopAll();

    private native void nativeStopPad(int i);

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
        this.context = ctx;
        long jNativeCreateAudioEngine = nativeCreateAudioEngine();
        this.nativeHandle = jNativeCreateAudioEngine;
        if (jNativeCreateAudioEngine != 0) {
            Log.i(TAG, "Audio engine initialized with native Oboe");
        } else {
            Log.e(TAG, "Failed to initialize audio engine");
        }
    }

    public void start() {
    }

    public void stop() {
        if (this.nativeHandle != 0) {
            nativeDestroyAudioEngine();
            this.nativeHandle = 0L;
        }
    }

    public SampleData loadWavFromUri(int padIndex, Uri uri) throws IOException {
        try {
            if (this.nativeHandle == 0) {
                return null;
            }
            if (padIndex < 0 || padIndex >= 16) {
                Log.e(TAG, "Invalid pad index: " + padIndex);
                return null;
            }
            AssetFileDescriptor afd = this.context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) {
                return null;
            }
            byte[] wavData = readAssetFileDescriptor(afd);
            afd.close();
            short[] pcmData = decodePcmFromWav(wavData);
            if (pcmData == null || pcmData.length == 0) {
                Log.e(TAG, "Failed to decode PCM from WAV");
                return null;
            }
            SampleData sd = new SampleData();
            sd.uri = uri;
            sd.soundId = padIndex;
            sd.loaded = true;
            nativeLoadSample(padIndex, pcmData, pcmData.length);
            Log.i(TAG, "Loaded WAV sample to pad " + padIndex + ": " + pcmData.length + " frames");
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading WAV from URI", e);
            return null;
        }
    }

    public SampleData loadRawSound(int padIndex, int resId) throws Resources.NotFoundException, IOException {
        try {
            if (this.nativeHandle == 0) {
                return null;
            }
            if (padIndex < 0 || padIndex >= 16) {
                Log.e(TAG, "Invalid pad index: " + padIndex);
                return null;
            }
            InputStream is = this.context.getResources().openRawResource(resId);
            byte[] wavData = new byte[is.available()];
            is.read(wavData);
            is.close();
            short[] pcmData = decodePcmFromWav(wavData);
            if (pcmData == null || pcmData.length == 0) {
                Log.e(TAG, "Failed to decode PCM from raw resource");
                return null;
            }
            SampleData sd = new SampleData();
            sd.soundId = resId;
            sd.loaded = true;
            nativeLoadSample(padIndex, pcmData, pcmData.length);
            Log.i(TAG, "Loaded raw sound to pad " + padIndex + ": " + pcmData.length + " frames");
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading raw sound", e);
            return null;
        }
    }

    public SampleData loadWavFromAsset(int padIndex, String assetPath) throws IOException {
        if (this.nativeHandle == 0) {
            return null;
        }
        if (padIndex < 0 || padIndex >= 16) {
            Log.e(TAG, "Invalid pad index: " + padIndex);
            return null;
        }
        try {
            InputStream is = this.context.getAssets().open(assetPath);
            byte[] wavData = new byte[is.available()];
            is.read(wavData);
            is.close();
            short[] pcmData = decodePcmFromWav(wavData);
            if (pcmData == null || pcmData.length == 0) {
                Log.e(TAG, "Failed to decode PCM from asset");
                return null;
            }
            SampleData sd = new SampleData();
            sd.soundId = padIndex;
            sd.loaded = true;
            nativeLoadSample(padIndex, pcmData, pcmData.length);
            Log.i(TAG, "Loaded asset sample to pad " + padIndex + ": " + pcmData.length + " frames");
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading WAV asset", e);
            return null;
        }
    }

    public void unloadSample(SampleData sample) {
        if (sample != null) {
            sample.soundId = 0;
            sample.loaded = false;
            sample.uri = null;
        }
    }

    public void preloadSample(SampleData sample) {
    }

    public void playSample(int padIndex, SampleData sample, float volume, float pitch, int loopMode, boolean delayOn, float delayMs, float delayLevel, float eqLow, float eqMid, float eqHigh, int chokeGroup, float attackMs, float releaseMs) {
        try {
            if (this.nativeHandle != 0 && sample != null && sample.loaded) {
                float vol = Math.max(0.0f, Math.min(1.0f, volume));
                float rate = Math.max(0.5f, Math.min(2.0f, pitch));
                nativePlaySample(padIndex, vol, rate, delayOn, delayMs, delayLevel, eqLow, eqMid, eqHigh, chokeGroup, attackMs, releaseMs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sample", e);
        }
    }

    public void playSample(int padIndex, SampleData sample, float volume, float pitch, int loopMode) {
        playSample(padIndex, sample, volume, pitch, loopMode, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f);
    }

    public void stopPad(int padIndex) {
        if (this.nativeHandle != 0) {
            nativeStopPad(padIndex);
        }
    }

    public void stopAll() {
        if (this.nativeHandle != 0) {
            nativeStopAll();
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:34:0x00bb, code lost:
        android.util.Log.e(com.pramod.loopmidi.AudioEngine.TAG, "Invalid WAV fmt chunk size");
     */
    /* JADX WARN: Code restructure failed: missing block: B:35:0x00c0, code lost:
        return null;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private short[] decodePcmFromWav(byte[] r18) {
        /*
            Method dump skipped, instructions count: 394
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.pramod.loopmidi.AudioEngine.decodePcmFromWav(byte[]):short[]");
    }

    private byte[] readAssetFileDescriptor(AssetFileDescriptor afd) throws Exception {
        byte[] data = new byte[(int) afd.getLength()];
        InputStream is = afd.createInputStream();
        is.read(data);
        is.close();
        return data;
    }
}
