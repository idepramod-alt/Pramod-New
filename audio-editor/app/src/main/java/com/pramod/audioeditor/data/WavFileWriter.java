package com.pramod.audioeditor.data;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Streaming WAV file writer. Supports 16-bit, 24-bit, and 32-bit float.
 * Patches header sizes on close().
 */
public class WavFileWriter {

    private RandomAccessFile raf;
    private int sampleRate;
    private int channels;
    private int bitsPerSample;
    private int dataSize;
    private boolean isFloat;

    public void open(java.io.File file, int sampleRate, int channels, int bitsPerSample) throws IOException {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.isFloat = (bitsPerSample == 32);
        this.dataSize = 0;

        raf = new RandomAccessFile(file, "rw");
        raf.setLength(0);
        writeHeader();
    }

    private void writeHeader() throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int formatTag = isFloat ? 3 : 1; // 3=IEEE float, 1=PCM

        ByteBuffer buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        // RIFF header
        buf.put("RIFF".getBytes());
        buf.putInt(36 + dataSize); // will patch
        buf.put("WAVE".getBytes());
        // fmt chunk
        buf.put("fmt ".getBytes());
        buf.putInt(16);
        buf.putShort((short) formatTag);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);
        // data chunk
        buf.put("data".getBytes());
        buf.putInt(dataSize); // will patch

        raf.seek(0);
        raf.write(buf.array());
    }

    public void write(float[] samples, int offset, int count) throws IOException {
        raf.seek(44 + dataSize);
        ByteBuffer buf = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN);
        if (isFloat) {
            for (int i = 0; i < count; i++) {
                buf.putFloat(samples[offset + i]);
            }
        } else if (bitsPerSample == 24) {
            for (int i = 0; i < count; i++) {
                int val = (int)(samples[offset + i] * 8388607f);
                buf.put((byte)(val & 0xFF));
                buf.put((byte)((val >> 8) & 0xFF));
                buf.put((byte)((val >> 16) & 0xFF));
            }
        } else { // 16-bit
            for (int i = 0; i < count; i++) {
                buf.putShort((short)(samples[offset + i] * 32767f));
            }
        }
        buf.flip();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        raf.write(bytes);
        dataSize += bytes.length;
    }

    public void writeShort(short[] samples, int offset, int count) throws IOException {
        raf.seek(44 + dataSize);
        byte[] bytes = new byte[count * 2];
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            buf.putShort(samples[offset + i]);
        }
        raf.write(bytes);
        dataSize += bytes.length;
    }

    public void close() throws IOException {
        if (raf != null) {
            writeHeader(); // patch sizes
            raf.close();
            raf = null;
        }
    }

    public int getBytesWritten() { return dataSize; }
}
