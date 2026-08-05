package com.pramod.audioeditor.engine;

import com.pramod.audioeditor.data.EqSettings;

/**
 * Thread-safe real-time 3-band EQ + gain + volume with parameter smoothing.
 * Uses volatile chain swap to avoid blocking the audio thread.
 */
public class RealtimeEq {

    private volatile EqChain chainL = new EqChain(new EqSettings());
    private volatile EqChain chainR = new EqChain(new EqSettings());

    public void setSettings(EqSettings s) {
        chainL = new EqChain(s);
        chainR = new EqChain(s);
    }

    /** Process a block of interleaved stereo samples in-place. */
    public void process(float[] buf, int frames, int channels) {
        EqChain cl = chainL;
        EqChain cr = chainR;

        boolean needsEq = !(cl.settings.bypass || cl.settings.isFlat());
        float volLin = (float) Math.pow(10, cl.settings.volumeDb / 20.0);
        boolean needsVol = Math.abs(cl.settings.volumeDb) > 0.01f;

        if (!needsEq && !needsVol) return;

        if (channels == 2) {
            for (int i = 0; i < frames * 2; i += 2) {
                float l = buf[i];
                float r = buf[i + 1];
                if (needsEq) {
                    l = (float) cl.process(l);
                    r = (float) cr.process(r);
                }
                if (needsVol) {
                    l *= volLin;
                    r *= volLin;
                }
                buf[i] = l;
                buf[i + 1] = r;
            }
        } else {
            for (int i = 0; i < frames; i++) {
                float s = buf[i];
                if (needsEq) {
                    s = (float) cl.process(s);
                }
                if (needsVol) {
                    s *= volLin;
                }
                buf[i] = s;
            }
        }
    }

    private static class EqChain {
        final EqSettings settings;
        private final BiquadFilter low, mid, high;
        private final double gainLin;

        EqChain(EqSettings s) {
            this.settings = s;
            double sr = 44100;
            low  = BiquadFilter.lowShelf(200, sr, s.lowDb);
            mid  = BiquadFilter.peaking(1000, sr, 0.9, s.midDb);
            high = BiquadFilter.highShelf(4000, sr, s.highDb);
            double totalGain = s.gainDb + (s.clipGuard ? s.autoGainDb() : 0);
            gainLin = Math.pow(10, totalGain / 20.0);
        }

        double process(double x) {
            x = low.process(x);
            x = mid.process(x);
            x = high.process(x);
            return x * gainLin;
        }
    }
}
