package com.pramod.audioeditor.engine;

/**
 * Transposed Direct Form II Biquad filter.
 * Used for EQ bands (low shelf, peaking, high shelf).
 */
public class BiquadFilter {

    private double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;

    public BiquadFilter(double b0, double b1, double b2, double a1, double a2) {
        this.b0 = b0; this.b1 = b1; this.b2 = b2;
        this.a1 = a1; this.a2 = a2;
    }

    public double process(double x) {
        double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x;
        y2 = y1; y1 = y;
        return y;
    }

    public void reset() { x1 = x2 = y1 = y2 = 0; }

    // ── Factory methods ──────────────────────────────────────────────────────

    public static BiquadFilter lowShelf(double freq, double sampleRate, double gainDb) {
        double A = Math.pow(10, gainDb / 40.0);
        double w0 = 2 * Math.PI * freq / sampleRate;
        double cosw = Math.cos(w0), sinw = Math.sin(w0);
        double alpha = sinw / 2.0 * Math.sqrt((A + 1.0 / A) * (1.0 / 0.707 - 1.0) + 2.0);
        double b0 = A * ((A + 1) - (A - 1) * cosw + 2 * Math.sqrt(A) * alpha);
        double b1 = 2 * A * ((A - 1) - (A + 1) * cosw);
        double b2 = A * ((A + 1) - (A - 1) * cosw - 2 * Math.sqrt(A) * alpha);
        double a0 = (A + 1) + (A - 1) * cosw + 2 * Math.sqrt(A) * alpha;
        double a1 = -2 * ((A - 1) + (A + 1) * cosw);
        double a2 = (A + 1) + (A - 1) * cosw - 2 * Math.sqrt(A) * alpha;
        return new BiquadFilter(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }

    public static BiquadFilter highShelf(double freq, double sampleRate, double gainDb) {
        double A = Math.pow(10, gainDb / 40.0);
        double w0 = 2 * Math.PI * freq / sampleRate;
        double cosw = Math.cos(w0), sinw = Math.sin(w0);
        double alpha = sinw / 2.0 * Math.sqrt((A + 1.0 / A) * (1.0 / 0.707 - 1.0) + 2.0);
        double b0 = A * ((A + 1) + (A - 1) * cosw + 2 * Math.sqrt(A) * alpha);
        double b1 = -2 * A * ((A - 1) + (A + 1) * cosw);
        double b2 = A * ((A + 1) + (A - 1) * cosw - 2 * Math.sqrt(A) * alpha);
        double a0 = (A + 1) - (A - 1) * cosw + 2 * Math.sqrt(A) * alpha;
        double a1 = 2 * ((A - 1) - (A + 1) * cosw);
        double a2 = (A + 1) - (A - 1) * cosw - 2 * Math.sqrt(A) * alpha;
        return new BiquadFilter(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }

    public static BiquadFilter peaking(double freq, double sampleRate, double Q, double gainDb) {
        double A = Math.pow(10, gainDb / 40.0);
        double w0 = 2 * Math.PI * freq / sampleRate;
        double cosw = Math.cos(w0), sinw = Math.sin(w0);
        double alpha = sinw / (2 * Q);
        double b0 = 1 + alpha * A;
        double b1 = -2 * cosw;
        double b2 = 1 - alpha * A;
        double a0 = 1 + alpha / A;
        double a1 = -2 * cosw;
        double a2 = 1 - alpha / A;
        return new BiquadFilter(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }
}
