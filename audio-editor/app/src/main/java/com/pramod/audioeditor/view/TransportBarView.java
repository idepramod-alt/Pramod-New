package com.pramod.audioeditor.view;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Transport bar: play/pause/stop + current/total time display.
 */
public class TransportBarView extends LinearLayout {

    private Button btnPlay, btnStop;
    private TextView txtTime;
    private Listener listener;
    private boolean playing = false;
    private int sampleRate = 44100;

    public interface Listener {
        void onPlayPause();
        void onStop();
    }

    public TransportBarView(Context context) { super(context); init(); }
    public TransportBarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(8), dp(4), dp(8), dp(4));
        setBackgroundColor(0xFF1A1A2E);

        btnPlay = new Button(getContext());
        btnPlay.setText("▶");
        btnPlay.setTextSize(18);
        btnPlay.setTextColor(0xFF00FF88);
        btnPlay.setBackgroundColor(Color.TRANSPARENT);
        btnPlay.setOnClickListener(v -> { if (listener != null) listener.onPlayPause(); });
        addView(btnPlay, new LayoutParams(dp(48), dp(48)));

        btnStop = new Button(getContext());
        btnStop.setText("⏹");
        btnStop.setTextSize(18);
        btnStop.setTextColor(0xFFFF4444);
        btnStop.setBackgroundColor(Color.TRANSPARENT);
        btnStop.setOnClickListener(v -> { if (listener != null) listener.onStop(); });
        addView(btnStop, new LayoutParams(dp(48), dp(48)));

        txtTime = new TextView(getContext());
        txtTime.setText("00:00.00 / 00:00.00");
        txtTime.setTextColor(0xFFCCCCCC);
        txtTime.setTextSize(14);
        txtTime.setPadding(dp(12), 0, 0, 0);
        addView(txtTime, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    public void setListener(Listener l) { this.listener = l; }
    public void setSampleRate(int sr) { this.sampleRate = sr; }

    public void updateTime(long currentFrame, long totalFrames) {
        String cur = formatTime(currentFrame);
        String tot = formatTime(totalFrames);
        txtTime.setText(cur + " / " + tot);
    }

    public void setPlaying(boolean p) {
        this.playing = p;
        btnPlay.setText(p ? "⏸" : "▶");
    }

    private String formatTime(long frames) {
        double seconds = frames / (double) sampleRate;
        int mins = (int)(seconds / 60);
        double secs = seconds % 60;
        return String.format("%02d:%05.2f", mins, secs);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
