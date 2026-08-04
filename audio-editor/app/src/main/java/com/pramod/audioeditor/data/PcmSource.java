package com.pramod.audioeditor.data;

/**
 * Read-only frame window accessor for PCM audio data.
 * All DSP reads go through this interface for thread safety.
 */
public interface PcmSource {
    int sampleRate();
    int channels();
    long totalFrames();

    /**
     * Read frames from the source into the output buffer.
     * @param startFrame first frame to read (0-based)
     * @param numFrames number of frames to read
     * @param out output float array (interleaved if stereo)
     * @param outOffset offset in output array
     * @param toMono if true and source is stereo, mix down to mono
     */
    void readFrames(long startFrame, int numFrames, float[] out, int outOffset, boolean toMono);

    void release();
}
