package com.pramod.loopmidi;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * AudioEngine — bridges Java loading/playback to the native Oboe engine.
 *
 * LOW-LATENCY SETUP:
 *   The engine queries the device's native sample rate and burst size from
 *   AudioManager and passes them to the native layer. Matching the hardware's
 *   actual parameters avoids Android's internal resampler, eliminating the
 *   20-40 ms of latency it adds on mismatched streams.
 *
 * Supported input formats (auto-detected by file header):
 *   WAV  — PCM 8-bit unsigned, 16-bit signed, 24-bit signed, 32-bit int or float
 *   MP3, AAC, OGG, FLAC — via MediaCodec
 *
 * All formats are decoded → 16-bit PCM mono at the device's native sample rate
 * before being passed to nativeLoadSample(), so no double-resampling occurs.
 */
public class AudioEngine {

    private static final int    PAD_COUNT  = 16;
    private static final String TAG        = "AudioEngine";

    // Device-native sample rate — set from AudioManager in constructor.
    // Decode all audio to this rate so Oboe never has to resample internally.
    private int targetSampleRate = 48000;

    private final Context context;
    private long  nativeHandle    = 0L;
    private boolean nativeAvailable = false;

    // ── JNI declarations ──────────────────────────────────────────────────────
    // nativeSR/nativeBurst: pass AudioManager values so C++ can configure
    // Oboe with the device's exact hardware parameters (zero-resampling path).
    private native long nativeCreateAudioEngine(int nativeSR, int nativeBurst);
    private native void nativeDestroyAudioEngine();
    private native void nativeLoadSample(int padIndex, short[] pcm, int length);
    private native void nativePlaySample(int padIndex, float volume, float pitch,
                                         boolean delayOn, float delayMs, float delayLevel,
                                         float eqLow, float eqMid, float eqHigh,
                                         int chokeGroup, float attackMs, float releaseMs);
    // speed = time-stretch factor (1.0=normal, 2.0=2x faster, pitch unchanged)
    // pitch = pitch-shift factor  (1.0=normal, 2.0=octave up, speed unchanged)
    private native void nativePlayLoop(int padIndex, float volume, float speed, float pitch);
    private native void nativeUpdateLoopSpeedPitch(int padIndex, float volume, float speed, float pitch);
    private native void nativeStopAll();
    private native void nativeStopPad(int padIndex);

    // Whether each optional JNI symbol is actually present in the .so
    private boolean hasNativePlayLoop             = false;
    private boolean hasNativeUpdateLoopSpeedPitch = false;

    static {
        try {
            System.loadLibrary("oboe_audio_engine");
            Log.i(TAG, "Oboe audio engine library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load Oboe audio engine library", e);
        }
    }

    // ── SampleData ─────────────────────────────────────────────────────────────
    public static class SampleData {
        public Uri    uri     = null;
        public int    soundId = 0;
        public boolean loaded = false;
    }

    // ── Constructor ────────────────────────────────────────────────────────────
    public AudioEngine(Context ctx) {
        this.context = ctx;

        // ── Query device-native audio parameters ──────────────────────────────
        // These come from the hardware HAL and tell us the exact SR and buffer
        // size that the audio subsystem prefers. Passing these to Oboe means the
        // stream runs without any internal resampling, giving the minimum possible
        // latency (typically <10 ms on AAudio / Android 8+).
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int nativeSR    = 48000; // safe default — most devices since ~2015
        int nativeBurst = 256;   // safe default

        if (am != null) {
            try {
                String srStr    = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                String burstStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                if (srStr    != null && !srStr.isEmpty())    nativeSR    = Integer.parseInt(srStr);
                if (burstStr != null && !burstStr.isEmpty()) nativeBurst = Integer.parseInt(burstStr);
                // Clamp to sane values
                if (nativeSR    < 8000  || nativeSR    > 192000) nativeSR    = 48000;
                if (nativeBurst < 32    || nativeBurst > 8192)   nativeBurst = 256;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse AudioManager properties", e);
            }
        }
        targetSampleRate = nativeSR;
        Log.i(TAG, "Device audio: nativeSR=" + nativeSR + " nativeBurst=" + nativeBurst);

        // ── Start native Oboe engine ──────────────────────────────────────────
        try {
            long handle = nativeCreateAudioEngine(nativeSR, nativeBurst);
            this.nativeHandle = handle;
            if (handle != 0) {
                this.nativeAvailable = true;
                Log.i(TAG, "Audio engine initialized (Oboe, native path)");
            } else {
                Log.e(TAG, "nativeCreateAudioEngine returned 0");
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native engine not available", e);
        }

        // Probe optional JNI symbols — pads not loaded yet so CMD_PLAY is ignored
        if (nativeAvailable) {
            try { nativePlayLoop(0, 0f, 1f, 1f); hasNativePlayLoop = true; }
            catch (UnsatisfiedLinkError e) { Log.w(TAG, "nativePlayLoop not in .so"); }
            catch (Exception ignored)      { hasNativePlayLoop = true; }

            try { nativeUpdateLoopSpeedPitch(0, 0f, 1f, 1f); hasNativeUpdateLoopSpeedPitch = true; }
            catch (UnsatisfiedLinkError e) { Log.w(TAG, "nativeUpdateLoopSpeedPitch not in .so"); }
            catch (Exception ignored)      { hasNativeUpdateLoopSpeedPitch = true; }
        }
    }

    /** Returns the sample rate this engine is running at (device native SR). */
    public int getSampleRate() { return targetSampleRate; }

    public void start() {}

    public void stop() {
        if (nativeAvailable && nativeHandle != 0) {
            try { nativeDestroyAudioEngine(); }
            catch (UnsatisfiedLinkError e) { Log.e(TAG, "destroy failed", e); }
            nativeHandle    = 0L;
            nativeAvailable = false;
        }
    }

    // ── Public load methods ───────────────────────────────────────────────────
    // All decode to 16-bit PCM mono at targetSampleRate (= device native SR).
    // This ensures samples are already at the correct rate so Oboe's output
    // stream never needs to resample → zero extra latency.

    /** Load from a user-selected URI (file picker). Supports any audio format. */
    public SampleData loadWavFromUri(int padIndex, Uri uri) throws IOException {
        try {
            if (!nativeAvailable || !validPad(padIndex)) return null;
            AssetFileDescriptor afd = context.getContentResolver()
                    .openAssetFileDescriptor(uri, "r");
            if (afd == null) return null;
            byte[] raw = readAssetFileDescriptor(afd);
            afd.close();

            short[] pcm = decodeAudioToPcm(raw);
            if (pcm == null || pcm.length == 0) {
                Log.e(TAG, "loadWavFromUri: decode failed " + uri);
                return null;
            }
            nativeLoadSample(padIndex, pcm, pcm.length);
            Log.i(TAG, "loadWavFromUri pad=" + padIndex + " frames=" + pcm.length);
            SampleData sd = new SampleData();
            sd.uri = uri; sd.soundId = padIndex; sd.loaded = true;
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading from URI", e);
            return null;
        }
    }

    /** Load from res/raw resource. Supports any audio format. */
    public SampleData loadRawSound(int padIndex, int resId)
            throws Resources.NotFoundException, IOException {
        try {
            if (!nativeAvailable || !validPad(padIndex)) return null;
            InputStream is  = context.getResources().openRawResource(resId);
            byte[]      raw = readFully(is);
            is.close();

            short[] pcm = decodeAudioToPcm(raw);
            if (pcm == null || pcm.length == 0) {
                Log.e(TAG, "loadRawSound: decode failed resId=" + resId);
                return null;
            }
            nativeLoadSample(padIndex, pcm, pcm.length);
            Log.i(TAG, "loadRawSound pad=" + padIndex + " frames=" + pcm.length);
            SampleData sd = new SampleData();
            sd.soundId = resId; sd.loaded = true;
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading raw sound", e);
            return null;
        }
    }

    /** Load from assets/ folder. Supports any audio format. */
    public SampleData loadWavFromAsset(int padIndex, String assetPath) throws IOException {
        if (!nativeAvailable || !validPad(padIndex)) return null;
        try {
            InputStream is  = context.getAssets().open(assetPath);
            byte[]      raw = readFully(is);
            is.close();

            short[] pcm = decodeAudioToPcm(raw);
            if (pcm == null || pcm.length == 0) {
                Log.e(TAG, "loadWavFromAsset: decode failed path=" + assetPath);
                return null;
            }
            nativeLoadSample(padIndex, pcm, pcm.length);
            Log.i(TAG, "loadWavFromAsset pad=" + padIndex + " frames=" + pcm.length);
            SampleData sd = new SampleData();
            sd.soundId = padIndex; sd.loaded = true;
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading asset", e);
            return null;
        }
    }

    public void unloadSample(SampleData sample) {
        if (sample != null) { sample.soundId = 0; sample.loaded = false; sample.uri = null; }
    }

    public void preloadSample(SampleData sample) {}

    // ── Playback ──────────────────────────────────────────────────────────────

    /**
     * Play a sample with independent speed and pitch control.
     *
     * @param speed  Time-stretch multiplier (1.0 = normal, 2.0 = 2× faster, pitch unchanged).
     *               Only meaningful for loop voices (loopMode == 1); drums use pitch for rate.
     * @param pitch  Pitch-shift multiplier (1.0 = normal, 2.0 = one octave up, speed unchanged).
     */
    public void playSample(int padIndex, SampleData sample,
                           float volume, float speed, float pitch, int loopMode,
                           boolean delayOn, float delayMs, float delayLevel,
                           float eqLow, float eqMid, float eqHigh,
                           int chokeGroup, float attackMs, float releaseMs) {
        try {
            if (!nativeAvailable || sample == null || !sample.loaded) return;
            float vol  = Math.max(0f, Math.min(1f, volume));
            float spd  = Math.max(0.1f, Math.min(4f, speed));
            float rate = Math.max(0.1f, Math.min(8f, pitch));
            if (loopMode == 1 && hasNativePlayLoop) {
                nativePlayLoop(padIndex, vol, spd, rate);
            } else {
                nativePlaySample(padIndex, vol, rate,
                        delayOn, delayMs, delayLevel,
                        eqLow, eqMid, eqHigh,
                        chokeGroup, attackMs, releaseMs);
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "JNI symbol missing in playSample", e);
        } catch (Exception e) {
            Log.e(TAG, "Error playing sample", e);
        }
    }

    /** Convenience overload: speed + pitch, no delay/EQ params. */
    public void playSample(int padIndex, SampleData sample,
                           float volume, float speed, float pitch, int loopMode) {
        playSample(padIndex, sample, volume, speed, pitch, loopMode,
                false, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0f);
    }

    /** Legacy overload for backward compatibility (no separate speed param). Speed = 1.0. */
    public void playSample(int padIndex, SampleData sample,
                           float volume, float pitch, int loopMode,
                           boolean delayOn, float delayMs, float delayLevel,
                           float eqLow, float eqMid, float eqHigh,
                           int chokeGroup, float attackMs, float releaseMs) {
        playSample(padIndex, sample, volume, 1.0f, pitch, loopMode,
                delayOn, delayMs, delayLevel,
                eqLow, eqMid, eqHigh,
                chokeGroup, attackMs, releaseMs);
    }

    /** Legacy overload: pitch only, speed defaults to 1.0. */
    public void playSample(int padIndex, SampleData sample,
                           float volume, float pitch, int loopMode) {
        playSample(padIndex, sample, volume, 1.0f, pitch, loopMode,
                false, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0f);
    }

    public void stopPad(int padIndex) {
        if (!nativeAvailable) return;
        try { nativeStopPad(padIndex); }
        catch (UnsatisfiedLinkError e) { Log.e(TAG, "nativeStopPad missing", e); }
    }

    public void stopAll() {
        if (!nativeAvailable) return;
        try { nativeStopAll(); }
        catch (UnsatisfiedLinkError e) { Log.e(TAG, "nativeStopAll missing", e); }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UNIVERSAL AUDIO DECODER
    //  Output: 16-bit PCM mono at targetSampleRate (device native SR)
    // ═════════════════════════════════════════════════════════════════════════

    private short[] decodeAudioToPcm(byte[] data) {
        if (data == null || data.length < 4) return null;
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            Log.d(TAG, "Format: WAV → pure-Java decoder");
            return decodePcmFromWav(data);
        }
        Log.d(TAG, "Format: compressed → MediaCodec decoder");
        return decodeWithMediaCodec(data);
    }

    // ─── WAV decoder ─────────────────────────────────────────────────────────

    private short[] decodePcmFromWav(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (buf.remaining() < 12) { Log.e(TAG, "WAV: too short"); return null; }

            int riff = buf.getInt();
            buf.getInt();
            int wave = buf.getInt();
            if (riff != 0x46464952 || wave != 0x45564157) {
                Log.e(TAG, "WAV: bad RIFF/WAVE header"); return null;
            }

            int    audioFormat   = 1;
            int    channels      = 1;
            int    sampleRate    = targetSampleRate;
            int    bitsPerSample = 16;
            byte[] pcmBytes      = null;

            while (buf.remaining() >= 8) {
                int chunkId   = buf.getInt();
                int chunkSize = buf.getInt();
                if (chunkSize < 0) break;

                if (chunkId == 0x20746D66) {  // "fmt "
                    if (chunkSize < 16) { Log.e(TAG, "WAV: fmt too small"); return null; }
                    audioFormat   = buf.getShort() & 0xFFFF;
                    channels      = buf.getShort() & 0xFFFF;
                    sampleRate    = buf.getInt();
                    buf.getInt();
                    buf.getShort();
                    bitsPerSample = buf.getShort() & 0xFFFF;
                    int extra = chunkSize - 16;
                    if (extra > 0) skip(buf, extra);
                } else if (chunkId == 0x61746164) {  // "data"
                    int sz = Math.min(chunkSize, buf.remaining());
                    pcmBytes = new byte[sz];
                    buf.get(pcmBytes);
                    break;
                } else {
                    skip(buf, chunkSize + (chunkSize & 1));
                }
            }

            if (pcmBytes == null) { Log.e(TAG, "WAV: no data chunk"); return null; }

            int bytesPerSample = Math.max(1, (bitsPerSample + 7) / 8);
            int ch             = Math.max(1, channels);
            int frameCount     = pcmBytes.length / (bytesPerSample * ch);
            if (frameCount == 0) return null;

            float[] mono = new float[frameCount];
            ByteBuffer pb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frameCount; i++) {
                double sum = 0;
                for (int c = 0; c < ch; c++) {
                    if (pb.remaining() < bytesPerSample) break;
                    sum += sampleToFloat(pb, bitsPerSample, audioFormat);
                }
                mono[i] = (float)(sum / ch);
            }

            // Resample to device native SR if needed
            float[] resampled = (sampleRate == targetSampleRate)
                    ? mono : linearResample(mono, sampleRate, targetSampleRate);

            short[] out = floatToShort(resampled);
            Log.i(TAG, "WAV decoded: " + channels + "ch " + sampleRate + "Hz "
                    + bitsPerSample + "bit → " + out.length + " frames @ " + targetSampleRate);
            return out;

        } catch (Exception e) {
            Log.e(TAG, "WAV decode exception", e);
            return null;
        }
    }

    private void skip(ByteBuffer b, int n) {
        int s = Math.min(n, b.remaining());
        if (s > 0) b.position(b.position() + s);
    }

    private float sampleToFloat(ByteBuffer b, int bits, int fmt) {
        switch (bits) {
            case 8:  return ((b.get() & 0xFF) - 128) / 128f;
            case 16: return b.getShort() / 32768f;
            case 24: {
                int lo = b.get() & 0xFF, mid = b.get() & 0xFF, hi = b.get();
                return ((hi << 16) | (mid << 8) | lo) / 8388608f;
            }
            case 32: return (fmt == 3) ? b.getFloat() : b.getInt() / 2147483648f;
            default:
                if (b.remaining() >= 2) b.getShort();
                return 0f;
        }
    }

    // ─── MediaCodec decoder ──────────────────────────────────────────────────

    private short[] decodeWithMediaCodec(byte[] data) {
        File            tmpFile   = null;
        MediaExtractor  extractor = null;
        MediaCodec      codec     = null;
        try {
            tmpFile = File.createTempFile("ac_", ".tmp", context.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) { fos.write(data); }

            extractor = new MediaExtractor();
            extractor.setDataSource(tmpFile.getAbsolutePath());

            int         trackIdx    = -1;
            MediaFormat trackFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt  = extractor.getTrackFormat(i);
                String      mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIdx    = i;
                    trackFormat = fmt;
                    break;
                }
            }
            if (trackIdx < 0 || trackFormat == null) {
                Log.e(TAG, "MediaCodec: no audio track"); return null;
            }

            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            int[] meta = extractMeta(trackFormat);

            extractor.selectTrack(trackIdx);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(trackFormat, null, null, 0);
            codec.start();

            int   cap     = targetSampleRate * 30;
            float[] accum = new float[cap];
            int   count   = 0;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean sawInputEOS  = false;
            boolean sawOutputEOS = false;
            final long TIMEOUT_US = 10_000L;

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inIdx = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inIdx >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(inIdx);
                        ib.clear();
                        int nRead = extractor.readSampleData(ib, 0);
                        if (nRead < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            codec.queueInputBuffer(inIdx, 0, nRead, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    meta = extractMeta(codec.getOutputFormat());
                } else if (outIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // deprecated — no-op
                } else if (outIdx >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(outIdx);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        count = appendFrames(ob, meta[0], meta[2], accum, count);
                        if (count >= accum.length - targetSampleRate) {
                            float[] grown = new float[accum.length * 2];
                            System.arraycopy(accum, 0, grown, 0, count);
                            accum = grown;
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true;
                }
            }

            if (count == 0) { Log.e(TAG, "MediaCodec: decoded 0 frames"); return null; }

            float[] frames = (count == accum.length) ? accum : java.util.Arrays.copyOf(accum, count);
            // Resample to device native SR (meta[1] is decoded SR)
            if (meta[1] != targetSampleRate) {
                frames = linearResample(frames, meta[1], targetSampleRate);
            }

            short[] out = floatToShort(frames);
            Log.i(TAG, "MediaCodec decoded: " + meta[0] + "ch " + meta[1] + "Hz "
                    + mime + " → " + out.length + " frames @ " + targetSampleRate);
            return out;

        } catch (Exception e) {
            Log.e(TAG, "MediaCodec decode exception", e);
            return null;
        } finally {
            try { if (codec     != null) { codec.stop(); codec.release(); } } catch (Exception ignored) {}
            try { if (extractor != null) { extractor.release(); }           } catch (Exception ignored) {}
            if (tmpFile != null) tmpFile.delete();
        }
    }

    private int[] extractMeta(MediaFormat fmt) {
        int ch  = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        int sr  = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)   : targetSampleRate;
        int enc = AudioFormat.ENCODING_PCM_16BIT;
        if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            enc = fmt.getInteger(MediaFormat.KEY_PCM_ENCODING);
        }
        return new int[]{ Math.max(1, ch), Math.max(1, sr), enc };
    }

    private int appendFrames(ByteBuffer ob, int ch, int encoding, float[] accum, int count) {
        ob.order(ByteOrder.LITTLE_ENDIAN);
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_FLOAT: {
                while (ob.remaining() >= 4 * ch && count < accum.length) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) sum += ob.getFloat();
                    accum[count++] = sum / ch;
                }
                break;
            }
            case AudioFormat.ENCODING_PCM_8BIT: {
                while (ob.remaining() >= ch && count < accum.length) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) sum += ((ob.get() & 0xFF) - 128) / 128f;
                    accum[count++] = sum / ch;
                }
                break;
            }
            default: {
                ShortBuffer sb = ob.asShortBuffer();
                while (sb.remaining() >= ch && count < accum.length) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) sum += sb.get() / 32768f;
                    accum[count++] = sum / ch;
                }
                break;
            }
        }
        return count;
    }

    // ─── Shared utilities ────────────────────────────────────────────────────

    private float[] linearResample(float[] src, int srcRate, int dstRate) {
        if (srcRate == dstRate || src.length == 0) return src;
        double ratio  = (double) srcRate / dstRate;
        int    outLen = (int)(src.length / ratio);
        float[] out   = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double pos = i * ratio;
            int    idx = (int) pos;
            float  frc = (float)(pos - idx);
            float  a   = (idx     < src.length) ? src[idx]     : 0f;
            float  b   = (idx + 1 < src.length) ? src[idx + 1] : 0f;
            out[i] = a + frc * (b - a);
        }
        return out;
    }

    private short[] floatToShort(float[] f) {
        short[] out = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (short)(Math.max(-1f, Math.min(1f, f[i])) * 32767f);
        }
        return out;
    }

    private boolean validPad(int idx) {
        if (idx < 0 || idx >= PAD_COUNT) {
            Log.e(TAG, "Invalid pad index: " + idx); return false;
        }
        return true;
    }

    private byte[] readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private byte[] readAssetFileDescriptor(AssetFileDescriptor afd) throws Exception {
        byte[]      data = new byte[(int) afd.getLength()];
        InputStream is   = afd.createInputStream();
        int read = 0;
        while (read < data.length) {
            int n = is.read(data, read, data.length - read);
            if (n < 0) break;
            read += n;
        }
        is.close();
        return data;
    }
}
