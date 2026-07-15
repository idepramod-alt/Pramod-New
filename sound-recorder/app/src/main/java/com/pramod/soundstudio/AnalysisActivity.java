package com.pramod.soundstudio;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import java.io.File;
import java.util.*;

/**
 * Audio Analysis Activity
 * BPM, Peak, RMS, LUFS, Dynamic Range, Frequency, Stereo info, Clipping.
 */
public class AnalysisActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_FILE = 9001;

    private AudioAnalyzerEngine analyzer = new AudioAnalyzerEngine();
    private File selectedFile;
    private TextView tvFileName, tvStatus, tvResult;
    private Button   btnPickFile, btnAnalyze;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvFileName = findViewById(R.id.tvFileName);
        tvStatus   = findViewById(R.id.tvStatus);
        tvResult   = findViewById(R.id.tvResult);
        btnPickFile= findViewById(R.id.btnPickFile);
        btnAnalyze = findViewById(R.id.btnAnalyze);

        btnPickFile.setOnClickListener(v -> pickFile());
        btnAnalyze.setOnClickListener(v -> runAnalysis());
        loadFirstFile();
    }

    private void loadFirstFile() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) return;
        File[] all = dir.listFiles(f -> f.getName().endsWith(".wav"));
        if (all != null && all.length > 0) {
            Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            selectedFile = all[0];
            tvFileName.setText(selectedFile.getName());
        }
    }

    private void pickFile() {
        File dir = new File(getFilesDir(), "recordings");
        File[] internal = dir.exists() ? dir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        List<File> all = new ArrayList<>();
        if (internal != null) all.addAll(Arrays.asList(internal));

        List<String> names = new ArrayList<>();
        for (File f : all) names.add(f.getName());
        names.add("📱 Browse Device Storage…");
        final int browseIndex = names.size() - 1;
        final File[] files = all.toArray(new File[0]);

        new AlertDialog.Builder(this).setTitle("Select WAV")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                if (which == browseIndex) {
                    DeviceFileImporter.launchPicker(this, REQUEST_DEVICE_FILE, false);
                } else {
                    selectedFile = files[which]; tvFileName.setText(files[which].getName());
                }
            }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_FILE && resultCode == RESULT_OK && data != null) {
            File dir = new File(getFilesDir(), "recordings");
            List<File> imported = DeviceFileImporter.handleResult(this, data, dir);
            if (!imported.isEmpty()) {
                selectedFile = imported.get(0);
                tvFileName.setText(selectedFile.getName());
                toast("Imported " + selectedFile.getName());
            }
        }
    }

    private void runAnalysis() {
        if (selectedFile == null) { toast("Select a file first"); return; }
        tvStatus.setText("Analyzing…");
        btnAnalyze.setEnabled(false);
        new Thread(() -> {
            try {
                AudioAnalyzerEngine.AnalysisResult r = analyzer.analyze(selectedFile);
                String report = buildReport(r);
                handler.post(() -> {
                    tvStatus.setText("Analysis complete");
                    tvResult.setText(report);
                    btnAnalyze.setEnabled(true);
                });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("Error: " + e.getMessage()); btnAnalyze.setEnabled(true); });
            }
        }).start();
    }

    private String buildReport(AudioAnalyzerEngine.AnalysisResult r) {
        return "━━━ FILE INFO ━━━\n" +
               "Sample Rate:     " + r.sampleRate + " Hz\n" +
               "Channels:        " + (r.isStereo ? "Stereo (2)" : "Mono (1)") + "\n" +
               "Bit Depth:       " + r.bitDepth + "-bit\n" +
               "Duration:        " + String.format("%.3fs", r.durationSec) + "\n" +
               "File Size:       " + (r.fileSizeBytes / 1024) + " KB\n\n" +
               "━━━ LEVELS ━━━\n" +
               "Peak:            " + String.format("%.2f dBFS", r.peakDB) + "\n" +
               "RMS:             " + String.format("%.2f dBFS", r.rmsDB) + "\n" +
               "Integrated LUFS: " + String.format("%.1f LUFS", r.integratedLUFS) + "\n" +
               "Dynamic Range:   " + String.format("%.1f dB", r.dynamicRangeDB) + "\n\n" +
               "━━━ DIAGNOSTICS ━━━\n" +
               "DC Offset:       " + String.format("%.5f", r.dcOffset) +
                    (Math.abs(r.dcOffset) > 0.01 ? " ⚠️ HIGH" : " ✅ OK") + "\n" +
               "Clipped Samples: " + r.clippedSamples +
                    (r.clippedSamples > 0 ? " ⚠️ CLIPPING" : " ✅ None") + "\n\n" +
               "━━━ RHYTHM ━━━\n" +
               "BPM:             " + (r.bpm > 0 ? String.format("%.1f BPM", r.bpm) : "Not detected") + "\n";
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
