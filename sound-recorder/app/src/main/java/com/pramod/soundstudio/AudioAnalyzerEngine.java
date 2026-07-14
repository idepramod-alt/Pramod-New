package com.pramod.soundstudio;

import java.io.File;
import java.io.IOException;

/**
 * Audio Analysis Engine
 *
 * Features:
 *  - BPM Detection (autocorrelation + onset-based beat tracking)
 *  - Peak Level measurement (sample-accurate)
 *  - RMS Level measurement
 *  - Approximate LUFS (integrated loudness, per ITU-R BS.1770-4)
 *  - Frequency Spectrum (FFT magnitude)
 *  - Stereo / Mono detection
 *  - Sample Rate and Bit Depth info (from WAV header)
 *  - Dynamic Range (peak – RMS in dB)
 *  - DC Offset detection
 *  - Clipping detection (samples at ±32767)
 */
public class AudioAnalyzerEngine {

    // ── Full analysis ─────────────────────────────────────────────────────────

    /**
     * Analyze a WAV file and return full AnalysisResult.
     */
    public AnalysisResult analyze(File wavFile) throws IOException {
        int[]   header     = AudioTrimmer.readWavHeader(wavFile);
        int     sampleRate = header[0];
        int     channels   = header[1];
        int     bitDepth   = header[2];
        short[] samples    = AudioTrimmer.readWavPcm(wavFile);

        AnalysisResult r = new AnalysisResult();
        r.sampleRate = sampleRate;
        r.channels   = channels;
        r.bitDepth   = bitDepth;
        r.isStereo   = channels >= 2;
        r.durationSec = (double) samples.length / (sampleRate * channels);
        r.fileSizeBytes = wavFile.length();

        // Convert to doubles -1..1
        double[] dbl = new double[samples.length];
        for (int i = 0; i < samples.length; i++) dbl[i] = samples[i] / 32768.0;

        // Peak level
        double peak = 0;
        for (double v : dbl) peak = Math.max(peak, Math.abs(v));
        r.peakDB = peak > 1e-10 ? 20 * Math.log10(peak) : -120.0;

        // RMS
        double sumSq = 0;
        for (double v : dbl) sumSq += v * v;
        double rms = Math.sqrt(sumSq / dbl.length);
        r.rmsDB = rms > 1e-10 ? 20 * Math.log10(rms) : -120.0;

        // Dynamic range
        r.dynamicRangeDB = r.peakDB - r.rmsDB;

        // DC offset
        double dcSum = 0;
        for (double v : dbl) dcSum += v;
        r.dcOffset = dcSum / dbl.length;

        // Clipping count
        int clips = 0;
        for (short s : samples) if (Math.abs(s) >= 32767) clips++;
        r.clippedSamples = clips;

        // LUFS (simplified ITU-R BS.1770 without inter-sample peak)
        r.integratedLUFS = computeLUFS(dbl, sampleRate, channels);

        // Frequency spectrum (FFT of center 4096 samples)
        r.frequencySpectrum = computeSpectrum(dbl, sampleRate);

        // BPM (mono mix or first channel)
        double[] mono = channels > 1 ? toMono(dbl, channels) : dbl;
        r.bpm = detectBPM(mono, sampleRate);

        return r;
    }

    // ── BPM Detection ─────────────────────────────────────────────────────────

    /**
     * Detect BPM using onset-strength envelope + autocorrelation.
     * Effective range: 60–200 BPM. Returns 0 if not confident.
     */
    public double detectBPM(double[] mono, int sampleRate) {
        // Step 1: Compute onset strength envelope using RMS difference
        int hopSize   = 512;
        int numFrames = (mono.length - hopSize) / hopSize;
        if (numFrames < 4) return 0;

        double[] onset = new double[numFrames];
        double prevRms = 0;
        for (int f = 0; f < numFrames; f++) {
            int from = f * hopSize;
            int to   = Math.min(from + hopSize, mono.length);
            double s = 0;
            for (int i = from; i < to; i++) s += mono[i] * mono[i];
            double curRms = Math.sqrt(s / (to - from));
            onset[f]  = Math.max(0, curRms - prevRms);
            prevRms   = curRms;
        }

        // Step 2: Autocorrelation of onset envelope
        double frameRate = (double) sampleRate / hopSize; // frames per second
        int minLag = (int)(frameRate * 60.0 / 200.0); // 200 BPM
        int maxLag = (int)(frameRate * 60.0 / 60.0);  // 60 BPM
        if (minLag < 1) minLag = 1;
        if (maxLag >= numFrames) maxLag = numFrames - 1;

        double bestCorr = -1;
        int    bestLag  = minLag;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double corr = 0;
            for (int i = 0; i + lag < numFrames; i++) corr += onset[i] * onset[i + lag];
            if (corr > bestCorr) { bestCorr = corr; bestLag = lag; }
        }

        double bpm = frameRate * 60.0 / bestLag;

        // Snap to nearest 0.5 BPM
        bpm = Math.round(bpm * 2.0) / 2.0;

        // Sanity check
        return (bpm >= 60 && bpm <= 200) ? bpm : 0;
    }

    // ── LUFS (simplified BS.1770-4 integrated) ────────────────────────────────

    private double computeLUFS(double[] samples, int sampleRate, int channels) {
        // K-weighting: high-shelf pre-filter + high-pass
        // For mobile simplicity: approximate with RMS * K-weighting correction factor (~-0.7 dB)
        // Full implementation would use 400ms blocks gated at -70 LUFS.
        int    blockSize    = (int)(0.4 * sampleRate * channels); // 400ms
        double sumLoudness  = 0;
        int    numBlocks    = 0;

        for (int pos = 0; pos + blockSize <= samples.length; pos += blockSize / 4) {
            double blockSumSq = 0;
            for (int i = pos; i < pos + blockSize; i++) blockSumSq += samples[i] * samples[i];
            double blockRms = Math.sqrt(blockSumSq / blockSize);
            double lufsBlock = blockRms > 1e-10 ? -0.691 + 10 * Math.log10(blockRms * blockRms) : -120;
            if (lufsBlock > -70) { // gating
                sumLoudness += blockRms * blockRms;
                numBlocks++;
            }
        }
        if (numBlocks == 0) return -120.0;
        return -0.691 + 10 * Math.log10(sumLoudness / numBlocks);
    }

    // ── Frequency Spectrum ────────────────────────────────────────────────────

    /**
     * Compute a 512-point magnitude spectrum from the center of the audio.
     * Returns array of [freq_hz, magnitude_db] pairs, length = 256.
     */
    public double[][] computeSpectrum(double[] samples, int sampleRate) {
        int fftSize = 1024;
        int start   = Math.max(0, samples.length / 2 - fftSize / 2);
        double[] frame = new double[fftSize];
        for (int i = 0; i < fftSize && start + i < samples.length; i++) {
            double w = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (fftSize - 1)); // Hamming
            frame[i] = samples[start + i] * w;
        }

        double[] real = frame, imag = new double[fftSize];
        fft(real, imag, false);

        int bins = fftSize / 2;
        double[][] spectrum = new double[bins][2];
        for (int k = 0; k < bins; k++) {
            double mag = Math.sqrt(real[k]*real[k] + imag[k]*imag[k]) / fftSize;
            spectrum[k][0] = (double) k * sampleRate / fftSize; // Hz
            spectrum[k][1] = mag > 1e-10 ? 20 * Math.log10(mag) : -120.0; // dB
        }
        return spectrum;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double[] toMono(double[] stereo, int channels) {
        int monoLen  = stereo.length / channels;
        double[] out = new double[monoLen];
        for (int i = 0; i < monoLen; i++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) sum += stereo[i * channels + c];
            out[i] = sum / channels;
        }
        return out;
    }

    private static void fft(double[] real, double[] imag, boolean inverse) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = real[i]; real[i] = real[j]; real[j] = t;
                t = imag[i]; imag[i] = imag[j]; imag[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cRe = 1, cIm = 0;
                for (int j = 0; j < len / 2; j++) {
                    double uRe = real[i+j], uIm = imag[i+j];
                    double vRe = real[i+j+len/2]*cRe - imag[i+j+len/2]*cIm;
                    double vIm = real[i+j+len/2]*cIm + imag[i+j+len/2]*cRe;
                    real[i+j] = uRe+vRe; imag[i+j] = uIm+vIm;
                    real[i+j+len/2] = uRe-vRe; imag[i+j+len/2] = uIm-vIm;
                    double nRe = cRe*wRe - cIm*wIm; cIm = cRe*wIm + cIm*wRe; cRe = nRe;
                }
            }
        }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public static class AnalysisResult {
        public int    sampleRate;
        public int    channels;
        public int    bitDepth;
        public boolean isStereo;
        public double durationSec;
        public long   fileSizeBytes;
        public double peakDB;
        public double rmsDB;
        public double dynamicRangeDB;
        public double dcOffset;
        public int    clippedSamples;
        public double integratedLUFS;
        public double bpm;                // 0 = not detected
        public double[][] frequencySpectrum; // [bins][2]: [hz, dBFS]

        @Override
        public String toString() {
            return String.format(
                "Sample Rate: %d Hz  |  Channels: %d  |  Bit Depth: %d-bit\n" +
                "Duration: %.2f s  |  File: %d KB\n" +
                "Peak: %.1f dBFS  |  RMS: %.1f dBFS  |  LUFS: %.1f\n" +
                "Dynamic Range: %.1f dB  |  DC Offset: %.4f\n" +
                "Clipped Samples: %d  |  BPM: %s",
                sampleRate, channels, bitDepth,
                durationSec, fileSizeBytes / 1024,
                peakDB, rmsDB, integratedLUFS,
                dynamicRangeDB, dcOffset,
                clippedSamples,
                bpm > 0 ? String.format("%.1f", bpm) : "N/A"
            );
        }
    }
}
