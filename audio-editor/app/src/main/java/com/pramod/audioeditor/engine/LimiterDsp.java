package com.pramod.audioeditor.engine;

/**
 * Soft-knee envelope-follower limiter for clipping prevention.
 */
public class LimiterDsp {

    private float thresholdLin;
    private float attackCoeff;
    private float releaseCoeff;
    private float envelope = 0;

    public void configure(float thresholdDb, float attackMs, float releaseMs, int sampleRate) {
        thresholdLin = (float) Math.pow(10, thresholdDb / 20.0);
        attackCoeff  = (float) Math.exp(-1.0 / (attackMs * sampleRate / 1000.0));
        releaseCoeff = (float) Math.exp(-1.0 / (releaseMs * sampleRate / 1000.0));
    }

    public void process(float[] buf, int frames, int channels) {
        for (int i = 0; i < frames * channels; i++) {
            float abs = Math.abs(buf[i]);
            if (abs > envelope) {
                envelope = attackCoeff * envelope + (1 - attackCoeff) * abs;
            } else {
                envelope = releaseCoeff * envelope + (1 - releaseCoeff) * abs;
            }
            if (envelope > thresholdLin) {
                float gain = thresholdLin / envelope;
                buf[i] *= gain;
            }
        }
    }

    public void reset() { envelope = 0; }
}
