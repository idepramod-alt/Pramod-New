package com.pramod.loopmidi;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * AudioEngine — bridges Java loading/playback calls to the native Oboe engine.
 *
 * Supported input formats (auto-detected by file header):
 *   WAV  — PCM 8-bit, 16-bit, 24-bit, 32-bit; any sample rate; mono/stereo
 *   MP3  — any bitrate (32 kbps … 320 kbps)
 *   AAC  — LC / HE-AAC
 *   OGG  — Vorbis
 *   FLAC — lossless
 *   Any format Android's MediaCodec supports
 *
 * All formats are decoded and resampled → 16-bit PCM mono 44 100 Hz before
 * being handed to the native layer via nativeLoadSample().
 */
public class AudioEngine {

    private static final int    PAD_COUNT      = 16;
    private static final int    TARGET_SR      = 44100;   // native engine sample rate
    private static final String TAG            = "AudioEngine";

    private Context context;
    private long    nativeHandle;

    // ── JNI declarations ──────────────────────────────────────────────────────
    private native long nativeCreateAudioEngine();
    private native void nativeDestroyAudioEngine();
    private native void nativeLoadSample(int padIndex, short[] pcm, int length);
    private native void nativePlaySample(int padIndex, float volume, float pitch,
                                         boolean delayOn, float delayMs, float delayLevel,
                                         float eqLow, float eqMid, float eqHigh,
                                         int chokeGroup, float attackMs, float releaseMs);
    private native void nativeStopAll();
    private native void nativeStopPad(int padIndex);

    // ── Optional loop-specific JNI (if present in .so) ────────────────────────
    private native void nativePlayLoop(int padIndex, float volume, float pitch);
    private native void nativeUpdateLoopPitch(int padIndex, float volume, float pitch);

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
        this.context      = ctx;
        this.nativeHandle = 0L;
        long handle = nativeCreateAudioEngine();
        this.nativeHandle = handle;
        if (handle != 0) {
            Log.i(TAG, "Audio engine initialized with native Oboe");
        } else {
            Log.e(TAG, "Failed to initialize audio engine");
        }
    }

    public void start() {}

    public void stop() {
        if (nativeHandle != 0) {
            nativeDestroyAudioEngine();
            nativeHandle = 0L;
        }
    }

    // ── Public load methods ───────────────────────────────────────────────────

    /** Load from a user-selected URI (file picker). Supports any audio format. */
    public SampleData loadWavFromUri(int padIndex, Uri uri) throws IOException {
        try {
            if (nativeHandle == 0) return null;
            if (!validPad(padIndex)) return null;

            AssetFileDescriptor afd = context.getContentResolver()
                    .openAssetFileDescriptor(uri, "r");
            if (afd == null) return null;
            byte[] raw = readAssetFileDescriptor(afd);
            afd.close();

            short[] pcm = decodeAudioToPcm(raw);
            if (pcm == null || pcm.length == 0) {
                Log.e(TAG, "loadWavFromUri: decode failed for " + uri);
                return null;
            }
            nativeLoadSample(padIndex, pcm, pcm.length);
            Log.i(TAG, "loadWavFromUri pad=" + padIndex + " frames=" + pcm.length);

            SampleData sd = new SampleData();
            sd.uri     = uri;
            sd.soundId = padIndex;
            sd.loaded  = true;
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
            if (nativeHandle == 0) return null;
            if (!validPad(padIndex)) return null;

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
            sd.soundId = resId;
            sd.loaded  = true;
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading raw sound", e);
            return null;
        }
    }

    /** Load from assets/ folder. Supports any audio format. */
    public SampleData loadWavFromAsset(int padIndex, String assetPath) throws IOException {
        if (nativeHandle == 0) return null;
        if (!validPad(padIndex)) return null;
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
            sd.soundId = padIndex;
            sd.loaded  = true;
            return sd;
        } catch (Exception e) {
            Log.e(TAG, "Error loading asset", e);
            return null;
        }
    }

    public void unloadSample(SampleData sample) {
        if (sample != null) {
            sample.soundId = 0;
            sample.loaded  = false;
            sample.uri     = null;
        }
    }

    public void preloadSample(SampleData sample) {}

    // ── Playback ──────────────────────────────────────────────────────────────

    public void playSample(int padIndex, SampleData sample,
                           float volume, float pitch, int loopMode,
                           boolean delayOn, float delayMs, float delayLevel,
                           float eqLow, float eqMid, float eqHigh,
                           int chokeGroup, float attackMs, float releaseMs) {
        try {
            if (nativeHandle != 0 && sample != null && sample.loaded) {
                float vol  = Math.max(0.0f, Math.min(1.0f, volume));
                float rate = Math.max(0.5f, Math.min(2.0f, pitch));
                if (loopMode == 1) {
                    // Loop mode: voice restarts when it reaches end (sustained loop)
                    nativePlayLoop(padIndex, vol, rate);
                } else {
                    // One-shot / drum mode: play once with full FX chain
                    nativePlaySample(padIndex, vol, rate,
                            delayOn, delayMs, delayLevel,
                            eqLow, eqMid, eqHigh,
                            chokeGroup, attackMs, releaseMs);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sample", e);
        }
    }

    public void playSample(int padIndex, SampleData sample,
                           float volume, float pitch, int loopMode) {
        playSample(padIndex, sample, volume, pitch, loopMode,
                false, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0f);
    }

    public void stopPad(int padIndex) {
        if (nativeHandle != 0) nativeStopPad(padIndex);
    }

    public void stopAll() {
        if (nativeHandle != 0) nativeStopAll();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UNIVERSAL AUDIO DECODER
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Auto-detects the audio format from the file header and decodes to
     * 16-bit PCM mono at TARGET_SR (44 100 Hz).
     *
     * Routing:
     *   RIFF/WAVE header  →  decodePcmFromWav()  (pure-Java, zero I/O)
     *   Everything else   →  decodeWithMediaCodec() (MP3, AAC, OGG, FLAC …)
     */
    private short[] decodeAudioToPcm(byte[] data) {
        if (data == null || data.length < 4) return null;

        // Detect WAV: first 4 bytes == "RIFF"
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            Log.d(TAG, "Format detected: WAV — using pure-Java decoder");
            return decodePcmFromWav(data);
        }

        // Everything else: MediaCodec (MP3, AAC, OGG, FLAC, …)
        Log.d(TAG, "Format detected: compressed — using MediaCodec decoder");
        return decodeWithMediaCodec(data);
    }

    // ─── WAV decoder ──────────────────────────────────────────────────────────

    /**
     * Decodes a WAV byte array to 16-bit PCM mono at TARGET_SR.
     *
     * Supports:
     *   • PCM formats: 8-bit unsigned, 16-bit signed, 24-bit signed, 32-bit signed/float
     *   • Any number of channels (mixed down to mono)
     *   • Any sample rate (linear-resampled to TARGET_SR)
     */
    private short[] decodePcmFromWav(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            // ── RIFF header ──────────────────────────────────────────────────
            if (buf.remaining() < 12) { Log.e(TAG, "WAV: too short"); return null; }
            int riff = buf.getInt();                    // "RIFF"
            buf.getInt();                               // file size (ignore)
            int wave = buf.getInt();                    // "WAVE"
            if (riff != 0x46464952 || wave != 0x45564157) {
                Log.e(TAG, "WAV: invalid RIFF/WAVE header");
                return null;
            }

            // ── Chunk scan ───────────────────────────────────────────────────
            int audioFormat   = 1;   // PCM
            int channels      = 1;
            int sampleRate    = TARGET_SR;
            int bitsPerSample = 16;
            byte[] pcmBytes   = null;

            while (buf.remaining() >= 8) {
                int   chunkId   = buf.getInt();
                int   chunkSize = buf.getInt();

                if (chunkId == 0x20746D66) {           // "fmt "
                    if (chunkSize < 16) {
                        Log.e(TAG, "WAV: fmt chunk too small");
                        return null;
                    }
                    audioFormat   = buf.getShort() & 0xFFFF;
                    channels      = buf.getShort() & 0xFFFF;
                    sampleRate    = buf.getInt();
                    buf.getInt();                       // byte rate
                    buf.getShort();                     // block align
                    bitsPerSample = buf.getShort() & 0xFFFF;
                    // skip extended fmt bytes if present
                    int extra = chunkSize - 16;
                    if (extra > 0) {
                        buf.position(buf.position() + Math.min(extra, buf.remaining()));
                    }

                } else if (chunkId == 0x61746164) {    // "data"
                    int sz = Math.min(chunkSize, buf.remaining());
                    pcmBytes = new byte[sz];
                    buf.get(pcmBytes);
                    break;

                } else {
                    // unknown chunk — skip
                    int skip = Math.min(chunkSize, buf.remaining());
                    buf.position(buf.position() + skip);
                }
            }

            if (pcmBytes == null) { Log.e(TAG, "WAV: no data chunk"); return null; }

            // ── Decode raw bytes → float[] mono ─────────────────────────────
            // audioFormat: 1 = PCM integer, 3 = IEEE float
            int bytesPerSample = (bitsPerSample + 7) / 8;
            if (bytesPerSample == 0) bytesPerSample = 2;
            int frameCount = pcmBytes.length / (bytesPerSample * Math.max(channels, 1));
            float[] mono = new float[frameCount];
            ByteBuffer pb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < frameCount; i++) {
                double sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    if (pb.remaining() < bytesPerSample) break;
                    sum += sampleToFloat(pb, bitsPerSample, audioFormat);
                }
                mono[i] = (float)(sum / channels);
            }

            // ── Resample to TARGET_SR if needed ─────────────────────────────
            float[] resampled = (sampleRate == TARGET_SR)
                    ? mono
                    : linearResample(mono, sampleRate, TARGET_SR);

            // ── Convert float[] → short[] ────────────────────────────────────
            short[] out = new short[resampled.length];
            for (int i = 0; i < resampled.length; i++) {
                float f = Math.max(-1f, Math.min(1f, resampled[i]));
                out[i] = (short)(f * 32767f);
            }
            Log.i(TAG, "WAV decoded: " + channels + "ch " + sampleRate + "Hz "
                    + bitsPerSample + "bit → " + out.length + " mono frames @ " + TARGET_SR + "Hz");
            return out;

        } catch (Exception e) {
            Log.e(TAG, "WAV decode exception", e);
            return null;
        }
    }

    /** Read one sample from the ByteBuffer and return as float in [-1, 1]. */
    private float sampleToFloat(ByteBuffer b, int bits, int fmt) {
        switch (bits) {
            case 8:
                // 8-bit WAV is unsigned [0,255]
                return ((b.get() & 0xFF) - 128) / 128f;
            case 16:
                return b.getShort() / 32768f;
            case 24: {
                int lo  = b.get() & 0xFF;
                int mid = b.get() & 0xFF;
                int hi  = b.get();           // signed
                int v   = (hi << 16) | (mid << 8) | lo;
                return v / 8388608f;
            }
            case 32:
                if (fmt == 3) {              // IEEE float
                    return b.getFloat();
                } else {                     // 32-bit integer
                    return b.getInt() / 2147483648f;
                }
            default:
                // Skip unknown byte width (2 bytes fallback)
                if (b.remaining() >= 2) b.getShort();
                return 0f;
        }
    }

    /** Linear (lerp) resampler — quality sufficient for music/percussion. */
    private float[] linearResample(float[] src, int srcRate, int dstRate) {
        if (srcRate == dstRate) return src;
        double ratio  = (double) srcRate / dstRate;
        int    outLen = (int)(src.length / ratio);
        float[] out   = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double pos  = i * ratio;
            int    idx  = (int) pos;
            float  frac = (float)(pos - idx);
            float  a    = (idx < src.length)     ? src[idx]     : 0f;
            float  b    = (idx + 1 < src.length) ? src[idx + 1] : 0f;
            out[i] = a + frac * (b - a);
        }
        return out;
    }

    // ─── MediaCodec decoder (MP3 / AAC / OGG / FLAC / any compressed) ────────

    /**
     * Decodes any Android-supported compressed audio format using
     * MediaExtractor + MediaCodec.
     * Output: 16-bit PCM mono at TARGET_SR.
     */
    private short[] decodeWithMediaCodec(byte[] data) {
        File tmpFile = null;
        MediaExtractor  extractor = null;
        MediaCodec      codec     = null;
        try {
            // Write bytes to a temp file (MediaExtractor needs a path/FD)
            tmpFile = File.createTempFile("ac_decode_", ".tmp", context.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                fos.write(data);
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(tmpFile.getAbsolutePath());

            // Find first audio track
            int trackIndex = -1;
            MediaFormat trackFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex  = i;
                    trackFormat = fmt;
                    break;
                }
            }
            if (trackIndex < 0 || trackFormat == null) {
                Log.e(TAG, "MediaCodec: no audio track found");
                return null;
            }

            String mime     = trackFormat.getString(MediaFormat.KEY_MIME);
            int srcChannels = trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int srcRate     = trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : TARGET_SR;

            Log.d(TAG, "MediaCodec: mime=" + mime
                    + " ch=" + srcChannels + " sr=" + srcRate);

            extractor.selectTrack(trackIndex);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(trackFormat, null, null, 0);
            codec.start();

            ByteBuffer[]        inputBuffers  = codec.getInputBuffers();
            ByteBuffer[]        outputBuffers = codec.getOutputBuffers();
            MediaCodec.BufferInfo info        = new MediaCodec.BufferInfo();
            ArrayList<Short>    samples       = new ArrayList<>(256 * 1024);
            boolean             sawEOS        = false;
            boolean             decodeEOS     = false;
            final long          TIMEOUT_US    = 5000L;

            while (!decodeEOS) {
                // Feed input
                if (!sawEOS) {
                    int inIdx = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inIdx >= 0) {
                        ByteBuffer ib    = inputBuffers[inIdx];
                        int        nRead = extractor.readSampleData(ib, 0);
                        if (nRead < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawEOS = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            codec.queueInputBuffer(inIdx, 0, nRead, pts, 0);
                            extractor.advance();
                        }
                    }
                }

                // Drain output
                int outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.getOutputBuffers();
                } else if (outIdx >= 0) {
                    ByteBuffer ob = outputBuffers[outIdx];
                    ob.position(info.offset);
                    ob.limit(info.offset + info.size);

                    // Output is always 16-bit PCM in signed little-endian
                    while (ob.remaining() >= 2 * srcChannels) {
                        long chSum = 0;
                        for (int ch = 0; ch < srcChannels; ch++) {
                            chSum += ob.order(ByteOrder.LITTLE_ENDIAN).getShort();
                        }
                        samples.add((short)(chSum / srcChannels));   // mono mix
                    }

                    codec.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        decodeEOS = true;
                    }
                }
            }

            // Convert ArrayList → short[]
            short[] decoded = new short[samples.size()];
            for (int i = 0; i < decoded.length; i++) decoded[i] = samples.get(i);

            // Resample if source rate ≠ target
            short[] out;
            if (srcRate != TARGET_SR) {
                float[] fSamples = new float[decoded.length];
                for (int i = 0; i < decoded.length; i++) fSamples[i] = decoded[i] / 32768f;
                float[] resampled = linearResample(fSamples, srcRate, TARGET_SR);
                out = new short[resampled.length];
                for (int i = 0; i < resampled.length; i++) {
                    out[i] = (short)(Math.max(-1f, Math.min(1f, resampled[i])) * 32767f);
                }
            } else {
                out = decoded;
            }

            Log.i(TAG, "MediaCodec decoded: " + srcChannels + "ch " + srcRate + "Hz "
                    + mime + " → " + out.length + " mono frames @ " + TARGET_SR + "Hz");
            return out;

        } catch (Exception e) {
            Log.e(TAG, "MediaCodec decode exception", e);
            return null;
        } finally {
            if (codec     != null) try { codec.stop();     codec.release();     } catch (Exception ignored) {}
            if (extractor != null) try { extractor.release();                   } catch (Exception ignored) {}
            if (tmpFile   != null) tmpFile.delete();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private boolean validPad(int idx) {
        if (idx < 0 || idx >= PAD_COUNT) {
            Log.e(TAG, "Invalid pad index: " + idx);
            return false;
        }
        return true;
    }

    private byte[] readFully(InputStream is) throws IOException {
        byte[] buf = new byte[65536];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private byte[] readAssetFileDescriptor(AssetFileDescriptor afd) throws Exception {
        byte[] data = new byte[(int) afd.getLength()];
        InputStream is = afd.createInputStream();
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
