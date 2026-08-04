package com.pramod.audioeditor.engine;

/**
 * Click-free fades and crossfades for cut-free editing.
 */
public class ClipFade {

    /**
     * Apply fade-in and/or fade-out to a buffer.
     * @param fadeIn true to apply fade-in
     * @param fadeOut true to apply fade-out
     * @param fadeSamples number of samples for each fade
     */
    public static void applyBoundaryFades(float[] buf, int totalSamples, boolean fadeIn, boolean fadeOut, int fadeSamples) {
        if (fadeIn) {
            int n = Math.min(fadeSamples, totalSamples);
            for (int i = 0; i < n; i++) {
                float t = (float) i / n;
                float gain = t * t * (3 - 2 * t); // smoothstep
                buf[i] *= gain;
            }
        }
        if (fadeOut) {
            int n = Math.min(fadeSamples, totalSamples);
            for (int i = 0; i < n; i++) {
                float t = (float) i / n;
                float gain = 1f - t * t * (3 - 2 * t);
                buf[totalSamples - 1 - i] *= gain;
            }
        }
    }

    /**
     * Equal-power crossfade at a splice point.
     * Blends the tail of segment A with the head of segment B.
     */
    public static void applyCrossfade(float[] a, int aLen, float[] b, int bStart, int fadeSamples) {
        int n = Math.min(fadeSamples, Math.min(aLen, b.length - bStart));
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float gainA = (float) Math.cos(t * Math.PI / 2);
            float gainB = (float) Math.sin(t * Math.PI / 2);
            int aIdx = aLen - n + i;
            if (aIdx >= 0 && aIdx < a.length) {
                a[aIdx] = a[aIdx] * gainA + b[bStart + i] * gainB;
            }
        }
    }
}
