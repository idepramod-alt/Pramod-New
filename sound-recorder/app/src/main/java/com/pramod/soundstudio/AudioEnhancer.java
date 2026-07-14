package com.pramod.soundstudio;

import java.io.File;
import java.io.IOException;

/**
 * Audio Enhancement Engine (pure Java DSP — no native libs required)
 *
 * Features:
 *  - 3-band Equalizer (Low / Mid / High) via biquad filters
 *  - Bass Boost (shelving filter)
 *  - Treble Boost (shelving filter)
 *  - Compressor / Limiter (envelope follower + gain reduction)
 *  - Peak Normalizer
 *  - DC Offset Removal (high-pass at ~5 Hz)
 *  - Simple Noise Reduction (spectral subtraction — single-pass)
 *  - De-Esser (dynamic high-frequency attenuator)
 *  - All operations are non-destructive: source file preserved
 */
public class AudioEnhancer {

    // ── Biquad filter ─────────────────────────────────────────────────────────

    /**
     * Transposed Direct Form II biquad filter.
     * One instance per channel per stage.
     */
    private static class Biquad {
        double b0, b1, b2, a1, a2;
        double z1 = 0, z2 = 0;

        Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0; this.b1 = b1; this.b2 = b2;
            this.a1 = a1; this.a2 = a2;
        }

        double process(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        void reset() { z1 = 0; z2 = 0; }
    }

    // ── Filter factories ──────────────────────────────────────────────────────

    /** Low-shelf biquad at cutoff frequency fc with gain dBGain. */
    private static Biquad lowShelf(double fc, double sampleRate, double dBGain) {
        double A  = Math.pow(10.0, dBGain / 40.0);
        double w0 = 2 * Math.PI * fc / sampleRate;
        double cosW0 = Math.cos(w0), sinW0 = Math.sin(w0);
        double S  = 1.0; // shelving slope (1 = max slope)
        double alpha = sinW0 / 2 * Math.sqrt((A + 1/A) * (1/S - 1) + 2);

        double b0 = A * ((A+1) - (A-1)*cosW0 + 2*Math.sqrt(A)*alpha);
        double b1 = 2*A * ((A-1) - (A+1)*cosW0);
        double b2 = A * ((A+1) - (A-1)*cosW0 - 2*Math.sqrt(A)*alpha);
        double a0 =      (A+1) + (A-1)*cosW0 + 2*Math.sqrt(A)*alpha;
        double a1 = -2 * ((A-1) + (A+1)*cosW0);
        double a2 =      (A+1) + (A-1)*cosW0 - 2*Math.sqrt(A)*alpha;
        return new Biquad(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }

    /** High-shelf biquad at cutoff frequency fc with gain dBGain. */
    private static Biquad highShelf(double fc, double sampleRate, double dBGain) {
        double A  = Math.pow(10.0, dBGain / 40.0);
        double w0 = 2 * Math.PI * fc / sampleRate;
        double cosW0 = Math.cos(w0), sinW0 = Math.sin(w0);
        double alpha = sinW0 / 2 * Math.sqrt((A + 1/A) * (1 - 1) + 2);

        double b0 = A * ((A+1) + (A-1)*cosW0 + 2*Math.sqrt(A)*alpha);
        double b1 = -2*A*((A-1) + (A+1)*cosW0);
        double b2 = A * ((A+1) + (A-1)*cosW0 - 2*Math.sqrt(A)*alpha);
        double a0 =      (A+1) - (A-1)*cosW0 + 2*Math.sqrt(A)*alpha;
        double a1 = 2  * ((A-1) - (A+1)*cosW0);
        double a2 =      (A+1) - (A-1)*cosW0 - 2*Math.sqrt(A)*alpha;
        return new Biquad(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }

    /** Peaking EQ biquad at center frequency fc, bandwidth Q, gain dBGain. */
    private static Biquad peakingEQ(double fc, double sampleRate, double Q, double dBGain) {
        double A  = Math.pow(10.0, dBGain / 40.0);
        double w0 = 2 * Math.PI * fc / sampleRate;
        double alpha = Math.sin(w0) / (2 * Q);

        double b0 =  1 + alpha * A;
        double b1 = -2 * Math.cos(w0);
        double b2 =  1 - alpha * A;
        double a0 =  1 + alpha / A;
        double a1 = -2 * Math.cos(w0);
        double a2 =  1 - alpha / A;
        return new Biquad(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }

    /** DC-blocking high-pass at ~5 Hz. */
    private static Biquad dcBlock(double sampleRate) {
        double fc = 5.0;
        double w0 = 2 * Math.PI * fc / sampleRate;
        double c  = Math.cos(w0), s = Math.sin(w0);
        double alpha = s / (2 * 0.707); // Q = 0.707 Butterworth
        double b0 = (1 + c) / 2, b1 = -(1 + c), b2 = (1 + c) / 2;
        double a0 = 1 + alpha, a1 = -2 * c, a2 = 1 - alpha;
        return new Biquad(b0/a0, b1/a0, b2/a0, a1/a0, a2/a0);
    }

    // ── Compressor / Limiter ──────────────────────────────────────────────────

    /**
     * Process samples with a feed-forward compressor/limiter.
     *
     * @param samples     PCM 16-bit (modified in-place after conversion)
     * @param sampleRate  sample rate
     * @param thresholdDB compression threshold (e.g. -18.0)
     * @param ratio       compression ratio (e.g. 4.0 for 4:1)
     * @param attackMs    attack time in ms (e.g. 5.0)
     * @param releaseMs   release time in ms (e.g. 100.0)
     * @param makeupGainDB makeup gain in dB (e.g. 6.0)
     * @param limit       if true, hard-clip output at 0 dBFS
     */
    public static double[] compress(double[] samples, int sampleRate,
                                    double thresholdDB, double ratio,
                                    double attackMs, double releaseMs,
                                    double makeupGainDB, boolean limit) {
        double attackCoeff  = Math.exp(-1.0 / (sampleRate * attackMs  / 1000.0));
        double releaseCoeff = Math.exp(-1.0 / (sampleRate * releaseMs / 1000.0));
        double makeupLinear = Math.pow(10.0, makeupGainDB / 20.0);
        double gainEnv      = 1.0;
        double[] out        = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            double xAbs  = Math.abs(samples[i]);
            double xDB   = xAbs > 1e-10 ? 20 * Math.log10(xAbs) : -120.0;
            double gainDB;
            if (xDB > thresholdDB) {
                gainDB = thresholdDB + (xDB - thresholdDB) / ratio - xDB;
            } else {
                gainDB = 0.0;
            }
            double gainLin  = Math.pow(10.0, gainDB / 20.0);
            gainEnv = (gainLin < gainEnv)
                ? attackCoeff  * gainEnv + (1 - attackCoeff)  * gainLin
                : releaseCoeff * gainEnv + (1 - releaseCoeff) * gainLin;

            double y = samples[i] * gainEnv * makeupLinear;
            if (limit) y = Math.max(-1.0, Math.min(1.0, y));
            out[i] = y;
        }
        return out;
    }

    // ── Normalizer ────────────────────────────────────────────────────────────

    /**
     * Normalize samples to a target peak level in dBFS.
     * @param targetDB  desired peak level (e.g. -1.0 for -1 dBFS)
     */
    public static double[] normalize(double[] samples, double targetDB) {
        double peak = 0;
        for (double s : samples) peak = Math.max(peak, Math.abs(s));
        if (peak < 1e-10) return samples.clone();
        double targetLinear = Math.pow(10.0, targetDB / 20.0);
        double gain         = targetLinear / peak;
        double[] out        = new double[samples.length];
        for (int i = 0; i < samples.length; i++) out[i] = samples[i] * gain;
        return out;
    }

    // ── Simple Noise Reduction (spectral subtraction) ─────────────────────────

    /**
     * One-pass noise reduction via simple spectral subtraction.
     * Takes the first noiseProfileMs milliseconds as the noise profile.
     *
     * @param samples          PCM double[] normalised -1..1
     * @param sampleRate       sample rate
     * @param noiseProfileMs   how many ms at the start to use as noise profile
     * @param strength         0.0–1.0, how aggressively to subtract noise (0.5 recommended)
     */
    public static double[] reduceNoise(double[] samples, int sampleRate,
                                        int noiseProfileMs, float strength) {
        int fftSize = 1024;
        int hop     = fftSize / 2;

        // Build noise magnitude profile from the first N frames
        int profileSamples = noiseProfileMs * sampleRate / 1000;
        double[] noiseMag  = new double[fftSize / 2 + 1];
        int profileFrames  = 0;

        for (int pos = 0; pos + fftSize <= profileSamples && pos + fftSize <= samples.length; pos += hop) {
            double[] frame = applyHann(samples, pos, fftSize);
            double[] mag   = magnitudeSpectrum(frame);
            for (int k = 0; k < mag.length; k++) noiseMag[k] += mag[k];
            profileFrames++;
        }
        if (profileFrames > 0)
            for (int k = 0; k < noiseMag.length; k++) noiseMag[k] /= profileFrames;

        // Spectral subtraction pass
        double[] output  = new double[samples.length];
        double[] window  = new double[fftSize];
        int      overlap = 0;

        for (int pos = 0; pos + fftSize <= samples.length; pos += hop) {
            double[] frame     = applyHann(samples, pos, fftSize);
            double[] real      = frame;
            double[] imag      = new double[fftSize];
            fft(real, imag, false);

            // Compute magnitude and phase
            for (int k = 0; k <= fftSize / 2; k++) {
                double mag = Math.sqrt(real[k] * real[k] + imag[k] * imag[k]);
                double phs = Math.atan2(imag[k], real[k]);
                // Spectral subtraction: subtract scaled noise magnitude
                double newMag = Math.max(0.01 * mag, mag - strength * noiseMag[Math.min(k, noiseMag.length-1)]);
                real[k] = newMag * Math.cos(phs);
                imag[k] = newMag * Math.sin(phs);
                if (k > 0 && k < fftSize / 2) {
                    real[fftSize - k] = real[k];
                    imag[fftSize - k] = -imag[k];
                }
            }

            fft(real, imag, true);

            // Overlap-add
            for (int i = 0; i < fftSize && pos + i < output.length; i++) {
                output[pos + i] += real[i] / fftSize;
            }
        }
        return output;
    }

    // ── Full enhancement pipeline ─────────────────────────────────────────────

    /**
     * Process a WAV file through the enhancement pipeline and save result.
     *
     * @param input      source WAV
     * @param outputDir  output directory
     * @param cfg        enhancement config
     * @return output file
     */
    public File enhance(File input, File outputDir, EnhancementConfig cfg) throws IOException {
        int[]   header     = AudioTrimmer.readWavHeader(input);
        int     sampleRate = header[0];
        int     channels   = header[1];
        short[] pcm        = AudioTrimmer.readWavPcm(input);

        // Convert to double[] normalized -1..1
        double[] samples = new double[pcm.length];
        for (int i = 0; i < pcm.length; i++) samples[i] = pcm[i] / 32768.0;

        // 1. DC offset removal
        if (cfg.removeDcOffset) {
            Biquad dc = dcBlock(sampleRate);
            for (int i = 0; i < samples.length; i++) samples[i] = dc.process(samples[i]);
        }

        // 2. Noise reduction
        if (cfg.noiseReductionStrength > 0) {
            samples = reduceNoise(samples, sampleRate, 200, cfg.noiseReductionStrength);
        }

        // 3. EQ: low, mid, high bands
        if (cfg.eqLowGainDB != 0) {
            Biquad eq = lowShelf(200, sampleRate, cfg.eqLowGainDB);
            for (int i = 0; i < samples.length; i++) samples[i] = eq.process(samples[i]);
        }
        if (cfg.eqMidGainDB != 0) {
            Biquad eq = peakingEQ(1000, sampleRate, 1.0, cfg.eqMidGainDB);
            for (int i = 0; i < samples.length; i++) samples[i] = eq.process(samples[i]);
        }
        if (cfg.eqHighGainDB != 0) {
            Biquad eq = highShelf(4000, sampleRate, cfg.eqHighGainDB);
            for (int i = 0; i < samples.length; i++) samples[i] = eq.process(samples[i]);
        }

        // 4. Bass / Treble boost (additional shelf)
        if (cfg.bassBoostDB != 0) {
            Biquad bb = lowShelf(100, sampleRate, cfg.bassBoostDB);
            for (int i = 0; i < samples.length; i++) samples[i] = bb.process(samples[i]);
        }
        if (cfg.trebleBoostDB != 0) {
            Biquad tb = highShelf(8000, sampleRate, cfg.trebleBoostDB);
            for (int i = 0; i < samples.length; i++) samples[i] = tb.process(samples[i]);
        }

        // 5. De-Esser (dynamic high-cut above 6 kHz)
        if (cfg.deEsser) {
            samples = deEss(samples, sampleRate);
        }

        // 6. Compressor
        if (cfg.compressionRatio > 1.0) {
            samples = compress(samples, sampleRate,
                    cfg.compressionThresholdDB, cfg.compressionRatio,
                    cfg.attackMs, cfg.releaseMs, cfg.makeupGainDB, false);
        }

        // 7. Limiter (hard clip guard at -0.1 dBFS)
        if (cfg.limiter) {
            samples = compress(samples, sampleRate, -0.1, 100.0, 0.1, 50.0, 0.0, true);
        }

        // 8. Normalize
        if (cfg.normalize) {
            samples = normalize(samples, cfg.normalizeTargetDB);
        }

        // Convert back to short[]
        short[] outPcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            outPcm[i] = (short) Math.max(-32768, Math.min(32767, (int)(samples[i] * 32767)));
        }

        String outName = input.getName().replace(".wav", "_enhanced.wav");
        File   outFile = new File(outputDir, outName);
        AudioTrimmer.writePcmToWav(outPcm, sampleRate, channels, outFile);
        return outFile;
    }

    // ── De-Esser ──────────────────────────────────────────────────────────────

    private double[] deEss(double[] samples, int sampleRate) {
        // Sidechain: high-shelf detect > threshold → apply high-cut
        Biquad detect = highShelf(6000, sampleRate, 0);   // flat detect path
        Biquad cut    = highShelf(6000, sampleRate, -8.0); // 8dB cut when triggered
        Biquad flat   = highShelf(6000, sampleRate, 0);
        double env    = 0;
        double attack  = Math.exp(-1.0 / (sampleRate * 0.002));
        double release = Math.exp(-1.0 / (sampleRate * 0.05));
        double[] out   = new double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            double x   = samples[i];
            double det = Math.abs(detect.process(x));
            env = det > env ? attack * env + (1-attack) * det : release * env + (1-release) * det;
            // Blend between flat and cut based on envelope > threshold
            double t = Math.min(1.0, Math.max(0.0, (env - 0.1) / 0.1));
            double flat_out = flat.process(x);
            double cut_out  = cut.process(x);
            out[i] = flat_out * (1 - t) + cut_out * t;
        }
        return out;
    }

    // ── FFT utilities (Cooley-Tukey in-place, power-of-2) ────────────────────

    private static void fft(double[] real, double[] imag, boolean inverse) {
        int n = real.length;
        if (n == 1) return;
        // Bit-reversal permutation
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = real[i]; real[i] = real[j]; real[j] = t;
                          t = imag[i]; imag[i] = imag[j]; imag[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curRe = 1, curIm = 0;
                for (int j = 0; j < len / 2; j++) {
                    double uRe = real[i+j], uIm = imag[i+j];
                    double vRe = real[i+j+len/2]*curRe - imag[i+j+len/2]*curIm;
                    double vIm = real[i+j+len/2]*curIm + imag[i+j+len/2]*curRe;
                    real[i+j] = uRe+vRe; imag[i+j] = uIm+vIm;
                    real[i+j+len/2] = uRe-vRe; imag[i+j+len/2] = uIm-vIm;
                    double newRe = curRe*wRe - curIm*wIm;
                    curIm = curRe*wIm + curIm*wRe; curRe = newRe;
                }
            }
        }
    }

    private static double[] applyHann(double[] samples, int offset, int size) {
        double[] frame = new double[size];
        for (int i = 0; i < size && offset + i < samples.length; i++) {
            double w = 0.5 * (1 - Math.cos(2 * Math.PI * i / (size - 1)));
            frame[i] = samples[offset + i] * w;
        }
        return frame;
    }

    private static double[] magnitudeSpectrum(double[] frame) {
        int n = frame.length;
        double[] real = frame.clone();
        double[] imag = new double[n];
        fft(real, imag, false);
        double[] mag = new double[n / 2 + 1];
        for (int k = 0; k < mag.length; k++)
            mag[k] = Math.sqrt(real[k] * real[k] + imag[k] * imag[k]);
        return mag;
    }

    // ── Config ────────────────────────────────────────────────────────────────

    public static class EnhancementConfig {
        // EQ
        public float eqLowGainDB  = 0;   // -12 to +12 dB
        public float eqMidGainDB  = 0;
        public float eqHighGainDB = 0;
        public float bassBoostDB  = 0;
        public float trebleBoostDB = 0;
        // Dynamics
        public double compressionThresholdDB = -18.0;
        public double compressionRatio       = 1.0;   // 1 = bypass
        public double attackMs               = 5.0;
        public double releaseMs              = 100.0;
        public double makeupGainDB           = 0.0;
        public boolean limiter               = false;
        // Enhancement
        public boolean removeDcOffset              = true;
        public float   noiseReductionStrength      = 0.0f;  // 0 = off
        public boolean deEsser                     = false;
        public boolean normalize                   = false;
        public double  normalizeTargetDB           = -1.0;
    }
}
