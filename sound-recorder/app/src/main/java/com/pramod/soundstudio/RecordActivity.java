package com.pramod.soundstudio;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Recording screen.
 *
 * Actual capture is delegated to {@link RecordingService} (a bound + foreground
 * service) so recording keeps running — with a persistent notification — when
 * the app is backgrounded or the screen turns off. This Activity binds to the
 * service, drives the start/stop/pause controls, and renders live timer /
 * waveform UI from broadcasts + the amplitude callback.
 */
public class RecordActivity extends AppCompatActivity {

    private static final int PERM_REQ_MIC   = 101;
    private static final int PERM_REQ_NOTIF = 102;

    // UI
    private TextView    tvUsbStatus, tvTimer, tvStatus;
    private WaveformView waveformView;
    private Button      btnRecord;
    private RadioButton rbWav, rbCompressed;
    private LinearLayout llSampleRate, llBitDepth;
    private int selectedSampleRate = 44100;
    private int selectedBitDepth   = 16;

    // Recording (delegated to RecordingService)
    private final Handler       uiHandler   = new Handler(Looper.getMainLooper());
    private final List<Float>   amplitudes  = new ArrayList<>();

    private RecordingService    boundService;
    private boolean             isBound = false;

    // USB poll
    private final Handler usbHandler = new Handler(Looper.getMainLooper());
    private Runnable usbPollRunnable;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            boundService = ((RecordingService.LocalBinder) binder).getService();
            isBound = true;
            boundService.setAmplitudeCallback(amp -> uiHandler.post(() -> pushAmplitude(amp)));
            syncUiWithService();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            boundService = null;
            isBound = false;
        }
    };

    private final BroadcastReceiver recordingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case "com.pramod.soundstudio.TIMER_TICK": {
                    long elapsed = intent.getLongExtra("elapsed_ms", 0);
                    updateTimerText(elapsed);
                    break;
                }
                case "com.pramod.soundstudio.RECORDING_DONE": {
                    String path  = intent.getStringExtra("file_path");
                    String error = intent.getStringExtra("error");
                    onRecordDone(path != null ? new File(path) : null, error);
                    break;
                }
                case "com.pramod.soundstudio.RECORDING_PAUSED":
                    tvStatus.setText("⏸ PAUSED");
                    tvStatus.setTextColor(0xFFFF8F00);
                    break;
                case "com.pramod.soundstudio.RECORDING_RESUMED":
                    tvStatus.setText("● RECORDING");
                    tvStatus.setTextColor(0xFFE53935);
                    break;
            }
        }
    };

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

        btnRecord.setOnClickListener(v -> onRecordClick());

        // Bottom nav
        View btnFiles = findViewById(R.id.navFiles);
        if (btnFiles != null) btnFiles.setOnClickListener(v -> finish());

        // Bind to the recording service so we can control/observe an in-progress
        // recording even if it was started before this Activity instance existed.
        bindService(new Intent(this, RecordingService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.pramod.soundstudio.TIMER_TICK");
        filter.addAction("com.pramod.soundstudio.RECORDING_DONE");
        filter.addAction("com.pramod.soundstudio.RECORDING_PAUSED");
        filter.addAction("com.pramod.soundstudio.RECORDING_RESUMED");
        ContextCompat.registerReceiver(this, recordingReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(recordingReceiver);
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

    private void syncUiWithService() {
        if (boundService == null) return;
        if (boundService.isRecording()) {
            btnRecord.setText("⏹ STOP");
            btnRecord.setBackgroundColor(0xFF880000);
            tvStatus.setText(boundService.isPaused() ? "⏸ PAUSED" : "● RECORDING");
            tvStatus.setTextColor(boundService.isPaused() ? 0xFFFF8F00 : 0xFFE53935);
            updateTimerText(boundService.getElapsedMs());
        }
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
        } else {
            tvUsbStatus.setText("⚪ No USB Device (Built-in Mic)");
            tvUsbStatus.setTextColor(0xFF888888);
        }
    }

    // ── Format selector ──────────────────────────────────────────────────────

    private void setupFormatSelector() {
        rbWav.setChecked(true);
        rbWav.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) llBitDepth.setVisibility(View.VISIBLE);
        });
        rbCompressed.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) llBitDepth.setVisibility(View.GONE);
        });
    }

    // ── Sample rate chips ────────────────────────────────────────────────────

    private void setupSampleRateChips() {
        int[] rates = {22050, 44100, 48000, 96000};
        String[] labels = {"22kHz", "44.1kHz", "48kHz", "96kHz"};
        buildChips(llSampleRate, labels, 1 /*default 44.1kHz*/, idx -> {
            selectedSampleRate = rates[idx];
            updateFileSizeEstimate();
        });
    }

    // ── Bit depth chips ──────────────────────────────────────────────────────

    private void setupBitDepthChips() {
        int[] depths = {16, 24};
        String[] labels = {"16-bit", "24-bit"};
        buildChips(llBitDepth, labels, 0 /*default 16-bit*/, idx -> {
            selectedBitDepth = depths[idx];
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
        boolean recording = isBound && boundService != null && boundService.isRecording();
        if (recording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, PERM_REQ_MIC);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQ_NOTIF);
            return;
        }

        amplitudes.clear();
        waveformView.setAmplitudes(new float[0]);

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        Intent startIntent = new Intent(this, RecordingService.class);
        startIntent.setAction(RecordingService.ACTION_START_RECORDING);
        startIntent.putExtra(RecordingService.EXTRA_SAMPLE_RATE, selectedSampleRate);
        startIntent.putExtra(RecordingService.EXTRA_BIT_DEPTH, selectedBitDepth);
        startIntent.putExtra(RecordingService.EXTRA_FORMAT, rbWav.isChecked() ? "WAV" : "COMPRESSED");
        startIntent.putExtra(RecordingService.EXTRA_FILE_NAME, "REC_" + ts);
        ContextCompat.startForegroundService(this, startIntent);

        btnRecord.setText("⏹ STOP");
        btnRecord.setBackgroundColor(0xFF880000);
        tvStatus.setText("● RECORDING");
        tvStatus.setTextColor(0xFFE53935);
        updateTimerText(0);
    }

    private void stopRecording() {
        if (isBound && boundService != null) {
            boundService.stopRecording();
        } else {
            Intent stopIntent = new Intent(this, RecordingService.class);
            stopIntent.setAction(RecordingService.ACTION_STOP_RECORDING);
            startService(stopIntent);
        }
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

    // ── Timer / waveform ─────────────────────────────────────────────────────

    private void updateTimerText(long elapsedMs) {
        long secs = (elapsedMs / 1000) % 60;
        long mins = elapsedMs / 60000;
        tvTimer.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
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
        if (results.length == 0) return;
        if (req == PERM_REQ_MIC) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                onRecordClick();
            } else {
                Toast.makeText(this, "Microphone permission required!", Toast.LENGTH_LONG).show();
            }
        } else if (req == PERM_REQ_NOTIF) {
            // Notification permission is best-effort — recording still works without it,
            // the persistent status notification just won't be visible.
            onRecordClick();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        uiHandler.removeCallbacksAndMessages(null);
        usbHandler.removeCallbacksAndMessages(null);
    }
}
