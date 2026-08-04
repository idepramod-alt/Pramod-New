package com.pramod.audioeditor.data;

/**
 * Export parameters.
 */
public class ExportSettings {
    public enum Format { WAV, MP3 }

    public Format format = Format.WAV;
    public int sampleRate = 44100;
    public int bitDepth = 16;        // WAV: 16, 24, 32 (float)
    public int mp3Bitrate = 192;     // MP3: 128, 192, 256, 320 kbps
    public boolean exportSelection = false; // true = only selected region

    public static final int[] SAMPLE_RATES = {22050, 32000, 44100, 48000};
    public static final int[] BIT_DEPTHS = {16, 24, 32};
    public static final int[] MP3_BITRATES = {128, 192, 256, 320};
}
