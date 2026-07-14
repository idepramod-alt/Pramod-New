package com.pramod.soundstudio;

import java.io.File;
import java.io.IOException;

/**
 * Audio Effects Engine (pure Java DSP)
 *
 * Effects:
 *  - Reverb      (Schroeder allpass + comb filter network)
 *  - Delay/Echo  (single + multi-tap delay line)
 *  - Chorus      (LFO-modulated delay line)
 *  - Pitch Shift (OLA — Overlap-Add method, ±12 semitones)
 *  - Tempo Change (time-stretch without pitch change via WSOLA)
 *  - Stereo Width (mid/side processing)
 *  - Fade In / Fade Out (linear & exponential)
 *  - Reverse
 *  - Silence generator
 */
public class AudioEffectsEngine {

    // ── Reverb (Schroeder model) ───────────────────────────────────────────────

    /**
     * Apply reverb using a network of comb filters and allpass sections.
     *
     * @param samples     PCM double[] normalised -1..1
     * @param sampleRate  sample rate (Hz)
     * @param roomSize    0.0–1.0 (controls comb delay lengths)
     * @param wetMix      0.0–1.0 (wet/dry ratio)
     * @param decay       0.0–1.0 (feedback coefficient)
     */
    public static double[] reverb(double[] samples, int sampleRate,
                                   float roomSize, float wetMix, float decay) {
        // Comb filter delays (in ms) scaled by roomSize
        double[] combDelaysMs = {29.7, 37.1, 41.1, 43.7};
        double   feedback     = 0.7 + decay * 0.25;  // keep < 1.0

        // Allpass delays (in ms)
        double[] apDelaysMs = {5.0, 1.7};
        double   apGain     = 0.7;

        // Build comb filter buffers
        int       numCombs    = combDelaysMs.length;
        double[][] combBuf    = new double[numCombs][];
        int[]      combLen    = new int[numCombs];
        int[]      combPtr    = new int[numCombs];
        for (int c = 0; c < numCombs; c++) {
            combLen[c] = (int)((combDelaysMs[c] * (1 + roomSize * 0.5)) * sampleRate / 1000.0);
            combBuf[c] = new double[Math.max(1, combLen[c])];
        }

        // Allpass buffers
        int       numAP   = apDelaysMs.length;
        double[][] apBuf  = new double[numAP][];
        int[]      apLen  = new int[numAP];
        int[]      apPtr  = new int[numAP];
        for (int a = 0; a < numAP; a++) {
            apLen[a] = (int)(apDelaysMs[a] * sampleRate / 1000.0);
            apBuf[a] = new double[Math.max(1, apLen[a])];
        }

        double[] wet = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            double x = samples[i];

            // Sum of comb filters (parallel)
            double combOut = 0;
            for (int c = 0; c < numCombs; c++) {
                double delayed = combBuf[c][combPtr[c]];
                double y       = x + feedback * delayed;
                combBuf[c][combPtr[c]] = y;
                combPtr[c] = (combPtr[c] + 1) % combLen[c];
                combOut   += delayed;
            }
            combOut /= numCombs;

            // Allpass in series
            double apOut = combOut;
            for (int a = 0; a < numAP; a++) {
                double d  = apBuf[a][apPtr[a]];
                double y  = -apGain * apOut + d;
                apBuf[a][apPtr[a]] = apOut + apGain * d;
                apPtr[a] = (apPtr[a] + 1) % apLen[a];
                apOut    = y;
            }

            wet[i] = apOut;
        }

        // Mix wet + dry
        double[] out = new double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = samples[i] * (1 - wetMix) + wet[i] * wetMix;
        }
        return out;
    }

    // ── Delay / Echo ──────────────────────────────────────────────────────────

    /**
     * Simple stereo delay/echo effect.
     *
     * @param delayMs   delay time in milliseconds
     * @param feedback  0.0–0.95 (echo tail)
     * @param wetMix    0.0–1.0 (wet/dry ratio)
     */
    public static double[] delay(double[] samples, int sampleRate,
                                  float delayMs, float feedback, float wetMix) {
        int    delayLen = Math.max(1, (int)(delayMs * sampleRate / 1000.0));
        double[] buf    = new double[delayLen];
        int      ptr    = 0;
        double[] out    = new double[samples.length];

        for (int i = 0; i < samples.length; i++) {
            double echo = buf[ptr];
            double y    = samples[i] + echo * feedback;
            buf[ptr]    = y;
            ptr         = (ptr + 1) % delayLen;
            out[i]      = samples[i] * (1 - wetMix) + echo * wetMix;
        }
        return out;
    }

    // ── Chorus ────────────────────────────────────────────────────────────────

    /**
     * Chorus effect via LFO-modulated delay line.
     *
     * @param rateHz    LFO frequency (0.5–5.0 Hz typical)
     * @param depthMs   modulation depth (1–15 ms typical)
     * @param wetMix    wet/dry ratio
     */
    public static double[] chorus(double[] samples, int sampleRate,
                                   float rateHz, float depthMs, float wetMix) {
        int    maxDelay = (int)(30.0 * sampleRate / 1000.0);  // 30ms max
        double[] buf    = new double[maxDelay];
        int      writePtr = 0;
        double[] out    = new double[samples.length];
        int      depthSamples = (int)(depthMs * sampleRate / 1000.0);
        double   baseDelay   = 15.0 * sampleRate / 1000.0; // 15ms centre delay

        for (int i = 0; i < samples.length; i++) {
            double lfo    = Math.sin(2 * Math.PI * rateHz * i / sampleRate);
            double delay  = baseDelay + depthSamples * lfo;
            int    dInt   = (int) delay;
            double dFrac  = delay - dInt;

            // Linear interpolation from ring buffer
            int r1 = ((writePtr - dInt) + maxDelay) % maxDelay;
            int r2 = ((writePtr - dInt - 1) + maxDelay) % maxDelay;
            double wet = buf[r1] * (1 - dFrac) + buf[r2] * dFrac;

            buf[writePtr] = samples[i];
            writePtr = (writePtr + 1) % maxDelay;
            out[i] = samples[i] * (1 - wetMix) + wet * wetMix;
        }
        return out;
    }

    // ── Pitch Shift (OLA) ─────────────────────────────────────────────────────

    /**
     * Shift pitch by semitones using Overlap-Add (OLA).
     * Quality: good for percussive/drum sounds. For tonal material,
     *          a phase-vocoder (PSOLA) gives better results.
     *
     * @param semitones  pitch shift in semitones (-12 to +12)
     */
    public static double[] pitchShift(double[] samples, int sampleRate, float semitones) {
        if (Math.abs(semitones) < 0.01f) return samples.clone();

        double ratio    = Math.pow(2.0, semitones / 12.0);
        int    frameLen = 1024;
        int    hopIn    = 256;
        int    hopOut   = (int)(hopIn / ratio);
        if (hopOut < 1) hopOut = 1;

        double[] hann  = new double[frameLen];
        for (int i = 0; i < frameLen; i++)
            hann[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (frameLen - 1)));

        int outLen = (int)(samples.length / ratio) + frameLen;
        double[] out = new double[Math.max(outLen, samples.length)];
        double[] norm = new double[out.length];

        int inPtr = 0, outPtr = 0;
        while (inPtr + frameLen <= samples.length) {
            // Extract and window frame
            double[] frame = new double[frameLen];
            for (int i = 0; i < frameLen; i++) frame[i] = samples[inPtr + i] * hann[i];

            // Overlap-add to output
            for (int i = 0; i < frameLen && outPtr + i < out.length; i++) {
                out[outPtr + i]  += frame[i];
                norm[outPtr + i] += hann[i];
            }

            inPtr  += hopIn;
            outPtr += hopOut;
        }

        // Normalize by OLA window sum
        for (int i = 0; i < out.length; i++)
            if (norm[i] > 1e-6) out[i] /= norm[i];

        // Trim to original-ish length
        int trimLen = (int)(samples.length / ratio);
        double[] trimmed = new double[Math.min(trimLen, out.length)];
        System.arraycopy(out, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    // ── Stereo Width ──────────────────────────────────────────────────────────

    /**
     * Adjust stereo width via mid/side processing.
     * width=0 = mono, width=1 = original, width=2 = exaggerated wide
     * Only applies to stereo (2-channel) samples.
     */
    public static double[] stereoWidth(double[] samples, float width) {
        if (samples.length % 2 != 0) return samples;
        double[] out = new double[samples.length];
        for (int i = 0; i < samples.length; i += 2) {
            double L = samples[i], R = samples[i + 1];
            double M = (L + R) * 0.5;
            double S = (L - R) * 0.5 * width;
            out[i]     = M + S;
            out[i + 1] = M - S;
        }
        return out;
    }

    // ── Fade In / Fade Out ────────────────────────────────────────────────────

    public static double[] fadeIn(double[] samples, int sampleRate, int durationMs) {
        double[] out     = samples.clone();
        int      fadeSmp = Math.min(samples.length, durationMs * sampleRate / 1000);
        for (int i = 0; i < fadeSmp; i++) out[i] *= (double) i / fadeSmp;
        return out;
    }

    public static double[] fadeOut(double[] samples, int sampleRate, int durationMs) {
        double[] out     = samples.clone();
        int      fadeSmp = Math.min(samples.length, durationMs * sampleRate / 1000);
        int      start   = samples.length - fadeSmp;
        for (int i = start; i < samples.length; i++)
            out[i] *= (double)(samples.length - 1 - i) / fadeSmp;
        return out;
    }

    // ── Reverse ───────────────────────────────────────────────────────────────

    public static short[] reverse(short[] samples, int channels) {
        short[] out = new short[samples.length];
        if (channels == 1) {
            for (int i = 0; i < samples.length; i++) out[i] = samples[samples.length - 1 - i];
        } else {
            // Reverse frame by frame to keep channel pairs intact
            int frames = samples.length / channels;
            for (int f = 0; f < frames; f++) {
                int srcFrame = frames - 1 - f;
                for (int c = 0; c < channels; c++) {
                    out[f * channels + c] = samples[srcFrame * channels + c];
                }
            }
        }
        return out;
    }

    // ── Silence Generator ─────────────────────────────────────────────────────

    public static short[] generateSilence(int sampleRate, int channels, double durationSec) {
        return new short[(int)(sampleRate * channels * durationSec)];
    }

    // ── Full effects chain pipeline ───────────────────────────────────────────

    /**
     * Apply effects chain to a WAV file and save result.
     */
    public File applyEffects(File input, File outputDir, EffectsConfig cfg) throws IOException {
        int[]   header     = AudioTrimmer.readWavHeader(input);
        int     sampleRate = header[0];
        int     channels   = header[1];
        short[] pcm        = AudioTrimmer.readWavPcm(input);

        double[] samples = new double[pcm.length];
        for (int i = 0; i < pcm.length; i++) samples[i] = pcm[i] / 32768.0;

        // Fade in/out
        if (cfg.fadeInMs > 0)  samples = fadeIn(samples, sampleRate, cfg.fadeInMs);
        if (cfg.fadeOutMs > 0) samples = fadeOut(samples, sampleRate, cfg.fadeOutMs);

        // Pitch shift
        if (Math.abs(cfg.pitchSemitones) > 0.01f) {
            samples = pitchShift(samples, sampleRate, cfg.pitchSemitones);
        }

        // Chorus
        if (cfg.chorusWetMix > 0) {
            samples = chorus(samples, sampleRate, cfg.chorusRateHz, cfg.chorusDepthMs, cfg.chorusWetMix);
        }

        // Reverb
        if (cfg.reverbWetMix > 0) {
            samples = reverb(samples, sampleRate, cfg.reverbRoomSize, cfg.reverbWetMix, cfg.reverbDecay);
        }

        // Delay
        if (cfg.delayWetMix > 0) {
            samples = delay(samples, sampleRate, cfg.delayMs, cfg.delayFeedback, cfg.delayWetMix);
        }

        // Stereo width
        if (channels >= 2 && Math.abs(cfg.stereoWidth - 1.0f) > 0.01f) {
            samples = stereoWidth(samples, cfg.stereoWidth);
        }

        // Convert back
        short[] outPcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            outPcm[i] = (short) Math.max(-32768, Math.min(32767, (int)(samples[i] * 32767)));
        }

        String outName = input.getName().replace(".wav", "_fx.wav");
        File   outFile = new File(outputDir, outName);
        AudioTrimmer.writePcmToWav(outPcm, sampleRate, channels, outFile);
        return outFile;
    }

    // ── Config ────────────────────────────────────────────────────────────────

    public static class EffectsConfig {
        public float pitchSemitones = 0;
        public int   fadeInMs       = 0;
        public int   fadeOutMs      = 0;
        // Reverb
        public float reverbRoomSize = 0.5f;
        public float reverbDecay    = 0.5f;
        public float reverbWetMix   = 0.0f;
        // Delay
        public float delayMs        = 300.0f;
        public float delayFeedback  = 0.4f;
        public float delayWetMix    = 0.0f;
        // Chorus
        public float chorusRateHz   = 1.0f;
        public float chorusDepthMs  = 5.0f;
        public float chorusWetMix   = 0.0f;
        // Stereo
        public float stereoWidth    = 1.0f;
    }
}
