package com.pramod.audioeditor.data;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Memory-mapped canonical 16-bit PCM WAV reader.
 * Thread-safe read-only access to large audio files without loading into heap.
 */
public class MappedPcmSource implements PcmSource {

    private final MappedByteBuffer mapped;
    private final int sampleRate;
    private final int channels;
    private final long totalFrames;
    private final int dataSize;
    private final RandomAccessFile raf;
    private final FileChannel channel;

    private MappedPcmSource(MappedByteBuffer mapped, int sampleRate, int channels,
                            long totalFrames, int dataSize,
                            RandomAccessFile raf, FileChannel channel) {
        this.mapped = mapped;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.totalFrames = totalFrames;
        this.dataSize = dataSize;
        this.raf = raf;
        this.channel = channel;
    }

    public static MappedPcmSource open(java.io.File wavFile) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(wavFile, "r");
        FileChannel channel = raf.getChannel();

        // Read 44-byte WAV header
        byte[] header = new byte[44];
        ByteBuffer headerBuf = ByteBuffer.wrap(header);
        channel.read(headerBuf, 0);

        int channels = header[22] & 0xFF | (header[23] & 0xFF) << 8;
        int sampleRate = header[24] & 0xFF | (header[25] & 0xFF) << 8
                | (header[26] & 0xFF) << 16 | (header[27] & 0xFF) << 24;
        int bitsPerSample = header[34] & 0xFF | (header[35] & 0xFF) << 8;
        int dataSize = header[40] & 0xFF | (header[41] & 0xFF) << 8
                | (header[42] & 0xFF) << 16 | (header[43] & 0xFF) << 24;

        if (bitsPerSample != 16) {
            throw new IOException("Only 16-bit PCM WAV supported, got " + bitsPerSample + " bit");
        }

        long totalFrames = dataSize / (channels * 2); // 2 bytes per sample

        // Memory-map the PCM data (skip 44-byte header)
        MappedByteBuffer mapped = channel.map(
                FileChannel.MapMode.READ_ONLY, 44, dataSize);

        return new MappedPcmSource(mapped, sampleRate, channels, totalFrames, dataSize, raf, channel);
    }

    @Override public int sampleRate() { return sampleRate; }
    @Override public int channels() { return channels; }
    @Override public long totalFrames() { return totalFrames; }

    @Override
    public void readFrames(long startFrame, int numFrames, float[] out, int outOffset, boolean toMono) {
        int bytesPerFrame = channels * 2;
        int byteOffset = (int)(startFrame * bytesPerFrame);
        int bytesToRead = numFrames * bytesPerFrame;

        ByteBuffer buf = mapped.slice();
        buf.position(byteOffset);

        if (channels == 1) {
            for (int i = 0; i < numFrames && buf.hasRemaining(); i++) {
                short s = buf.getShort();
                out[outOffset + i] = s / 32768f;
            }
        } else if (toMono) {
            for (int i = 0; i < numFrames && buf.remaining() >= 4; i++) {
                short l = buf.getShort();
                short r = buf.getShort();
                out[outOffset + i] = ((l + r) / 2f) / 32768f;
            }
        } else {
            // Stereo interleaved
            int idx = outOffset;
            for (int i = 0; i < numFrames && buf.remaining() >= 4; i++) {
                out[idx++] = buf.getShort() / 32768f;
                out[idx++] = buf.getShort() / 32768f;
            }
        }
    }

    @Override
    public void release() {
        try {
            if (channel != null) channel.close();
            if (raf != null) raf.close();
        } catch (IOException ignored) {}
    }
}
