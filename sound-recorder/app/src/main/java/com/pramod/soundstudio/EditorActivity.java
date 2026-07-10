package com.pramod.soundstudio;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;

public class EditorActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";

    // UI
    private TextView     tvFileName, tvDuration, tvAutoTrimResult;
    private WaveformView waveformView;
    private EditText     etStartSec, etEndSec;
    private TextView     tvDurationDisplay;
    private Button       btnAutoTrim, btnApplyTrim, btnPlay, btnSave, btnLoadOctapad;

    // Data
    private File    sourceFile;
    private File    workingFile;      // current file (may be already-trimmed)
    private short[] pcmSamples;
    private int     sampleRate   = 44100;
    private int     channels     = 1;
    private double  totalDurSec  = 0;
    private double  trimStart    = 0;
    private double  trimEnd      = 0;

    // Playback
    private MediaPlayer mediaPlayer;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable playbackUpdater;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        tvFileName       = findViewById(R.id.tvFileName);
        tvDuration       = findViewById(R.id.tvDuration);
        tvAutoTrimResult = findViewById(R.id.tvAutoTrimResult);
        waveformView     = findViewById(R.id.waveformView);
        etStartSec       = findViewById(R.id.etStartSec);
        etEndSec         = findViewById(R.id.etEndSec);
        tvDurationDisplay= findViewById(R.id.tvDurationDisplay);
        btnAutoTrim      = findViewById(R.id.btnAutoTrim);
        btnApplyTrim     = findViewById(R.id.btnApplyTrim);
        btnPlay          = findViewById(R.id.btnPlay);
        btnSave          = findViewById(R.id.btnSave);
        btnLoadOctapad   = findViewById(R.id.btnLoadOctapad);

        // Back
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (path == null) { finish(); return; }

        sourceFile  = new File(path);
        workingFile = sourceFile;

        loadFile(workingFile);
        setupButtons();
    }

    // ── File loading ─────────────────────────────────────────────────────────

    private void loadFile(File file) {
        tvFileName.setText(file.getName());

        if (!file.getName().endsWith(".wav")) {
            // Non-WAV: just show file info, no waveform
            tvDuration.setText(file.length() / 1024 + " KB  •  " + file.getName());
            tvAutoTrimResult.setText("ℹ️ Auto trim only supported for WAV files");
            btnAutoTrim.setEnabled(false);
            btnApplyTrim.setEnabled(false);
            return;
        }

        new Thread(() -> {
            try {
                int[]  hdr    = AudioTrimmer.readWavHeader(file);
                sampleRate    = hdr[0];
                channels      = hdr[1];
                pcmSamples    = AudioTrimmer.readWavPcm(file);
                totalDurSec   = (double) pcmSamples.length / (sampleRate * channels);
                trimStart     = 0;
                trimEnd       = totalDurSec;

                float[] bars  = AudioTrimmer.toWaveformAmplitudes(pcmSamples, 100);

                uiHandler.post(() -> {
                    String info = String.format(Locale.US,
                            "%.2fs  •  %dHz  •  %d KB",
                            totalDurSec, sampleRate, file.length() / 1024);
                    tvDuration.setText(info);
                    waveformView.setAmplitudes(bars);
                    waveformView.setTrimRegion(0f, 1f);
                    etStartSec.setText(String.format(Locale.US, "%.3f", trimStart));
                    etEndSec.setText(String.format(Locale.US, "%.3f", trimEnd));
                    updateDurationDisplay();
                });
            } catch (Exception e) {
                uiHandler.post(() ->
                    tvDuration.setText("Error loading file: " + e.getMessage()));
            }
        }).start();
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    private void setupButtons() {

        // Auto Trim
        btnAutoTrim.setOnClickListener(v -> runAutoTrim());

        // Apply manual trim
        btnApplyTrim.setOnClickListener(v -> applyManualTrim());

        // Play/Stop
        btnPlay.setOnClickListener(v -> togglePlayback());

        // Save (overwrite original with working file)
        btnSave.setOnClickListener(v -> {
            Toast.makeText(this, "✅ Saved: " + workingFile.getName(), Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });

        // Load to Octapad
        btnLoadOctapad.setOnClickListener(v -> loadToOctapad());

        // Live trim range update when user edits fields
        etStartSec.setOnFocusChangeListener((v, f) -> { if (!f) syncTrimFromFields(); });
        etEndSec.setOnFocusChangeListener((v, f)   -> { if (!f) syncTrimFromFields(); });
    }

    // ── Auto Trim ────────────────────────────────────────────────────────────

    private void runAutoTrim() {
        if (pcmSamples == null || !workingFile.getName().endsWith(".wav")) return;

        btnAutoTrim.setEnabled(false);
        btnAutoTrim.setText("⏳ Trimming…");
        tvAutoTrimResult.setText("");

        new Thread(() -> {
            try {
                File outDir = workingFile.getParentFile();
                AudioTrimmer.TrimResult result =
                        AudioTrimmer.autoTrimWav(workingFile, 300, 50, outDir);

                // Reload the new file
                int[]  hdr    = AudioTrimmer.readWavHeader(result.outputFile);
                sampleRate    = hdr[0];
                channels      = hdr[1];
                pcmSamples    = AudioTrimmer.readWavPcm(result.outputFile);
                totalDurSec   = result.newDurationSec;
                trimStart     = 0;
                trimEnd       = totalDurSec;
                workingFile   = result.outputFile;

                float[] bars  = AudioTrimmer.toWaveformAmplitudes(pcmSamples, 100);

                String msg = String.format(Locale.US,
                        "✅ Removed %.2fs start + %.2fs end  •  New: %.2fs",
                        result.removedStartSec, result.removedEndSec, result.newDurationSec);

                uiHandler.post(() -> {
                    tvAutoTrimResult.setText(msg);
                    tvAutoTrimResult.setTextColor(0xFF4CAF50);
                    waveformView.setAmplitudes(bars);
                    waveformView.setTrimRegion(0f, 1f);
                    etStartSec.setText(String.format(Locale.US, "%.3f", trimStart));
                    etEndSec.setText(String.format(Locale.US, "%.3f", trimEnd));
                    tvFileName.setText(workingFile.getName());
                    tvDuration.setText(String.format(Locale.US,
                            "%.2fs  •  %dHz  •  %d KB",
                            totalDurSec, sampleRate, workingFile.length() / 1024));
                    updateDurationDisplay();
                    btnAutoTrim.setEnabled(true);
                    btnAutoTrim.setText("⚡ AUTO TRIM");
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    tvAutoTrimResult.setText("❌ " + e.getMessage());
                    btnAutoTrim.setEnabled(true);
                    btnAutoTrim.setText("⚡ AUTO TRIM");
                });
            }
        }).start();
    }

    // ── Manual Trim ──────────────────────────────────────────────────────────

    private void syncTrimFromFields() {
        try {
            trimStart = Double.parseDouble(etStartSec.getText().toString());
            trimEnd   = Double.parseDouble(etEndSec.getText().toString());
            trimStart = Math.max(0, Math.min(trimStart, totalDurSec));
            trimEnd   = Math.max(trimStart, Math.min(trimEnd, totalDurSec));
            waveformView.setTrimRegion(
                    (float)(trimStart / totalDurSec),
                    (float)(trimEnd   / totalDurSec));
            updateDurationDisplay();
        } catch (NumberFormatException ignored) {}
    }

    private void updateDurationDisplay() {
        if (tvDurationDisplay == null) return;
        double dur = trimEnd - trimStart;
        tvDurationDisplay.setText(String.format(Locale.US, "Duration: %.3fs", dur));
    }

    private void applyManualTrim() {
        syncTrimFromFields();
        if (trimEnd <= trimStart || !workingFile.getName().endsWith(".wav")) {
            Toast.makeText(this, "Invalid trim range", Toast.LENGTH_SHORT).show();
            return;
        }

        btnApplyTrim.setEnabled(false);
        btnApplyTrim.setText("⏳ Trimming…");

        double ts = trimStart, te = trimEnd;
        new Thread(() -> {
            try {
                File outDir = workingFile.getParentFile();
                File result = AudioTrimmer.trimWav(workingFile, ts, te, outDir);

                // Reload
                int[]  hdr = AudioTrimmer.readWavHeader(result);
                sampleRate = hdr[0]; channels = hdr[1];
                pcmSamples = AudioTrimmer.readWavPcm(result);
                totalDurSec= (double) pcmSamples.length / (sampleRate * channels);
                trimStart  = 0; trimEnd = totalDurSec;
                workingFile= result;

                float[] bars = AudioTrimmer.toWaveformAmplitudes(pcmSamples, 100);

                uiHandler.post(() -> {
                    waveformView.setAmplitudes(bars);
                    waveformView.setTrimRegion(0f, 1f);
                    etStartSec.setText(String.format(Locale.US, "%.3f", 0.0));
                    etEndSec.setText(String.format(Locale.US, "%.3f", totalDurSec));
                    tvFileName.setText(workingFile.getName());
                    tvDuration.setText(String.format(Locale.US,
                            "%.2fs  •  %dHz  •  %d KB",
                            totalDurSec, sampleRate, workingFile.length() / 1024));
                    updateDurationDisplay();
                    btnApplyTrim.setEnabled(true);
                    btnApplyTrim.setText("✂️ Apply Trim");
                    Toast.makeText(this, "✅ Trim applied!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    Toast.makeText(this, "Trim failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnApplyTrim.setEnabled(true);
                    btnApplyTrim.setText("✂️ Apply Trim");
                });
            }
        }).start();
    }

    // ── Playback ─────────────────────────────────────────────────────────────

    private void togglePlayback() {
        if (isPlaying) {
            stopPlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        try {
            stopPlayback();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(workingFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            btnPlay.setText("⏹ Stop");
            mediaPlayer.setOnCompletionListener(mp -> uiHandler.post(this::stopPlayback));
        } catch (Exception e) {
            Toast.makeText(this, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
        btnPlay.setText("▶ Play");
    }

    // ── Load to Octapad ──────────────────────────────────────────────────────

    private void loadToOctapad() {
        try {
            android.net.Uri uri = FileProvider.getUriForFile(
                    this,
                    "com.pramod.soundstudio.fileprovider",
                    workingFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "audio/wav");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Try to open with the main Octapad app first
            intent.setPackage("com.pramod.loopmidi");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback: share picker
                intent.setPackage(null);
                Intent chooser = Intent.createChooser(intent, "Open with…");
                startActivity(chooser);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
        uiHandler.removeCallbacksAndMessages(null);
    }
}
