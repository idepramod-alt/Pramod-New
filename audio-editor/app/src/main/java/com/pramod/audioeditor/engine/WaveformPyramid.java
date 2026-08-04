package com.pramod.audioeditor.engine;

import com.pramod.audioeditor.data.PcmSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-resolution min/max peak envelope for efficient waveform rendering at any zoom level.
 * Precomputes peak data at several decimated resolutions.
 */
public class WaveformPyramid {

    private static final int BASE_RESOLUTION = 16; // frames per column at base level
    private static final int DECIMATION = 8;       // each level decimates by this factor
    private static final int MAX_COLUMNS = 1_500_000;

    public static class Level {
        public final float[] min;
        public final float[] max;
        public final long startFrame;
        public final long endFrame;
        public final int columns;
        public final double framesPerPixel;

        Level(float[] min, float[] max, long startFrame, long endFrame, int columns, double fpp) {
            this.min = min; this.max = max;
            this.startFrame = startFrame; this.endFrame = endFrame;
            this.columns = columns; this.framesPerPixel = fpp;
        }
    }

    private final List<Level> levels = new ArrayList<>();
    private final int sampleRate;
    private final int channels;
    private final long totalFrames;

    private WaveformPyramid(int sampleRate, int channels, long totalFrames) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.totalFrames = totalFrames;
    }

    /**
     * Build the pyramid from a PcmSource on a background thread.
     * @param progress callback for 0-100 progress (can be null)
     */
    public static WaveformPyramid build(PcmSource src, ProgressListener progress) {
        WaveformPyramid pyramid = new WaveformPyramid(src.sampleRate(), src.channels(), src.totalFrames());
        long totalFrames = src.totalFrames();
        if (totalFrames == 0) return pyramid;

        // Build levels from coarsest to finest
        double fpp = BASE_RESOLUTION;
        while (true) {
            int cols = (int)(totalFrames / fpp) + 1;
            if (cols > MAX_COLUMNS && fpp < totalFrames) {
                fpp *= DECIMATION;
                continue;
            }
            cols = Math.min(cols, MAX_COLUMNS);

            float[] min = new float[cols];
            float[] max = new float[cols];
            float[] readBuf = new float[(int)Math.min(fpp * src.channels(), 16384)];

            for (int c = 0; c < cols; c++) {
                long frameStart = (long)(c * fpp);
                int framesToRead = (int) Math.min((long)readBuf.length / src.channels(), totalFrames - frameStart);
                if (framesToRead <= 0) break;

                src.readFrames(frameStart, framesToRead, readBuf, 0, false);

                float minVal = Float.MAX_VALUE;
                float maxVal = Float.MIN_VALUE;
                for (int i = 0; i < framesToRead * src.channels(); i++) {
                    if (readBuf[i] < minVal) minVal = readBuf[i];
                    if (readBuf[i] > maxVal) maxVal = readBuf[i];
                }
                min[c] = minVal;
                max[c] = maxVal;
            }

            pyramid.levels.add(new Level(min, max, 0, totalFrames, cols, fpp));

            if (progress != null) {
                int pct = Math.min(100, (int)(pyramid.levels.size() * 10));
                progress.onProgress(pct);
            }

            if (cols <= 1000) break; // fine enough
            fpp *= DECIMATION;
        }

        // Reverse so finest level is first (index 0)
        java.util.Collections.reverse(pyramid.levels);
        if (progress != null) progress.onProgress(100);
        return pyramid;
    }

    /** Pick the best level for the given frames-per-pixel. */
    public Level pickLevel(double framesPerPixel) {
        if (levels.isEmpty()) return null;
        // Find level whose framesPerPixel is closest to requested
        Level best = levels.get(0);
        for (Level l : levels) {
            if (Math.abs(l.framesPerPixel - framesPerPixel) < Math.abs(best.framesPerPixel - framesPerPixel)) {
                best = l;
            }
        }
        return best;
    }

    public int getLevelCount() { return levels.size(); }
    public Level getLevel(int i) { return levels.get(i); }
    public long getTotalFrames() { return totalFrames; }

    public interface ProgressListener {
        void onProgress(int percent);
    }
}
