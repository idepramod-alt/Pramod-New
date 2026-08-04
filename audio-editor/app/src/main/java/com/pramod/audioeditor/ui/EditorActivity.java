package com.pramod.audioeditor.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pramod.audioeditor.R;
import com.pramod.audioeditor.data.EditModel;
import com.pramod.audioeditor.data.EqSettings;
import com.pramod.audioeditor.data.ExportSettings;
import com.pramod.audioeditor.data.MappedPcmSource;
import com.pramod.audioeditor.data.PcmSource;
import com.pramod.audioeditor.engine.AudioDecoder;
import com.pramod.audioeditor.engine.ExportEngine;
import com.pramod.audioeditor.engine.LimiterDsp;
import com.pramod.audioeditor.engine.OfflineRenderEngine;
import com.pramod.audioeditor.engine.PlaybackEngine;
import com.pramod.audioeditor.engine.RealtimeEq;
import com.pramod.audioeditor.engine.WaveformPyramid;
import com.pramod.audioeditor.view.EqPanelView;
import com.pramod.audioeditor.view.ProWaveformView;
import com.pramod.audioeditor.view.TransportBarView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main DAW editor activity. Landscape-only.
 * Handles: file loading, waveform rendering, playback, trim, split, EQ, export.
 */
public class EditorActivity extends AppCompatActivity {

    // Views
    private ProWaveformView waveformView;
    private TransportBarView transportBar;
    private EqPanelView eqPanel;
    private TextView txtFileName, txtSelectionInfo, txtTrimInfo, txtLoading;
    private LinearLayout loadingPanel;

    // Engine
    private PcmSource pcmSource;
    private WaveformPyramid pyramid;
    private PlaybackEngine playbackEngine;
    private RealtimeEq realtimeEq;
    private LimiterDsp limiter;
    private final EditModel editModel = new EditModel();
    private final EqSettings eqSettings = new EqSettings();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // State
    private File canonicalWav;
    private Uri sourceUri;
    private boolean isPlayingSelection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        initViews();
        setupListeners();

        // Handle incoming audio URI
        String uriStr = getIntent().getStringExtra("AUDIO_URI");
        if (uriStr != null) {
            sourceUri = Uri.parse(uriStr);
            loadAudioFile(sourceUri);
        }
    }

    private void initViews() {
        waveformView = findViewById(R.id.waveformView);
        transportBar = findViewById(R.id.transportBar);
        eqPanel = findViewById(R.id.eqPanel);
        txtFileName = findViewById(R.id.txtFileName);
        txtSelectionInfo = findViewById(R.id.txtSelectionInfo);
        txtTrimInfo = findViewById(R.id.txtTrimInfo);
        txtLoading = findViewById(R.id.txtLoading);
        loadingPanel = findViewById(R.id.loadingPanel);
    }

    private void setupListeners() {
        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Open button
        findViewById(R.id.btnOpen).setOnClickListener(v -> openAudioFile());

        // Export button
        findViewById(R.id.btnExport).setOnClickListener(v -> showExportDialog());

        // Mode buttons
        Button btnSelect = findViewById(R.id.btnModeSelect);
        Button btnTrim = findViewById(R.id.btnModeTrim);
        Button btnSplit = findViewById(R.id.btnModeSplit);
        Button btnEq = findViewById(R.id.btnModeEq);

        btnSelect.setOnClickListener(v -> setMode(EditModel.Mode.SELECT, btnSelect));
        btnTrim.setOnClickListener(v -> setMode(EditModel.Mode.TRIM, btnTrim));
        btnSplit.setOnClickListener(v -> setMode(EditModel.Mode.SPLIT, btnSplit));
        btnEq.setOnClickListener(v -> setMode(EditModel.Mode.EQ, btnEq));

        // Transport
        transportBar.setListener(new TransportBarView.Listener() {
            @Override public void onPlayPause() {
                if (playbackEngine.isPlaying()) {
                    playbackEngine.pause();
                    transportBar.setPlaying(false);
                } else {
                    playFromPlayhead();
                }
            }
            @Override public void onStop() {
                playbackEngine.stop();
                transportBar.setPlaying(false);
            }
        });

        // Play Selection
        findViewById(R.id.btnPlaySelection).setOnClickListener(v -> playSelection());

        // Apply Trim
        findViewById(R.id.btnApplyTrim).setOnClickListener(v -> applyTrim());

        // Split
        findViewById(R.id.btnSplit).setOnClickListener(v -> splitAtPlayhead());

        // EQ panel
        eqPanel.setListener(settings -> {
            eqSettings.lowDb = settings.lowDb;
            eqSettings.midDb = settings.midDb;
            eqSettings.highDb = settings.highDb;
            eqSettings.gainDb = settings.gainDb;
            eqSettings.bypass = settings.bypass;
            eqSettings.clipGuard = settings.clipGuard;
            if (realtimeEq != null) realtimeEq.setSettings(eqSettings);
        });

        // Waveform listener
        waveformView.setListener(new ProWaveformView.Listener() {
            @Override public void onTrimChanged(long start, long end) {
                editModel.setTrim(start, end);
                updateTrimInfo();
            }
            @Override public void onPlayheadChanged(long frame) {
                transportBar.updateTime(frame, editModel.getTotalFrames());
            }
            @Override public void onZoomChanged(double fpp, long anchor) {}
            @Override public void onSelectionChanged(long start, long end) {
                editModel.setSelection(start, end);
                updateSelectionInfo();
            }
            @Override public void onCutAdded(long frame) {
                long snapped = EditModel.snapToZeroCrossing(pcmSource, frame, 256);
                editModel.addCut(snapped);
                waveformView.setCutPoints(editModel.getCutPoints().stream().mapToLong(l -> l).toArray());
            }
        });
    }

    // ── File Loading ─────────────────────────────────────────────────────────

    private void openAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (Exception ignored) {}
                loadAudioFile(uri);
            }
        }
    }

    private void loadAudioFile(Uri uri) {
        showLoading("Decoding audio...");
        executor.execute(() -> {
            try {
                // Copy to cache
                File cacheDir = new File(getCacheDir(), "editor");
                cacheDir.mkdirs();
                File inputFile = new File(cacheDir, "input_" + System.currentTimeMillis());

                // Copy URI to file
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                java.io.FileOutputStream out = new java.io.FileOutputStream(inputFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                out.close();

                // Decode to canonical WAV
                canonicalWav = new File(cacheDir, "canonical_" + System.currentTimeMillis() + ".wav");
                AudioDecoder.decodeToWav(inputFile, canonicalWav, new AudioDecoder.ProgressListener() {
                    @Override public void onProgress(int pct) {
                        mainHandler.post(() -> txtLoading.setText("Decoding... " + pct + "%"));
                    }
                    @Override public void onComplete(File f) {
                        mainHandler.post(() -> loadCanonicalWav(f));
                    }
                    @Override public void onError(String msg) {
                        mainHandler.post(() -> {
                            hideLoading();
                            Toast.makeText(EditorActivity.this, "Error: " + msg, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(EditorActivity.this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadCanonicalWav(File wavFile) {
        txtLoading.setText("Building waveform...");
        executor.execute(() -> {
            try {
                pcmSource = MappedPcmSource.open(wavFile);
                editModel.setTotalFrames(pcmSource.totalFrames());
                editModel.setTrim(0, pcmSource.totalFrames());

                // Build waveform pyramid
                pyramid = WaveformPyramid.build(pcmSource, pct ->
                        mainHandler.post(() -> txtLoading.setText("Waveform... " + pct + "%")));

                // Init engines
                realtimeEq = new RealtimeEq();
                limiter = new LimiterDsp();
                limiter.configure(-0.5f, 1f, 50f, pcmSource.sampleRate());

                playbackEngine = new PlaybackEngine();
                playbackEngine.configure(pcmSource);
                playbackEngine.setEq(realtimeEq);
                playbackEngine.setLimiter(limiter);
                playbackEngine.setListener(new PlaybackEngine.Listener() {
                    @Override public void onPositionUpdate(long frame) {
                        mainHandler.post(() -> {
                            waveformView.setPlayhead(frame);
                            transportBar.updateTime(frame, editModel.getTotalFrames());
                        });
                    }
                    @Override public void onPlaybackComplete() {
                        mainHandler.post(() -> {
                            transportBar.setPlaying(false);
                            waveformView.setPlaying(false);
                        });
                    }
                });

                // Update UI
                mainHandler.post(() -> {
                    hideLoading();
                    waveformView.setPyramid(pyramid);
                    waveformView.setTrimMarkers(editModel.getTrimStart(), editModel.getTrimEnd());
                    transportBar.setSampleRate(pcmSource.sampleRate());
                    transportBar.updateTime(0, editModel.getTotalFrames());

                    String name = sourceUri != null ? sourceUri.getLastPathSegment() : "audio";
                    if (name == null) name = "audio";
                    txtFileName.setText(name);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(EditorActivity.this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Mode Switching ───────────────────────────────────────────────────────

    private void setMode(EditModel.Mode mode, Button activeBtn) {
        editModel.setMode(mode);
        waveformView.setMode(mode);

        // Reset all mode buttons
        int[] btnIds = {R.id.btnModeSelect, R.id.btnModeTrim, R.id.btnModeSplit, R.id.btnModeEq};
        for (int id : btnIds) {
            findViewById(id).setBackgroundColor(0xFF333333);
        }
        activeBtn.setBackgroundColor(0xFF00CCAA);

        // Show/hide EQ panel
        eqPanel.setVisibility(mode == EditModel.Mode.EQ ? View.VISIBLE : View.GONE);
    }

    // ── Playback ─────────────────────────────────────────────────────────────

    private void playFromPlayhead() {
        if (pcmSource == null || playbackEngine == null) return;
        playbackEngine.play(editModel.activeStart(), editModel.activeEnd());
        transportBar.setPlaying(true);
        waveformView.setPlaying(true);
    }

    private void playSelection() {
        if (pcmSource == null || playbackEngine == null) return;
        if (!editModel.hasSelection()) {
            Toast.makeText(this, "Pehle selection karein", Toast.LENGTH_SHORT).show();
            return;
        }
        isPlayingSelection = true;
        playbackEngine.play(editModel.getSelectionStart(), editModel.getSelectionEnd());
        transportBar.setPlaying(true);
        waveformView.setPlaying(true);
    }

    // ── Trim ─────────────────────────────────────────────────────────────────

    private void applyTrim() {
        if (pcmSource == null || canonicalWav == null) return;
        long start = editModel.getTrimStart();
        long end = editModel.getTrimEnd();
        if (end <= start) {
            Toast.makeText(this, "Trim range set karein", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Trimming...");
        playbackEngine.stop();

        executor.execute(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "editor");
                File trimmed = new File(cacheDir, "trimmed_" + System.currentTimeMillis() + ".wav");

                // Read region and write to new file
                int sr = pcmSource.sampleRate();
                int ch = pcmSource.channels();
                com.pramod.audioeditor.data.WavFileWriter writer = new com.pramod.audioeditor.data.WavFileWriter();
                writer.open(trimmed, sr, ch, 16);

                float[] buf = new float[4096 * ch];
                long totalFrames = end - start;
                for (long pos = start; pos < end; ) {
                    int count = (int) Math.min(4096, end - pos);
                    pcmSource.readFrames(pos, count, buf, 0, false);
                    short[] shortBuf = new short[count * ch];
                    for (int i = 0; i < count * ch; i++) {
                        shortBuf[i] = (short)(Math.max(-1f, Math.min(1f, buf[i])) * 32767f);
                    }
                    writer.writeShort(shortBuf, 0, shortBuf.length);
                    pos += count;
                }
                writer.close();

                // Replace canonical file
                canonicalWav.delete();
                trimmed.renameTo(canonicalWav);

                // Reload
                if (pcmSource != null) pcmSource.release();
                pcmSource = MappedPcmSource.open(canonicalWav);
                editModel.setTotalFrames(pcmSource.totalFrames());
                editModel.setTrim(0, pcmSource.totalFrames());
                pyramid = WaveformPyramid.build(pcmSource, null);

                mainHandler.post(() -> {
                    hideLoading();
                    waveformView.setPyramid(pyramid);
                    waveformView.setTrimMarkers(0, pcmSource.totalFrames());
                    transportBar.updateTime(0, editModel.getTotalFrames());
                    Toast.makeText(this, "Trim applied!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, "Trim failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Split ────────────────────────────────────────────────────────────────

    private void splitAtPlayhead() {
        long frame = editModel.activeStart();
        if (frame <= 0 || frame >= editModel.getTotalFrames()) {
            Toast.makeText(this, "Pehle playhead position set karein", Toast.LENGTH_SHORT).show();
            return;
        }
        long snapped = EditModel.snapToZeroCrossing(pcmSource, frame, 256);
        editModel.addCut(snapped);
        waveformView.setCutPoints(editModel.getCutPoints().stream().mapToLong(l -> l).toArray());
        Toast.makeText(this, "Cut at " + formatFrame(snapped), Toast.LENGTH_SHORT).show();
    }

    // ── Export ───────────────────────────────────────────────────────────────

    private void showExportDialog() {
        if (pcmSource == null) {
            Toast.makeText(this, "Pehle audio file load karein", Toast.LENGTH_SHORT).show();
            return;
        }

        ExportSettings settings = new ExportSettings();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        // Format
        TextView fmtLabel = new TextView(this);
        fmtLabel.setText("Format:");
        fmtLabel.setTextColor(0xFFAAAAAA);
        layout.addView(fmtLabel);

        android.widget.RadioGroup rgFormat = new android.widget.RadioGroup(this);
        android.widget.RadioButton rbWav = new android.widget.RadioButton(this);
        rbWav.setText("WAV (Lossless)");
        rbWav.setTextColor(0xFFFFFFFF);
        rbWav.setChecked(true);
        rbWav.setId(1001);
        rgFormat.addView(rbWav);

        android.widget.RadioButton rbMp3 = new android.widget.RadioButton(this);
        rbMp3.setText("MP3 (Compressed)");
        rbMp3.setTextColor(0xFFFFFFFF);
        rbMp3.setId(1002);
        rgFormat.addView(rbMp3);
        layout.addView(rgFormat);

        rgFormat.setOnCheckedChangeListener((group, checkedId) ->
                settings.format = checkedId == 1002 ? ExportSettings.Format.MP3 : ExportSettings.Format.WAV);

        // Sample rate
        TextView srLabel = new TextView(this);
        srLabel.setText("Sample Rate:");
        srLabel.setTextColor(0xFFAAAAAA);
        srLabel.setPadding(0, dp(8), 0, 0);
        layout.addView(srLabel);

        android.widget.Spinner spinnerSr = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> srAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"22050 Hz", "32000 Hz", "44100 Hz", "48000 Hz"});
        srAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSr.setAdapter(srAdapter);
        spinnerSr.setSelection(2); // 44100 default
        spinnerSr.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                settings.sampleRate = ExportSettings.SAMPLE_RATES[pos];
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        layout.addView(spinnerSr);

        // Export selection checkbox
        android.widget.CheckBox cbSelection = new android.widget.CheckBox(this);
        cbSelection.setText("Export selection only");
        cbSelection.setTextColor(0xFFAAAAAA);
        cbSelection.setChecked(editModel.hasSelection());
        cbSelection.setEnabled(editModel.hasSelection());
        cbSelection.setOnCheckedChangeListener((b, checked) -> settings.exportSelection = checked);
        layout.addView(cbSelection);

        new AlertDialog.Builder(this)
                .setTitle("Export Settings")
                .setView(layout)
                .setPositiveButton("Export", (d, w) -> doExport(settings))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doExport(ExportSettings settings) {
        showLoading("Exporting...");
        playbackEngine.stop();

        executor.execute(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "exports");
                cacheDir.mkdirs();

                File exported = ExportEngine.export(editModel, pcmSource, settings, cacheDir, eqSettings,
                        new ExportEngine.ProgressListener() {
                            @Override public void onProgress(int pct) {
                                mainHandler.post(() -> txtLoading.setText("Exporting... " + pct + "%"));
                            }
                            @Override public void onComplete(File f) {
                                mainHandler.post(() -> {
                                    hideLoading();
                                    Toast.makeText(EditorActivity.this, "Exported: " + f.getName(), Toast.LENGTH_LONG).show();
                                });
                            }
                            @Override public void onError(String msg) {
                                mainHandler.post(() -> {
                                    hideLoading();
                                    Toast.makeText(EditorActivity.this, "Export failed: " + msg, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void showLoading(String msg) {
        loadingPanel.setVisibility(View.VISIBLE);
        txtLoading.setText(msg);
    }

    private void hideLoading() {
        loadingPanel.setVisibility(View.GONE);
    }

    private void updateSelectionInfo() {
        if (editModel.hasSelection()) {
            txtSelectionInfo.setText("Selection: " + formatFrame(editModel.getSelectionStart())
                    + " → " + formatFrame(editModel.getSelectionEnd()));
        } else {
            txtSelectionInfo.setText("Selection: --");
        }
    }

    private void updateTrimInfo() {
        txtTrimInfo.setText("Trim: " + formatFrame(editModel.getTrimStart())
                + " → " + formatFrame(editModel.getTrimEnd()));
    }

    private String formatFrame(long frame) {
        double sec = frame / (double)(pcmSource != null ? pcmSource.sampleRate() : 44100);
        int mins = (int)(sec / 60);
        double secs = sec % 60;
        return String.format("%02d:%05.2f", mins, secs);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackEngine != null) playbackEngine.release();
        if (pcmSource != null) pcmSource.release();
        executor.shutdown();
    }
}
