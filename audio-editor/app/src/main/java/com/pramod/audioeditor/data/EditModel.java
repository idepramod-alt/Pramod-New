package com.pramod.audioeditor.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-destructive edit state: selection, trim markers, cut points, mode.
 */
public class EditModel {

    public enum Mode { SELECT, TRIM, SPLIT, EQ }

    private Mode mode = Mode.SELECT;
    private long selectionStart = 0;
    private long selectionEnd = 0;
    private long trimStart = 0;
    private long trimEnd = 0;
    private long totalFrames = 0;
    private final List<Long> cutPoints = new ArrayList<>();
    private Listener listener;

    public interface Listener {
        void onSelectionChanged(long start, long end);
        void onTrimChanged(long start, long end);
        void onCutAdded(long frame);
        void onCutRemoved(long frame);
        void onModeChanged(Mode mode);
    }

    public void setListener(Listener l) { this.listener = l; }

    public Mode getMode() { return mode; }
    public void setMode(Mode m) {
        this.mode = m;
        if (listener != null) listener.onModeChanged(m);
    }

    public long getTotalFrames() { return totalFrames; }
    public void setTotalFrames(long f) { this.totalFrames = f; }

    // Selection
    public long getSelectionStart() { return selectionStart; }
    public long getSelectionEnd() { return selectionEnd; }
    public void setSelection(long start, long end) {
        this.selectionStart = Math.max(0, Math.min(start, end));
        this.selectionEnd = Math.min(totalFrames, Math.max(start, end));
        if (listener != null) listener.onSelectionChanged(selectionStart, selectionEnd);
    }
    public boolean hasSelection() { return selectionEnd > selectionStart; }
    public long selectionLength() { return selectionEnd - selectionStart; }

    // Trim
    public long getTrimStart() { return trimStart; }
    public long getTrimEnd() { return trimEnd; }
    public void setTrim(long start, long end) {
        this.trimStart = Math.max(0, start);
        this.trimEnd = Math.min(totalFrames, end);
        if (listener != null) listener.onTrimChanged(trimStart, trimEnd);
    }

    // Cuts (split points)
    public List<Long> getCutPoints() { return cutPoints; }
    public void addCut(long frame) {
        if (frame > 0 && frame < totalFrames && !cutPoints.contains(frame)) {
            cutPoints.add(frame);
            java.util.Collections.sort(cutPoints);
            if (listener != null) listener.onCutAdded(frame);
        }
    }
    public void removeCutNear(long frame, int radius) {
        for (int i = cutPoints.size() - 1; i >= 0; i--) {
            if (Math.abs(cutPoints.get(i) - frame) <= radius) {
                long removed = cutPoints.remove(i);
                if (listener != null) listener.onCutRemoved(removed);
                return;
            }
        }
    }
    public void clearCuts() { cutPoints.clear(); }

    /** Compute segments from cut points. */
    public List<AudioSegment> computeSegments() {
        List<AudioSegment> segments = new ArrayList<>();
        long starts[] = new long[cutPoints.size() + 1];
        long ends[] = new long[cutPoints.size() + 1];

        starts[0] = trimStart;
        for (int i = 0; i < cutPoints.size(); i++) {
            ends[i] = cutPoints.get(i);
            starts[i + 1] = cutPoints.get(i);
        }
        ends[cutPoints.size()] = trimEnd > 0 ? trimEnd : totalFrames;

        for (int i = 0; i < starts.length; i++) {
            if (ends[i] > starts[i]) {
                segments.add(new AudioSegment(i, starts[i], ends[i]));
            }
        }
        return segments;
    }

    /** Get active region (selection or trim or full file). */
    public long activeStart() {
        if (hasSelection()) return selectionStart;
        if (trimEnd > trimStart) return trimStart;
        return 0;
    }
    public long activeEnd() {
        if (hasSelection()) return selectionEnd;
        if (trimEnd > trimStart) return trimEnd;
        return totalFrames;
    }

    /** Snap frame to nearest zero crossing for click-free cuts. */
    public static long snapToZeroCrossing(PcmSource src, long frame, int radius) {
        if (radius <= 0 || src == null) return frame;
        float[] buf = new float[Math.min(radius * 2, 1024)];
        long start = Math.max(0, frame - radius);
        int count = (int) Math.min(buf.length, Math.min(radius, frame) + Math.min(radius, src.totalFrames() - frame));
        src.readFrames(start, count, buf, 0, true);

        long bestFrame = frame;
        float bestVal = Float.MAX_VALUE;
        for (int i = 1; i < count; i++) {
            if ((buf[i - 1] < 0 && buf[i] >= 0) || (buf[i - 1] >= 0 && buf[i] < 0)) {
                // Zero crossing found
                float val = Math.abs(buf[i]);
                if (val < bestVal) {
                    bestVal = val;
                    bestFrame = start + i;
                }
            }
        }
        return bestFrame;
    }
}
