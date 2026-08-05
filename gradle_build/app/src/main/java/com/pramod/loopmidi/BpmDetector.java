package com.pramod.loopmidi;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * BPM detection — decodes audio to mono PCM, then runs dual-method detection.
 * Primary: direct InputStream decode (handles WAV and raw PCM).
 * Fallback: MediaCodec (handles MP3, OGG, FLAC, AAC, M4A).
 */
public class BpmDetector {

    private static final int HOP = 1024;
    private static final float MIN_BPM = 60f;
    private static final float MAX_BPM = 240f;

    public static int detectBpm(Context context, Uri uri) {
        try {
            float[] pcm = decodeToMono(context, uri);
            if (pcm == null || pcm.length < 22050) return 120;
            int sr = getSampleRate(context, uri);
            return detectBest(pcm, sr);
        } catch (Exception e) {
            return 120;
        }
    }

    private static int getSampleRate(Context context, Uri uri) {
        try {
            MediaExtractor ext = new MediaExtractor();
            ext.setDataSource(context, uri, null);
            for (int i = 0; i < ext.getTrackCount(); i++) {
                MediaFormat fmt = ext.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    ext.release();
                    return sr;
                }
            }
            ext.release();
        } catch (Exception ignored) {}
        return 44100;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DECODE — try WAV first, then MediaCodec
    // ═══════════════════════════════════════════════════════════════════════

    private static float[] decodeToMono(Context context, Uri uri) throws Exception {
        // Try WAV/PCM direct decode first (fastest, most reliable)
        float[] wav = tryDecodeWav(context, uri);
        if (wav != null && wav.length > 0) return wav;

        // Try MediaCodec for compressed formats
        float[] mc = tryDecodeMediaCodec(context, uri);
        if (mc != null && mc.length > 0) return mc;

        return null;
    }

    /**
     * Decode WAV/PCM file directly from InputStream.
     * Handles standard44-byte WAV headers and raw PCM.
     */
    private static float[] tryDecodeWav(Context context, Uri uri) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            // Read header bytes (may need more than44 for non-standard headers)
            byte[] header = readFully(is, 44);
            if (header == null || header.length < 44) return null;

            // Check RIFF/WAVE magic
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F')
                return null;
            if (header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')
                return null;

            int channels = u16(header, 22);
            int bitsPerSample = u16(header, 34);
            int fmtChunkSize = u32(header, 16);
            int dataSize = u32(header, 40);

            if (channels < 1 || channels > 2) return null;
            if (bitsPerSample != 16) return null;

            // If fmt chunk is larger than16, skip extra bytes to reach "data" chunk
            int extraFmtBytes = fmtChunkSize - 16;
            if (extraFmtBytes > 0 && extraFmtBytes < 100) {
                byte[] skip = readFully(is, extraFmtBytes);
                // After extra fmt bytes, we should be at "data" chunk
                // Read data header (8 bytes: "data" + size)
                byte[] dataHeader = readFully(is, 8);
                if (dataHeader != null && dataHeader.length >= 8
                        && dataHeader[0] == 'd' && dataHeader[1] == 'a'
                        && dataHeader[2] == 't' && dataHeader[3] == 'a') {
                    dataSize = (dataHeader[4] & 0xFF) | ((dataHeader[5] & 0xFF) << 8)
                            | ((dataHeader[6] & 0xFF) << 16) | ((dataHeader[7] & 0xFF) << 24);
                }
            }

            // Read all PCM data
            byte[] pcmBytes;
            if (dataSize > 0 && dataSize < 100_000_000) {
                pcmBytes = readFully(is, dataSize);
            } else {
                // dataSize unknown or 0 — read all remaining bytes
                pcmBytes = readAllRemaining(is);
            }

            if (pcmBytes == null || pcmBytes.length < 2) return null;

            int bytesPerFrame = channels * 2;
            int totalFrames = pcmBytes.length / bytesPerFrame;
            if (totalFrames < 100) return null;

            ShortBuffer sb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            short[] shorts = new short[totalFrames * channels];
            sb.get(shorts);

            float[] mono = new float[totalFrames];
            if (channels == 2) {
                for (int i = 0; i < totalFrames; i++) {
                    mono[i] = (shorts[i * 2] + shorts[i * 2 + 1]) / (2f * 32768f);
                }
            } else {
                for (int i = 0; i < totalFrames; i++) {
                    mono[i] = shorts[i] / 32768f;
                }
            }
            return mono;
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
        }
    }

    /** Read exactly n bytes from InputStream, or null on failure. */
    private static byte[] readFully(InputStream is, int n) {
        try {
            byte[] buf = new byte[n];
            int pos = 0;
            while (pos < n) {
                int read = is.read(buf, pos, n - pos);
                if (read < 0) break;
                pos += read;
            }
            return pos == n ? buf : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Read all remaining bytes from InputStream. */
    private static byte[] readAllRemaining(InputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Read unsigned16-bit LE from byte array. */
    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    /** Read unsigned32-bit LE from byte array. */
    private static int u32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MediaCodec fallback for compressed formats
    // ═══════════════════════════════════════════════════════════════════════

    private static float[] tryDecodeMediaCodec(Context context, Uri uri) {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(context, uri, null);

            int audioTrack = -1;
            int channels = 1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/") && !mime.equals(MediaFormat.MIMETYPE_AUDIO_RAW)) {
                    audioTrack = i;
                    channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    extractor.selectTrack(i);
                    decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(fmt, null, null, 0);
                    decoder.start();
                    break;
                }
            }
            if (audioTrack < 0 || decoder == null) return null;

            java.util.ArrayList<float[]> chunks = new java.util.ArrayList<>();
            int totalFrames = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean done = false;

            while (!done) {
                int inIdx = decoder.dequeueInputBuffer(10000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        int sz = extractor.readSampleData(inBuf, 0);
                        if (sz < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sz, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIdx = decoder.dequeueOutputBuffer(info, 10000);
                if (outIdx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true;
                    if (info.size > 0) {
                        ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                        if (outBuf != null) {
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            int nShorts = info.size / 2;
                            short[] sbuf = new short[nShorts];
                            outBuf.asShortBuffer().get(sbuf);
                            int nFrames = info.size / (channels * 2);
                            float[] chunk = new float[nFrames];
                            if (channels == 2) {
                                for (int f = 0; f < nFrames && f * 2 + 1 < nShorts; f++) {
                                    chunk[f] = (sbuf[f * 2] + sbuf[f * 2 + 1]) / (2f * 32768f);
                                }
                            } else {
                                for (int f = 0; f < nFrames && f < nShorts; f++) {
                                    chunk[f] = sbuf[f] / 32768f;
                                }
                            }
                            chunks.add(chunk);
                            totalFrames += nFrames;
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false);
                }
            }

            if (totalFrames == 0) return null;
            float[] pcm = new float[totalFrames];
            int pos = 0;
            for (float[] c : chunks) { System.arraycopy(c, 0, pcm, pos, c.length); pos += c.length; }
            return pcm;
        } catch (Exception e) {
            return null;
        } finally {
            if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) {} try { decoder.release(); } catch (Exception ignored) {} }
            if (extractor != null) { try { extractor.release(); } catch (Exception ignored) {} }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BPM DETECTION — dual-method consensus
    // ═══════════════════════════════════════════════════════════════════════

    private static int detectBest(float[] pcm, int sampleRate) {
        int bpm1 = detectByPeakInterval(pcm, sampleRate);
        int bpm2 = detectByAutocorrelation(pcm, sampleRate);

        if (bpm1 == 120 && bpm2 == 120) return 120;
        if (bpm1 == 120) return bpm2;
        if (bpm2 == 120) return bpm1;
        if (Math.abs(bpm1 - bpm2) <= 3) return bpm1;
        if (bpm1 >= 90 && bpm1 <= 150) return bpm1;
        if (bpm2 >= 90 && bpm2 <= 150) return bpm2;
        return (Math.abs(bpm1 - 120) <= Math.abs(bpm2 - 120)) ? bpm1 : bpm2;
    }

    private static int detectByPeakInterval(float[] samples, int sampleRate) {
        float[] energy = computeEnergy(samples);
        float[] onset = computeOnset(energy);
        float med = computeMedian(onset);
        float threshold = Math.max(med * 1.5f, computeMean(onset) * 2f);
        if (threshold < 0.0005f) return 120;

        java.util.ArrayList<Integer> peaks = new java.util.ArrayList<>();
        for (int i = 2; i < onset.length - 1; i++) {
            if (onset[i] > threshold && onset[i] > onset[i - 1] && onset[i] >= onset[i + 1]) {
                peaks.add(i);
            }
        }
        if (peaks.size() < 4) return 120;

        java.util.ArrayList<Float> intervals = new java.util.ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            intervals.add((float) (peaks.get(i) - peaks.get(i - 1)));
        }
        float bestInterval = findModeInterval(intervals);
        if (bestInterval <= 0) return 120;
        return clampBpm(60.0f * sampleRate / (HOP * bestInterval));
    }

    private static float findModeInterval(java.util.ArrayList<Float> intervals) {
        if (intervals.isEmpty()) return 0;
        java.util.Collections.sort(intervals);
        float best = intervals.get(0);
        int bestCount = 0;
        for (int i = 0; i < intervals.size(); i++) {
            float center = intervals.get(i);
            int count = 0;
            for (float v : intervals) if (Math.abs(v - center) <= 0.5f) count++;
            if (count > bestCount) { bestCount = count; best = center; }
        }
        return bestCount >= 3 ? best : 0;
    }

    private static int detectByAutocorrelation(float[] samples, int sampleRate) {
        float[] energy = computeEnergy(samples);
        float[] onset = computeOnset(energy);
        int nHops = onset.length;
        int minLag = Math.max(2, (int) (sampleRate * 60.0 / (HOP * MAX_BPM)));
        int maxLag = Math.min(nHops / 2, (int) (sampleRate * 60.0 / (HOP * MIN_BPM)));
        if (minLag >= maxLag) return 120;

        float bestScore = -1;
        int bestLag = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            float sum = 0, normA = 0, normB = 0;
            int count = nHops - lag;
            for (int i = 0; i < count; i++) {
                sum += onset[i] * onset[i + lag];
                normA += onset[i] * onset[i];
                normB += onset[i + lag] * onset[i + lag];
            }
            float denom = (float) Math.sqrt(normA * normB);
            float acf = denom > 0 ? sum / denom : 0;
            if (acf > bestScore) { bestScore = acf; bestLag = lag; }
        }
        if (bestLag <= 0 || bestScore < 0.08f) return 120;
        float bpm = 60.0f * sampleRate / (HOP * bestLag);

        float best = bpm;
        float bestE = beatEnergy(onset, nHops, sampleRate, bpm);
        for (int m = 2; m <= 3; m++) {
            float c = bpm * m;
            if (c <= MAX_BPM) { float e = beatEnergy(onset, nHops, sampleRate, c); if (e > bestE * 1.15f) { best = c; bestE = e; } }
        }
        for (int d = 2; d <= 3; d++) {
            float c = bpm / d;
            if (c >= MIN_BPM) { float e = beatEnergy(onset, nHops, sampleRate, c); if (e > bestE * 1.15f) { best = c; bestE = e; } }
        }
        return clampBpm(best);
    }

    private static float beatEnergy(float[] onset, int nHops, int sr, float bpm) {
        float lag = (float) sr / (HOP * bpm / 60.0f);
        if (lag < 1) return 0;
        float total = 0;
        for (int i = 0; i < nHops; i += Math.max(1, (int) lag)) total += onset[i];
        return total;
    }

    private static float[] computeEnergy(float[] samples) {
        int nHops = samples.length / HOP;
        float[] energy = new float[nHops];
        for (int i = 0; i < nHops; i++) {
            int start = i * HOP;
            float sum = 0;
            int end = Math.min(start + HOP, samples.length);
            for (int j = start; j < end; j++) sum += samples[j] * samples[j];
            energy[i] = (float) Math.sqrt(sum / (end - start));
        }
        return energy;
    }

    private static float[] computeOnset(float[] energy) {
        int n = energy.length;
        float[] onset = new float[n];
        for (int i = 1; i < n; i++) {
            float diff = energy[i] - energy[i - 1];
            onset[i] = diff > 0 ? diff : 0;
        }
        int window = 16;
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - window);
            int hi = Math.min(n, i + window + 1);
            float[] nb = new float[hi - lo];
            System.arraycopy(onset, lo, nb, 0, hi - lo);
            java.util.Arrays.sort(nb);
            float threshold = nb[nb.length / 2] * 1.5f;
            if (onset[i] < threshold) onset[i] = 0;
        }
        return onset;
    }

    private static float computeMedian(float[] arr) { float[] s = arr.clone(); java.util.Arrays.sort(s); return s[s.length / 2]; }
    private static float computeMean(float[] arr) { float sum = 0; for (float v : arr) sum += v; return sum / arr.length; }
    private static int clampBpm(float bpm) {
        if (bpm < MIN_BPM || bpm > MAX_BPM) return 120;
        int r = Math.round(bpm);
        return (r >= MIN_BPM && r <= MAX_BPM) ? r : 120;
    }
}
