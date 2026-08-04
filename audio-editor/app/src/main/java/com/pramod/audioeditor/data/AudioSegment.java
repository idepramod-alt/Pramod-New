package com.pramod.audioeditor.data;

/**
 * One split segment.
 */
public class AudioSegment {
    public final int index;
    public final long startFrame;
    public final long endFrame;

    public AudioSegment(int index, long startFrame, long endFrame) {
        this.index = index;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
    }

    public String name() { return "Part " + (index + 1); }
    public long lengthFrames() { return endFrame - startFrame; }
    public double durationSec(int sampleRate) { return lengthFrames() / (double) sampleRate; }
}
