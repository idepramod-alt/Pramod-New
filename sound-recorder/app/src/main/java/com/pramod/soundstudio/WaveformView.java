package com.pramod.soundstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom waveform visualizer — draws amplitude bars.
 * Used in both RecordActivity (live waveform) and EditorActivity (static waveform).
 */
public class WaveformView extends View {

    private final Paint barPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trimPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint    = new Paint();
    private final RectF rect       = new RectF();

    private float[] amplitudes     = new float[0]; // values 0.0–1.0
    private float   trimStart      = 0f;           // 0.0–1.0 position
    private float   trimEnd        = 1f;           // 0.0–1.0 position
    private boolean showTrimRegion = false;
    private int     barColor       = 0xFFE53935;   // red default
    private int     barColorDim    = 0xFF444444;

    public WaveformView(Context ctx) { super(ctx); init(); }
    public WaveformView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public WaveformView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(); }

    private void init() {
        bgPaint.setColor(0xFF111111);
        barPaint.setColor(barColor);
        trimPaint.setColor(0x55E53935);
        trimPaint.setStyle(Paint.Style.FILL);
        setBackgroundColor(0xFF111111);
    }

    /** Set live amplitude bars (values 0.0–1.0). */
    public void setAmplitudes(float[] amps) {
        this.amplitudes = amps != null ? amps : new float[0];
        invalidate();
    }

    /** Enable trim region highlight. trimStart/End in 0.0–1.0 of total duration. */
    public void setTrimRegion(float start, float end) {
        this.trimStart      = Math.max(0f, Math.min(1f, start));
        this.trimEnd        = Math.max(0f, Math.min(1f, end));
        this.showTrimRegion = true;
        invalidate();
    }

    public void hideTrimRegion() {
        showTrimRegion = false;
        invalidate();
    }

    public void setBarColor(int color) {
        barColor = color;
        barPaint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Draw trim region highlight
        if (showTrimRegion) {
            rect.set(trimStart * w, 0, trimEnd * w, h);
            trimPaint.setColor(0x33FF8F00);
            canvas.drawRect(rect, trimPaint);
        }

        if (amplitudes.length == 0) {
            // Draw a flat idle line
            barPaint.setColor(barColorDim);
            canvas.drawRect(0, h / 2f - 1f, w, h / 2f + 1f, barPaint);
            return;
        }

        int count   = amplitudes.length;
        float step  = (float) w / count;
        float barW  = Math.max(1f, step * 0.7f);
        float gap   = step - barW;

        for (int i = 0; i < count; i++) {
            float amp    = Math.max(0.02f, amplitudes[i]);
            float cx     = i * step + step / 2f;
            float halfH  = amp * (h / 2f) * 0.95f;

            // Colour depends on trim region
            boolean inTrim = showTrimRegion
                    && (cx / w) >= trimStart
                    && (cx / w) <= trimEnd;
            barPaint.setColor(inTrim ? 0xFFFF8F00 : barColor);

            rect.set(cx - barW / 2f, h / 2f - halfH, cx + barW / 2f, h / 2f + halfH);
            canvas.drawRoundRect(rect, 2f, 2f, barPaint);
        }

        // Trim boundary lines
        if (showTrimRegion) {
            Paint linePaint = new Paint();
            linePaint.setColor(0xFFFF8F00);
            linePaint.setStrokeWidth(3f);
            canvas.drawLine(trimStart * w, 0, trimStart * w, h, linePaint);
            canvas.drawLine(trimEnd   * w, 0, trimEnd   * w, h, linePaint);
        }
    }
}
