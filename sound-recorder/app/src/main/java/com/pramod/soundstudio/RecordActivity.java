package com.pramod.soundstudio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordActivity extends AppCompatActivity {

    private static final int PERM_REQ = 101;

    // UI
    private TextView    tvUsbStatus, tvTimer, tvStatus;
    private WaveformView waveformView;
    private Button      btnRecord;
    private RadioButton rbWav, rbCompressed;
    private LinearLayout llSampleRate, llBitDepth;
    private int selectedSampleRate = 44100;
    private int selectedBitDepth   = 16;

    // Recording
    private AudioRecorderHelper recorder;
    private final Handler       uiHandler   = new Handler(Looper.getMainLooper());
    private long                startTimeMs = 0;
    private final List<Float>   amplitudes  = new ArrayList<>();
    private Runnable            timerRunnable;

    // USB poll
    private final Handler usbHandler = new Handler(Looper.getMainLooper());
    private Runnable usbPollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        tvUsbStatus  = findViewById(R.id.tvUsbStatus);
        tvTimer      = findViewById(R.id.tvTimer);
        tvStatus     = findViewById(R.id.tvRecStatus);
        waveformView = findViewById(R.id.waveformView);
        btnRecord    = findViewById(R.id.btnRecord);
        rbWav        = findViewById(R.id.rbWav);
        rbCompressed = findViewById(R.id.rbCompressed);
        llSampleRate = findViewById(R.id.llSampleRate);
        llBitDepth   = findViewById(R.id.llBitDepth);

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setupFormatSelector();
        setupSampleRateChips();
        setupBitDepthChips();

        recorder = new AudioRecorderHelper(this);
        recorder.setOnAmplitude(amp -> uiHandler.post(() -> pushAmplitude(amp)));
        recorder.setOnDone((file, err) -> uiHandler.post(() -> onRecordDone(file, err)));

        btnRecord.setOnClickListener(v -> onRecordClick());

        // Bottom nav
        View btnFiles = findViewById(R.id.navFiles);
        if (btnFiles != null) btnFiles.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUsbPoll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        usbHandler.removeCallbacks(usbPollRunnable);
    }

    // ── USB status poll ──────────────────────────────────────────────────────

    private void startUsbPoll() {
        usbPollRunnable = new Runnable() {
            @Override public void run() {
                updateUsbStatus();
                usbHandler.postDelayed(this, 2000);
            }
        };
        usbHandler.post(usbPollRunnable);
    }

    private void updateUsbStatus() {
        AudioDeviceInfo usb = AudioRecorderHelper.getUsbInputDevice(this);
        if (usb != null) {
            tvUsbStatus.setText("🟢 USB Audio Connected");
            tvUsbStatus.setTextColor(0xFF4CAF50);
            recorder.setPreferredDevice(usb);
        } else {
            tvUsbStatus.setText("⚪ No USB Device (Built-in Mic)");
            tvUsbStatus.setTextColor(0xFF888888);
        }
    }

    // ── Format selector ──────────────────────────────────────────────────────

    private void setupFormatSelector() {
        rbWav.setChecked(true);
        rbWav.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                recorder.setFormat(AudioRecorderHelper.Format.WAV);
                llBitDepth.setVisibility(View.VISIBLE);
            }
        });
        rbCompressed.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                recorder.setFormat(AudioRecorderHelper.Format.COMPRESSED);
                llBitDepth.setVisibility(View.GONE);
            }
        });
    }

    // ── Sample rate chips ────────────────────────────────────────────────────

    private void setupSampleRateChips() {
        int[] rates = {22050, 44100, 48000, 96000};
        String[] labels = {"22kHz", "44.1kHz", "48kHz", "96kHz"};
        buildChips(llSampleRate, labels, 1 /*default 44.1kHz*/, idx -> {
            selectedSampleRate = rates[idx];
            recorder.setSampleRate(selectedSampleRate);
            updateFileSizeEstimate();
        });
    }

    // ── Bit depth chips ──────────────────────────────────────────────────────

    private void setupBitDepthChips() {
        int[] depths = {16, 24};
        String[] labels = {"16-bit", "24-bit"};
        buildChips(llBitDepth, labels, 0 /*default 16-bit*/, idx -> {
            selectedBitDepth = depths[idx];
            recorder.setBitDepth(selectedBitDepth);
            updateFileSizeEstimate();
        });
    }

    private void buildChips(LinearLayout parent, String[] labels, int defaultIdx,
                             OnChipSelected listener) {
        parent.removeAllViews();
        Button[] chips = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int fi = i;
            Button btn = new Button(this);
            btn.setText(labels[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12f);
            btn.setPadding(24, 8, 24, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(i == defaultIdx ? 0xFFE53935 : 0xFF333333);
            chips[i] = btn;
            btn.setOnClickListener(v -> {
                for (Button b : chips) b.setBackgroundColor(0xFF333333);
                btn.setBackgroundColor(0xFFE53935);
                listener.onSelected(fi);
            });
            parent.addView(btn);
        }
    }

    interface OnChipSelected { void onSelected(int idx); }

    private void updateFileSizeEstimate() {
        TextView tvEst = findViewById(R.id.tvFileSizeEst);
        if (tvEst == null) return;
        boolean isWav = rbWav.isChecked();
        if (isWav) {
            double mbPerMin = selectedSampleRate * selectedBitDepth / 8.0 / 1024.0 / 1024.0 * 60;
            tvEst.setText(String.format(Locale.US, "≈ %.1f MB/min (WAV %dHz %d-bit)",
                    mbPerMin, selectedSampleRate, selectedBitDepth));
        } else {
            tvEst.setText("≈ 2.4 MB/min (AAC 320kbps)");
        }
    }

    // ── Record button ────────────────────────────────────────────────────────

    private void onRecordClick() {
        if (recorder.isRecording()) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, PERM_REQ);
            return;
        }

        amplitudes.clear();
        waveformView.setAmplitudes(new float[0]);

        String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File   dir  = new File(getFilesDir(), "recordings");

        recorder.setSampleRate(selectedSampleRate);
        recorder.setBitDepth(selectedBitDepth);
        recorder.startRecording(dir, "REC_" + ts);

        startTimeMs = System.currentTimeMillis();
        btnRecord.setText("⏹ STOP");
        btnRecord.setBackgroundColor(0xFF880000);
        tvStatus.setText("● RECORDING");
        tvStatus.setTextColor(0xFFE53935);

        startTimer();
    }

    private void stopRecording() {
        recorder.stopRecording();
        stopTimer();
        btnRecord.setText("⏺ RECORD");
        btnRecord.setBackgroundColor(0xFFE53935);
        tvStatus.setText("Saving…");
        tvStatus.setTextColor(0xFF888888);
    }

    private void onRecordDone(File file, String error) {
        if (error != null || file == null) {
            tvStatus.setText("❌ Error: " + error);
            return;
        }
        tvStatus.setText("✅ Saved: " + file.getName());
        // Open EditorActivity immediately
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override public void run() {
                long elapsed = System.currentTimeMillis() - startTimeMs;
                long ms   = elapsed % 1000;
                long secs = (elapsed / 1000) % 60;
                long mins = elapsed / 60000;
                tvTimer.setText(String.format(Locale.US, "%02d:%02d.%03d", mins, secs, ms));
                uiHandler.postDelayed(this, 50);
            }
        };
        uiHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) uiHandler.removeCallbacks(timerRunnable);
    }

    private void pushAmplitude(float amp) {
        amplitudes.add(amp);
        // Keep last 80 bars
        while (amplitudes.size() > 80) amplitudes.remove(0);
        float[] arr = new float[amplitudes.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = amplitudes.get(i);
        waveformView.setAmplitudes(arr);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_REQ && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            onRecordClick();
        } else {
            Toast.makeText(this, "Microphone permission required!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear callbacks FIRST so late onDone/onAmplitude posts don't navigate a dead Activity
        recorder.setOnAmplitude(null);
        recorder.setOnDone(null);
        if (recorder.isRecording()) recorder.stopRecording();
        stopTimer();
        uiHandler.removeCallbacksAndMessages(null);
        usbHandler.removeCallbacksAndMessages(null);
    }
}
