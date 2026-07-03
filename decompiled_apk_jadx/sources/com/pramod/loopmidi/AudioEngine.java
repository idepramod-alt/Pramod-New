package com.pramod.loopmidi;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

/**
 * AudioEngine v3:
 *  - Lock-free command queue (no mutex in audio path = zero latency spikes)
 *  - Split voice pools: loops use fixed slots 0-7, drums use round-robin 8-23
 *  - nativePlayLoop()        — start/restart a loop voice (continuous)
 *  - nativeUpdateLoopPitch() — live pitch/volume update without stop/restart gap
 *  - nativePlaySample()      — drum/pad hit (stackable, fast rolling)
 *  - WAV decoder: 8/16/24/32-bit, any sample rate (linear resample to 44100)
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

    // ── Native declarations ──────────────────────────────────────────────────
    private native long nativeCreateAudioEngine();
    private native void nativeDestroyAudioEngine();
    private native void nativeLoadSample(int padIdx, short[] pcm, int len);

    /** Drum/pad hit — stackable, uses drum voice pool (8-23). */
    private native void nativePlaySample(int padIdx, float volume, float pitch,
            boolean delayOn, float delayMs, float delayLevel,
            float eqLow, float eqMid, float eqHigh,
            int chokeGroup, float attackMs, float releaseMs);

    /** Loop playback — uses fixed loop voice slot (0-7). Restarts the loop. */
    private native void nativePlayLoop(int padIdx, float volume, float pitch);

    /** Live pitch+volume update for a running loop — NO gap, NO restart. */
    private native void nativeUpdateLoopPitch(int padIdx, float volume, float pitch);

    private native void nativeStopAll();
    private native void nativeStopPad(int padIdx);

    static {
        try {
            System.loadLibrary("oboe_audio_engine");
            Log.i(TAG, "Oboe audio engine loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load Oboe library", e);
        }
    }

    public AudioEngine(Context ctx) {
        this.nativeHandle = 0L;
        this.context      = ctx;
        long h = nativeCreateAudioEngine();
        this.nativeHandle = h;
        if (h != 0) Log.i(TAG, "Audio engine initialised (low-latency Oboe)");
        else        Log.e(TAG, "Failed to initialise audio engine");
    }

    public void start() {}

    public void stop() {
        if (nativeHandle != 0) { nativeDestroyAudioEngine(); nativeHandle = 0L; }
    }

    // ── Load helpers ─────────────────────────────────────────────────────────

    public SampleData loadWavFromUri(int padIndex, Uri uri) throws IOException {
        if (!checkPad(padIndex)) return null;
        try {
            AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) return null;
            byte[] wav = readAfd(afd); afd.close();
            return finishLoad(padIndex, wav, uri, 0);
        } catch (Exception e) { Log.e(TAG, "loadWavFromUri", e); return null; }
    }

    public SampleData loadRawSound(int padIndex, int resId)
            throws Resources.NotFoundException, IOException {
        if (!checkPad(padIndex)) return null;
        try {
            InputStream is = context.getResources().openRawResource(resId);
            byte[] wav = readStream(is); is.close();
            return finishLoad(padIndex, wav, null, resId);
        } catch (Exception e) { Log.e(TAG, "loadRawSound", e); return null; }
    }

    public SampleData loadWavFromAsset(int padIndex, String assetPath) throws IOException {
        if (!checkPad(padIndex)) return null;
        try {
            InputStream is = context.getAssets().open(assetPath);
            byte[] wav = readStream(is); is.close();
            return finishLoad(padIndex, wav, null, padIndex);
        } catch (Exception e) { Log.e(TAG, "loadWavFromAsset", e); return null; }
    }

    public void unloadSample(SampleData s) {
        if (s != null) { s.soundId = 0; s.loaded = false; s.uri = null; }
    }

    public void preloadSample(SampleData s) {}

    // ── Play helpers ─────────────────────────────────────────────────────────

    /**
     * Play as a DRUM hit — stackable, uses drum voice pool.
     * Multiple rapid calls = fast rolling with no latency.
     */
    public void playSample(int padIndex, SampleData sample, float volume, float pitch,
            int loopMode, boolean delayOn, float delayMs, float delayLevel,
            float eqLow, float eqMid, float eqHigh, int chokeGroup,
            float attackMs, float releaseMs) {
        if (nativeHandle == 0 || sample == null || !sample.loaded) return;
        float vol  = clampF(volume, 0f, 1f);
        float rate = clampF(pitch,  0.1f, 8f);
        nativePlaySample(padIndex, vol, rate, delayOn, delayMs, delayLevel,
                eqLow, eqMid, eqHigh, chokeGroup, attackMs, releaseMs);
    }

    /** Shorthand for drum hit with no effects. */
    public void playSample(int padIndex, SampleData sample, float volume, float pitch, int loopMode) {
        playSample(padIndex, sample, volume, pitch, loopMode,
                false, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0f);
    }

    /**
     * Start a LOOP — uses fixed voice slot for this pad.
     * Call this when the pad is first activated.
     */
    public void playLoop(int padIndex, SampleData sample, float volume, float pitch) {
        if (nativeHandle == 0 || sample == null || !sample.loaded) return;
        nativePlayLoop(padIndex, clampF(volume, 0f, 1f), clampF(pitch, 0.1f, 8f));
    }

    /**
     * Update pitch/volume of a running loop WITHOUT stopping it.
     * Zero gap — use this when BPM/pitch seekbar changes.
     */
    public void updateLoopPitch(int padIndex, float volume, float pitch) {
        if (nativeHandle == 0) return;
        nativeUpdateLoopPitch(padIndex, clampF(volume, 0f, 1f), clampF(pitch, 0.1f, 8f));
    }

    public void stopPad(int padIndex)  { if (nativeHandle != 0) nativeStopPad(padIndex); }
    public void stopAll()              { if (nativeHandle != 0) nativeStopAll(); }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private boolean checkPad(int idx) {
        if (nativeHandle == 0) return false;
        if (idx < 0 || idx >= PAD_COUNT) { Log.e(TAG, "Bad pad index: " + idx); return false; }
        return true;
    }

    private SampleData finishLoad(int padIndex, byte[] wav, Uri uri, int soundId) {
        short[] pcm = decodeWav(wav);
        if (pcm == null || pcm.length == 0) { Log.e(TAG, "WAV decode failed pad " + padIndex); return null; }
        SampleData sd = new SampleData();
        sd.uri = uri; sd.soundId = soundId; sd.loaded = true;
        nativeLoadSample(padIndex, pcm, pcm.length);
        Log.i(TAG, "Pad " + padIndex + " loaded: " + pcm.length + " frames @ " + TARGET_SR + " Hz");
        return sd;
    }

    /**
     * Universal WAV decoder → mono 16-bit PCM @ 44100 Hz.
     * Supports: 8-bit unsigned, 16-bit signed, 24-bit signed, 32-bit IEEE float.
     * Channels: mono or stereo (averaged to mono).
     * Sample rate: any — linear resampled to 44100 Hz.
     */
    private short[] decodeWav(byte[] wav) {
        if (wav == null || wav.length < 44) { Log.e(TAG, "WAV too short"); return null; }
        if (wav[0]!='R'||wav[1]!='I'||wav[2]!='F'||wav[3]!='F') { Log.e(TAG,"Not RIFF"); return null; }
        if (wav[8]!='W'||wav[9]!='A'||wav[10]!='V'||wav[11]!='E') { Log.e(TAG,"Not WAVE"); return null; }

        int offset=12, audioFormat=1, channels=1, sampleRate=44100, bitsPerSample=16;
        int dataOffset=-1, dataSize=-1;

        while (offset + 8 <= wav.length) {
            int sz = le32(wav, offset + 4);
            char a=(char)wav[offset],b=(char)wav[offset+1],c2=(char)wav[offset+2],d=(char)wav[offset+3];
            if (a=='f'&&b=='m'&&c2=='t'&&d==' ' && offset+24<=wav.length) {
                audioFormat   = le16(wav, offset+8);
                channels      = le16(wav, offset+10);
                sampleRate    = le32(wav, offset+12);
                bitsPerSample = le16(wav, offset+22);
            } else if (a=='d'&&b=='a'&&c2=='t'&&d=='a') {
                dataOffset = offset+8; dataSize = sz; break;
            }
            offset += 8 + sz + (sz & 1);
        }
        if (dataOffset<0||dataSize<=0||dataOffset+dataSize>wav.length) { Log.e(TAG,"No data chunk"); return null; }
        if (channels < 1) channels = 1;
        Log.i(TAG,"WAV fmt="+audioFormat+" ch="+channels+" sr="+sampleRate+" bits="+bitsPerSample);

        int bps = Math.max(1, bitsPerSample/8);
        int frameCount = dataSize / (bps * channels);
        float[] mono = new float[frameCount];

        for (int i = 0; i < frameCount; i++) {
            int p = dataOffset + i * bps * channels;
            float sum = 0f;
            for (int ch = 0; ch < channels; ch++) {
                int s = p + ch * bps;
                if (s + bps > wav.length) break;
                float samp;
                if      (audioFormat==3 && bitsPerSample==32) samp = Float.intBitsToFloat(le32(wav,s));
                else if (bitsPerSample==8)  samp = ((wav[s]&0xFF)-128)/128.0f;
                else if (bitsPerSample==16) samp = (short)le16(wav,s)/32768.0f;
                else if (bitsPerSample==24) samp = ((wav[s]&0xFF)|((wav[s+1]&0xFF)<<8)|(wav[s+2]<<16))/8388608.0f;
                else if (bitsPerSample==32) samp = le32(wav,s)/2147483648.0f;
                else samp = 0f;
                sum += samp;
            }
            mono[i] = sum / channels;
        }

        if (sampleRate == TARGET_SR) {
            short[] out = new short[frameCount];
            for (int i=0;i<frameCount;i++) out[i]=clampShort(mono[i]);
            return out;
        }
        // Linear interpolation resample
        double ratio = (double)sampleRate / TARGET_SR;
        int outLen = (int)Math.ceil(frameCount / ratio);
        short[] out = new short[outLen];
        for (int i=0;i<outLen;i++) {
            double sp = i*ratio;
            int s0=(int)sp, s1=Math.min(s0+1,frameCount-1);
            out[i] = clampShort(mono[s0]*(1f-(float)(sp-s0)) + mono[s1]*(float)(sp-s0));
        }
        Log.i(TAG,"Resampled "+sampleRate+"->"+TARGET_SR+" Hz: "+frameCount+"->"+outLen+" frames");
        return out;
    }

    private static int le16(byte[] b,int o){ return (b[o]&0xFF)|((b[o+1]&0xFF)<<8); }
    private static int le32(byte[] b,int o){ return (b[o]&0xFF)|((b[o+1]&0xFF)<<8)|((b[o+2]&0xFF)<<16)|((b[o+3]&0xFF)<<24); }
    private static short clampShort(float v){ v=Math.max(-1f,Math.min(1f,v)); return (short)(v*32767); }
    private static float clampF(float v,float lo,float hi){ return Math.max(lo,Math.min(hi,v)); }

    private static byte[] readStream(InputStream is) throws IOException {
        byte[] buf = new byte[Math.max(is.available(),4096)];
        int total=0,read;
        while((read=is.read(buf,total,buf.length-total))!=-1){
            total+=read;
            if(total==buf.length){byte[] b2=new byte[buf.length*2];System.arraycopy(buf,0,b2,0,total);buf=b2;}
        }
        byte[] r=new byte[total]; System.arraycopy(buf,0,r,0,total); return r;
    }
    private static byte[] readAfd(AssetFileDescriptor afd) throws Exception {
        InputStream is=afd.createInputStream(); byte[] d=readStream(is); is.close(); return d;
    }
}
