package com.pramod.soundstudio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * EchoRecordActivity — Recording with real-time Echo DSP.
 *
 * Two modes via Switch:
 *  ON  → EchoRecordingEngine (delay + feedback + dry/wet mix)
 *  OFF → Normal AudioRecorderHelper (identical to RecordActivity behaviour)
 *
 * After recording → opens EditorActivity with the saved WAV.
 * ExistingRecordActivity is NOT modified.
 */
public class EchoRecordActivity extends AppCompatActivity {

    private static final int PERM_REQ = 202;

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView     tvTimer, tvStatus, tvUsbStatus, tvPreview;
    private WaveformView waveformView;
    private Button       btnRecord;
    private SeekBar      sbDelay, sbFeedback, sbDry, sbWet;
    private TextView     tvDelayVal, tvFeedbackVal, tvDryVal, tvWetVal;
    private Switch       swEcho;

    // ── Engines ───────────────────────────────────────────────────────────────
    private EchoRecordingEngine echoEngine;
    private AudioRecorderHelper normalRecorder;
    private boolean             useEcho = true;

    // ── State ────────────────────────────────────────────────────────────────
    private final Handler     uiHandler  = new Handler(Looper.getMainLooper());
    private final Handler     usbHandler = new Handler(Looper.getMainLooper());
    private Runnable          timerRun, usbPollRun;
    private long              startMs;
    private final List<Float> amplitudes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_echo_record);
        bindViews();
        setupEngines();
        setupSliders();
        btnRecord.setOnClickListener(v -> onRecordClick());
        swEcho.setOnCheckedChangeListener((b, checked) -> { useEcho = checked; updateEchoVis(); updatePreview(); });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        updateEchoVis(); updatePreview();
    }

    @Override protected void onResume()  { super.onResume();  startUsbPoll(); }
    @Override protected void onPause()   { super.onPause();   usbHandler.removeCallbacks(usbPollRun); }
    @Override protected void onDestroy() {
        super.onDestroy();
        echoEngine.setOnAmplitude(null);   echoEngine.setOnDone(null);
        normalRecorder.setOnAmplitude(null); normalRecorder.setOnDone(null);
        if (echoEngine.isRecording())    echoEngine.stopRecording();
        if (normalRecorder.isRecording()) normalRecorder.stopRecording();
        stopTimer();
        uiHandler.removeCallbacksAndMessages(null);
        usbHandler.removeCallbacksAndMessages(null);
    }

    private void bindViews() {
        tvTimer      = findViewById(R.id.tvTimer);
        tvStatus     = findViewById(R.id.tvStatus);
        tvUsbStatus  = findViewById(R.id.tvUsbStatus);
        waveformView = findViewById(R.id.waveformView);
        btnRecord    = findViewById(R.id.btnRecord);
        sbDelay      = findViewById(R.id.sbDelay);      tvDelayVal    = findViewById(R.id.tvDelayVal);
        sbFeedback   = findViewById(R.id.sbFeedback);   tvFeedbackVal = findViewById(R.id.tvFeedbackVal);
        sbDry        = findViewById(R.id.sbDry);         tvDryVal      = findViewById(R.id.tvDryVal);
        sbWet        = findViewById(R.id.sbWet);         tvWetVal      = findViewById(R.id.tvWetVal);
        tvPreview    = findViewById(R.id.tvEchoPreview);
        swEcho       = findViewById(R.id.swEcho);
    }

    private void setupEngines() {
        echoEngine = new EchoRecordingEngine(this);
        echoEngine.setOnAmplitude(a -> uiHandler.post(() -> pushAmp(a)));
        echoEngine.setOnDone((f, e) -> uiHandler.post(() -> onDone(f, e)));

        normalRecorder = new AudioRecorderHelper(this);
        normalRecorder.setFormat(AudioRecorderHelper.Format.WAV);
        normalRecorder.setSampleRate(44100); normalRecorder.setBitDepth(16);
        normalRecorder.setOnAmplitude(a -> uiHandler.post(() -> pushAmp(a)));
        normalRecorder.setOnDone((f, e) -> uiHandler.post(() -> onDone(f, e)));
    }

    private void setupSliders() {
        sbDelay.setMax(1950); sbDelay.setProgress(200); tvDelayVal.setText("250 ms");
        sbDelay.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                int ms = p + 50; tvDelayVal.setText(ms + " ms"); echoEngine.setDelayMs(ms); updatePreview();
            }
        });
        sbFeedback.setMax(85); sbFeedback.setProgress(35); tvFeedbackVal.setText("35%");
        sbFeedback.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvFeedbackVal.setText(p + "%"); echoEngine.setFeedback(p / 100f); updatePreview();
            }
        });
        sbDry.setMax(100); sbDry.setProgress(70); tvDryVal.setText("70%");
        sbDry.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvDryVal.setText(p + "%"); echoEngine.setDryMix(p / 100f); updatePreview();
            }
        });
        sbWet.setMax(100); sbWet.setProgress(40); tvWetVal.setText("40%");
        sbWet.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvWetVal.setText(p + "%"); echoEngine.setWetMix(p / 100f); updatePreview();
            }
        });
    }

    private void updateEchoVis() {
        int v = useEcho ? View.VISIBLE : View.GONE;
        for (int id : new int[]{R.id.sbDelay, R.id.tvDelayVal, R.id.tvLabelDelay,
                R.id.sbFeedback, R.id.tvFeedbackVal, R.id.tvLabelFeedback,
                R.id.sbDry, R.id.tvDryVal, R.id.tvLabelDry,
                R.id.sbWet, R.id.tvWetVal, R.id.tvLabelWet}) {
            View vw = findViewById(id); if (vw != null) vw.setVisibility(v);
        }
    }

    private void updatePreview() {
        tvPreview.setText(useEcho
                ? String.format(Locale.US, "🔊 Echo: delay=%dms  feedback=%d%%  dry=%d%%  wet=%d%%",
                        echoEngine.getDelayMs(), (int)(echoEngine.getFeedback()*100),
                        (int)(echoEngine.getDryMix()*100), (int)(echoEngine.getWetMix()*100))
                : "🎙️ Normal recording — no echo processing");
    }

    private void onRecordClick() {
        boolean rec = useEcho ? echoEngine.isRecording() : normalRecorder.isRecording();
        if (rec) stopRec(); else startRec();
    }

    private void startRec() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, PERM_REQ); return;
        }
        amplitudes.clear(); waveformView.setAmplitudes(new float[0]);
        String ts  = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File   dir = new File(getFilesDir(), "recordings");
        if (useEcho) echoEngine.startRecording(dir, "ECHO_" + ts);
        else         normalRecorder.startRecording(dir, "REC_" + ts);
        startMs = System.currentTimeMillis();
        btnRecord.setText("⏹ STOP"); btnRecord.setBackgroundColor(0xFF880000);
        tvStatus.setText(useEcho ? "● RECORDING (Echo Mode)" : "● RECORDING");
        tvStatus.setTextColor(0xFFE53935); startTimer();
        sbDelay.setEnabled(false); sbFeedback.setEnabled(false);
        sbDry.setEnabled(false); sbWet.setEnabled(false); swEcho.setEnabled(false);
    }

    private void stopRec() {
        if (useEcho) echoEngine.stopRecording(); else normalRecorder.stopRecording();
        stopTimer(); btnRecord.setText("⏺ RECORD"); btnRecord.setBackgroundColor(0xFFE53935);
        tvStatus.setText("Saving…"); tvStatus.setTextColor(0xFF888888);
    }

    private void onDone(File file, String error) {
        sbDelay.setEnabled(true); sbFeedback.setEnabled(true);
        sbDry.setEnabled(true);   sbWet.setEnabled(true); swEcho.setEnabled(true);
        if (error != null || file == null) { tvStatus.setText("❌ " + error); return; }
        tvStatus.setText("✅ Saved: " + file.getName()); tvStatus.setTextColor(0xFF4CAF50);
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        startActivity(i);
    }

    private void startTimer() {
        timerRun = new Runnable() {
            @Override public void run() {
                long e = System.currentTimeMillis() - startMs;
                tvTimer.setText(String.format(Locale.US, "%02d:%02d.%03d", e/60000, (e/1000)%60, e%1000));
                uiHandler.postDelayed(this, 50);
            }
        };
        uiHandler.post(timerRun);
    }

    private void stopTimer() { if (timerRun != null) uiHandler.removeCallbacks(timerRun); }

    private void startUsbPoll() {
        usbPollRun = new Runnable() {
            @Override public void run() {
                AudioDeviceInfo usb = AudioRecorderHelper.getUsbInputDevice(EchoRecordActivity.this);
                if (usb != null) {
                    tvUsbStatus.setText("🟢 USB Audio Connected"); tvUsbStatus.setTextColor(0xFF4CAF50);
                    echoEngine.setPreferredDevice(usb);
                } else {
                    tvUsbStatus.setText("⚪ No USB Device"); tvUsbStatus.setTextColor(0xFF888888);
                }
                usbHandler.postDelayed(this, 2000);
            }
        };
        usbHandler.post(usbPollRun);
    }

    private void pushAmp(float amp) {
        amplitudes.add(amp);
        while (amplitudes.size() > 80) amplitudes.remove(0);
        float[] arr = new float[amplitudes.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = amplitudes.get(i);
        waveformView.setAmplitudes(arr);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_REQ && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            onRecordClick();
        else Toast.makeText(this, "Microphone permission required!", Toast.LENGTH_LONG).show();
    }

    private abstract static class SL implements SeekBar.OnSeekBarChangeListener {
        @Override public abstract void onProgressChanged(SeekBar sb, int p, boolean u);
        @Override public void onStartTrackingTouch(SeekBar sb) {}
        @Override public void onStopTrackingTouch(SeekBar sb) {}
    }
}
