package com.pramod.audioeditor.view;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.pramod.audioeditor.data.EqSettings;

/**
 * EQ panel: 3-band EQ (Low/Mid/High) + Gain, with bypass and clip guard toggles.
 */
public class EqPanelView extends LinearLayout {

    private SeekBar seekLow, seekMid, seekHigh, seekGain;
    private TextView txtLow, txtMid, txtHigh, txtGain;
    private CheckBox cbBypass, cbClipGuard;
    private Listener listener;
    private final EqSettings settings = new EqSettings();

    public interface Listener {
        void onEqChanged(EqSettings settings);
    }

    public EqPanelView(Context context) { super(context); init(); }
    public EqPanelView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(8));
        setBackgroundColor(0xFF1A1A1A);

        // Header
        TextView header = new TextView(getContext());
        header.setText("🎛️ EQ & Gain");
        header.setTextColor(0xFF00CCAA);
        header.setTextSize(14);
        addView(header);

        // Bypass + Clip Guard row
        LinearLayout toggles = new LinearLayout(getContext());
        toggles.setOrientation(HORIZONTAL);
        cbBypass = new CheckBox(getContext());
        cbBypass.setText("Bypass");
        cbBypass.setTextColor(0xFF888888);
        cbBypass.setTextSize(11);
        cbBypass.setOnCheckedChangeListener((b, checked) -> {
            settings.bypass = checked;
            notifyChange();
        });
        toggles.addView(cbBypass);

        cbClipGuard = new CheckBox(getContext());
        cbClipGuard.setText("Clip Guard");
        cbClipGuard.setTextColor(0xFF888888);
        cbClipGuard.setTextSize(11);
        cbClipGuard.setChecked(true);
        cbClipGuard.setOnCheckedChangeListener((b, checked) -> {
            settings.clipGuard = checked;
            notifyChange();
        });
        toggles.addView(cbClipGuard);
        addView(toggles);

        // EQ sliders
        seekLow  = addEqSlider("LOW (200Hz)", -10, 10, 0);
        seekMid  = addEqSlider("MID (1kHz)", -10, 10, 0);
        seekHigh = addEqSlider("HIGH (4kHz)", -10, 10, 0);
        seekGain = addEqSlider("GAIN", -10, 10, 0);
    }

    private SeekBar addEqSlider(String label, int min, int max, int defaultVal) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView txtLabel = new TextView(getContext());
        txtLabel.setText(label);
        txtLabel.setTextColor(0xFFAAAAAA);
        txtLabel.setTextSize(10);
        txtLabel.setWidth(dp(80));
        row.addView(txtLabel);

        SeekBar seek = new SeekBar(getContext());
        seek.setMax(200); // 0..200 → -10..+10
        seek.setProgress(100 + defaultVal); // 100 = 0dB
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        seek.setLayoutParams(p);

        TextView txtVal = new TextView(getContext());
        txtVal.setText("0 dB");
        txtVal.setTextColor(0xFF00CCAA);
        txtVal.setTextSize(11);
        txtVal.setWidth(dp(50));
        row.addView(seek);
        row.addView(txtVal);

        final String lbl = label;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float db = (progress - 100) * 0.1f;
                txtVal.setText(String.format("%.1f dB", db));
                if (fromUser) {
                    if (lbl.startsWith("LOW")) settings.lowDb = db;
                    else if (lbl.startsWith("MID")) settings.midDb = db;
                    else if (lbl.startsWith("HIGH")) settings.highDb = db;
                    else settings.gainDb = db;
                    notifyChange();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        addView(row);
        return seek;
    }

    public EqSettings getSettings() { return settings.copy(); }

    private void notifyChange() {
        if (listener != null) listener.onEqChanged(settings.copy());
    }

    public void setListener(Listener l) { this.listener = l; }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
