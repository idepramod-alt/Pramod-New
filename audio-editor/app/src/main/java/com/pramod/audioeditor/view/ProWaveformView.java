package com.pramod.audioeditor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.pramod.audioeditor.engine.WaveformPyramid;

/**
 * Professional DAW-style waveform view with:
 * - Multi-resolution peak envelope rendering
 * - Draggable trim markers (start/end)
 * - Selection highlight
 * - Cut markers
 * - Playhead
 * - Pinch-to-zoom
 * - Pan/fling
 */
public class ProWaveformView extends View {

    // Colors (DAW style)
    private static final int COLOR_BG = 0xFF111111;
    private static final int COLOR_PEAK = 0xFF00CCAA;       // Teal waveform
    private static final int COLOR_TRIMREGION = 0x4400CCAA;  // Selection highlight
    private static final int COLOR_MARKER = 0xFFFFAA00;      // Amber markers
    private static final int COLOR_PLAYHEAD = 0xFF00FF88;    // Green playhead
    private static final int COLOR_CUT = 0xFFFF4444;         // Red cut points
    private static final int COLOR_CENTERLINE = 0xFF333333;

    // Paints
    private final Paint paintPeak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPeakMirror = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTrimRegion = new Paint();
    private final Paint paintMarker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMarkerHandle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPlayhead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTimeRuler = new Paint(Paint.ANTI_ALIAS_FLAG);

    // State
    private WaveformPyramid pyramid;
    private long viewStartFrame = 0;
    private double framesPerPixel = 100;
    private long playheadFrame = 0;
    private boolean isPlaying = false;

    // Markers
    private long trimStartFrame = 0;
    private long trimEndFrame = 0;
    private boolean showTrimMarkers = true;

    // Selection
    private long selectionStart = 0;
    private long selectionEnd = 0;
    private boolean showSelection = false;

    // Cut points
    private long[] cutPoints = new long[0];

    // Touch
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private int draggedMarker = 0; // 0=none, 1=start, 2=end, 3=playhead
    private float touchSlop;

    // Mode
    public enum Mode { SELECT, TRIM, SPLIT, EQ }
    private Mode mode = Mode.SELECT;

    // Overview mode (thin strip)
    private boolean overviewMode = false;

    // Listener
    private Listener listener;
    public interface Listener {
        void onTrimChanged(long start, long end);
        void onPlayheadChanged(long frame);
        void onZoomChanged(double framesPerPixel, long anchorFrame);
        void onSelectionChanged(long start, long end);
        void onCutAdded(long frame);
    }

    public ProWaveformView(Context context) { super(context); init(); }
    public ProWaveformView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ProWaveformView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        setBackgroundColor(COLOR_BG);

        paintPeak.setColor(COLOR_PEAK);
        paintPeak.setStyle(Paint.Style.FILL);
        paintPeakMirror.setColor(0xFF009977);
        paintPeakMirror.setStyle(Paint.Style.FILL);

        paintTrimRegion.setColor(COLOR_TRIMREGION);
        paintTrimRegion.setStyle(Paint.Style.FILL);

        paintMarker.setColor(COLOR_MARKER);
        paintMarker.setStrokeWidth(dp(2));
        paintMarker.setStyle(Paint.Style.STROKE);

        paintMarkerHandle.setColor(COLOR_MARKER);
        paintMarkerHandle.setStyle(Paint.Style.FILL);

        paintPlayhead.setColor(COLOR_PLAYHEAD);
        paintPlayhead.setStrokeWidth(dp(2));

        paintCut.setColor(COLOR_CUT);
        paintCut.setStrokeWidth(dp(2));

        paintCenter.setColor(COLOR_CENTERLINE);
        paintCenter.setStrokeWidth(1);

        paintText.setColor(0xFF888888);
        paintText.setTextSize(dp(10));

        paintTimeRuler.setColor(0xFF444444);
        paintTimeRuler.setStrokeWidth(1);
        paintTimeRuler.setTextSize(dp(9));

        touchSlop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                long frameDelta = (long)(dx * framesPerPixel);
                viewStartFrame = Math.max(0, viewStartFrame + frameDelta);
                invalidate();
                return true;
            }
            @Override
            public boolean onDown(MotionEvent e) { return true; }
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mode == Mode.SPLIT) {
                    long frame = frameAtX(e.getX());
                    if (listener != null) listener.onCutAdded(frame);
                } else {
                    long frame = frameAtX(e.getX());
                    playheadFrame = frame;
                    if (listener != null) listener.onPlayheadChanged(frame);
                    invalidate();
                }
                return true;
            }
        });

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                double factor = 1.0 / detector.getScaleFactor();
                long anchorFrame = frameAtX(detector.getFocusX());
                framesPerPixel = Math.max(1, Math.min(framesPerPixel * factor, 100000));
                viewStartFrame = Math.max(0, anchorFrame - (long)(detector.getFocusX() * framesPerPixel));
                invalidate();
                if (listener != null) listener.onZoomChanged(framesPerPixel, anchorFrame);
                return true;
            }
        });
    }

    public void setPyramid(WaveformPyramid p) {
        this.pyramid = p;
        recalcZoom();
        invalidate();
    }

    private void recalcZoom() {
        if (pyramid != null && pyramid.getTotalFrames() > 0 && getWidth() > 0) {
            framesPerPixel = pyramid.getTotalFrames() / (double) getWidth();
            if (framesPerPixel < 1) framesPerPixel = 1;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcZoom();
        invalidate();
    }

    public void setPlayhead(long frame) { this.playheadFrame = frame; invalidate(); }
    public void setPlaying(boolean p) { this.isPlaying = p; }
    public void setTrimMarkers(long start, long end) { this.trimStartFrame = start; this.trimEndFrame = end; invalidate(); }
    public void setSelection(long start, long end) { this.selectionStart = start; this.selectionEnd = end; this.showSelection = true; invalidate(); }
    public void clearSelection() { this.showSelection = false; invalidate(); }
    public void setCutPoints(long[] cuts) { this.cutPoints = cuts; invalidate(); }
    public void setMode(Mode m) { this.mode = m; invalidate(); }
    public void setListener(Listener l) { this.listener = l; }
    public void setOverviewMode(boolean o) { this.overviewMode = o; invalidate(); }

    public long frameAtX(float x) {
        return viewStartFrame + (long)(x * framesPerPixel);
    }
    public float xForFrame(long frame) {
        return (float)((frame - viewStartFrame) / framesPerPixel);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Center line
        canvas.drawLine(0, h / 2f, w, h / 2f, paintCenter);

        // Time ruler
        drawTimeRuler(canvas, w, h);

        // Waveform peaks
        if (pyramid != null) {
            WaveformPyramid.Level level = pyramid.pickLevel(framesPerPixel);
            if (level != null) {
                drawPeaks(canvas, level, w, h);
            }
        }

        // Trim region highlight
        if (showTrimMarkers && trimEndFrame > trimStartFrame) {
            float x1 = xForFrame(trimStartFrame);
            float x2 = xForFrame(trimEndFrame);
            canvas.drawRect(x1, 0, x2, h, paintTrimRegion);
            drawMarker(canvas, x1, h, COLOR_MARKER, "IN");
            drawMarker(canvas, x2, h, COLOR_MARKER, "OUT");
        }

        // Selection highlight
        if (showSelection && selectionEnd > selectionStart) {
            float x1 = xForFrame(selectionStart);
            float x2 = xForFrame(selectionEnd);
            paintTrimRegion.setColor(0x440088FF);
            canvas.drawRect(x1, 0, x2, h, paintTrimRegion);
            paintTrimRegion.setColor(COLOR_TRIMREGION);
        }

        // Cut points
        for (long cut : cutPoints) {
            float x = xForFrame(cut);
            canvas.drawLine(x, 0, x, h, paintCut);
        }

        // Playhead
        float phX = xForFrame(playheadFrame);
        canvas.drawLine(phX, 0, phX, h, paintPlayhead);
        // Playhead handle
        canvas.drawCircle(phX, dp(8), dp(6), paintPlayhead);
    }

    private void drawPeaks(Canvas canvas, WaveformPyramid.Level level, int w, int h) {
        float halfH = h / 2f;
        float maxAmp = halfH * 0.9f;

        int startCol = Math.max(0, (int)(viewStartFrame / level.framesPerPixel));
        int endCol = Math.min(level.columns, startCol + w + 2);

        for (int c = startCol; c < endCol; c++) {
            float x = (float)((c * level.framesPerPixel - viewStartFrame) / framesPerPixel);
            if (x < -2 || x > w + 2) continue;

            float minVal = level.min[c];
            float maxVal = level.max[c];

            // Upper half (positive)
            float yTop = halfH - maxVal * maxAmp;
            float yBot = halfH - minVal * maxAmp;
            if (minVal < 0) yBot = halfH - minVal * maxAmp;

            // Draw peak envelope
            canvas.drawRect(x - 0.5f, Math.min(yTop, halfH), x + 0.5f, Math.max(yBot, halfH), paintPeak);

            // Mirror for negative
            float yTopM = halfH + minVal * maxAmp;
            float yBotM = halfH + maxVal * maxAmp;
            canvas.drawRect(x - 0.5f, Math.min(yTopM, halfH), x + 0.5f, Math.max(yBotM, halfH), paintPeakMirror);
        }
    }

    private void drawMarker(Canvas canvas, float x, int h, int color, String label) {
        canvas.drawLine(x, 0, x, h, paintMarker);
        // Handle knob
        canvas.drawCircle(x, h / 2f, dp(8), paintMarkerHandle);
        // Label
        paintText.setColor(color);
        canvas.drawText(label, x + dp(10), h / 2f + dp(4), paintText);
    }

    private void drawTimeRuler(Canvas canvas, int w, int h) {
        if (pyramid == null) return;
        int sr = 44100; // default
        double secPerPixel = framesPerPixel / sr;
        double pixPerSec = 1.0 / secPerPixel;

        // Choose nice interval
        double interval = 1.0; // 1 second
        if (pixPerSec < 20) interval = 10;
        else if (pixPerSec < 50) interval = 5;
        else if (pixPerSec > 200) interval = 0.5;
        else if (pixPerSec > 500) interval = 0.1;

        double startTime = viewStartFrame / (double) sr;
        double firstTick = Math.ceil(startTime / interval) * interval;

        for (double t = firstTick; t < (startTime + w * secPerPixel); t += interval) {
            float x = (float)((t - startTime) * pixPerSec);
            canvas.drawLine(x, 0, x, dp(12), paintTimeRuler);
            String label = formatTime(t);
            canvas.drawText(label, x + 2, dp(10), paintTimeRuler);
        }
    }

    private String formatTime(double seconds) {
        int mins = (int)(seconds / 60);
        double secs = seconds % 60;
        if (mins > 0) return String.format("%d:%04.1f", mins, secs);
        return String.format("%.2f", secs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = scaleDetector.onTouchEvent(event);
        handled |= gestureDetector.onTouchEvent(event);

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            long frame = frameAtX(x);

            // Check if touching near trim markers
            if (showTrimMarkers) {
                float startX = xForFrame(trimStartFrame);
                float endX = xForFrame(trimEndFrame);
                if (Math.abs(x - startX) < dp(24)) {
                    draggedMarker = 1;
                    return true;
                }
                if (Math.abs(x - endX) < dp(24)) {
                    draggedMarker = 2;
                    return true;
                }
            }

            // Check playhead
            float phX = xForFrame(playheadFrame);
            if (Math.abs(x - phX) < dp(24)) {
                draggedMarker = 3;
                return true;
            }
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && draggedMarker > 0) {
            long frame = Math.max(0, frameAtX(event.getX()));
            if (draggedMarker == 1) {
                trimStartFrame = frame;
                if (listener != null) listener.onTrimChanged(trimStartFrame, trimEndFrame);
            } else if (draggedMarker == 2) {
                trimEndFrame = frame;
                if (listener != null) listener.onTrimChanged(trimStartFrame, trimEndFrame);
            } else if (draggedMarker == 3) {
                playheadFrame = frame;
                if (listener != null) listener.onPlayheadChanged(frame);
            }
            invalidate();
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            draggedMarker = 0;
        }

        return handled;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
