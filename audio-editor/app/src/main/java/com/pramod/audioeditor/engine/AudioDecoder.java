package com.pramod.audioeditor.engine;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.pramod.audioeditor.data.WavFileWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Multi-format audio decoder. Decodes MP3/FLAC/OGG/AAC/M4A → canonical 16-bit PCM WAV.
 * Uses MediaCodec for streaming decode (no OOM on large files).
 */
public class AudioDecoder {

    public interface ProgressListener {
        void onProgress(int percent);
        void onComplete(File wavFile);
        void onError(String message);
    }

    /**
     * Decode any audio file to canonical 16-bit PCM WAV.
     * @param input source audio file
     * @param output target WAV file
     * @param listener progress callback
     */
    public static void decodeToWav(File input, File output, ProgressListener listener) throws IOException {
        // Check if already 16-bit WAV
        if (isWav16bit(input)) {
            copyFile(input, output);
            if (listener != null) listener.onComplete(output);
            return;
        }

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input.getAbsolutePath());

        // Find audio track
        int audioTrack = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrack = i;
                break;
            }
        }
        if (audioTrack < 0) throw new IOException("No audio track found");

        extractor.selectTrack(audioTrack);
        MediaFormat format = extractor.getTrackFormat(audioTrack);

        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION) : 0;
        String mime = format.getString(MediaFormat.KEY_MIME);

        // Create decoder
        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();

        WavFileWriter writer = new WavFileWriter();
        writer.open(output, sampleRate, channels, 16);

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean done = false;
        long totalOutput = 0;

        while (!done) {
            // Feed input
            int inIdx = decoder.dequeueInputBuffer(10000);
            if (inIdx >= 0) {
                ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                int sampleSize = extractor.readSampleData(inBuf, 0);
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            // Read output
            int outIdx = decoder.dequeueOutputBuffer(info, 10000);
            if (outIdx >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    done = true;
                }
                if (info.size > 0) {
                    ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                    byte[] pcmBytes = new byte[info.size];
                    outBuf.get(pcmBytes);

                    // Convert to float for writing
                    int samples = info.size / (channels * 2);
                    float[] floatBuf = new float[samples * channels];
                    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(
                            new short[samples * channels]);
                    short[] shortBuf = new short[samples * channels];
                    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuf);

                    writer.writeShort(shortBuf, 0, shortBuf.length);
                    totalOutput += info.size;

                    if (durationUs > 0 && listener != null) {
                        int pct = (int)((double) info.presentationTimeUs / durationUs * 100);
                        listener.onProgress(Math.min(99, pct));
                    }
                }
                decoder.releaseOutputBuffer(outIdx, false);
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();
        writer.close();

        if (listener != null) {
            listener.onProgress(100);
            listener.onComplete(output);
        }
    }

    private static boolean isWav16bit(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] header = new byte[44];
            if (fis.read(header) < 44) { fis.close(); return false; }
            fis.close();
            // Check RIFF/WAVE magic
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') return false;
            if (header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E') return false;
            int bits = header[34] & 0xFF | (header[35] & 0xFF) << 8;
            return bits == 16;
        } catch (Exception e) {
            return false;
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.close();
    }
}
