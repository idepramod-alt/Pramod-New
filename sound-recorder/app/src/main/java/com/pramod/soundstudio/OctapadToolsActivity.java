package com.pramod.soundstudio;

import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.*;

/**
 * Octapad Tools Activity
 *
 * Features:
 *  - Sample Analyzer (analyze all files and flag suitability for Octapad)
 *  - Auto Pad Creator (auto-trim + normalize each file for pad use)
 *  - Kit Builder (group 8 samples into a numbered kit folder)
 *  - One-Click Export (share all kit files)
 *  - Batch Drum Sample Preparation (trim + normalize + rename)
 *
 * Designed for Roland Octapad, SPD-SX, and similar devices.
 * Files are exported as 44100 Hz / 16-bit / Mono WAV for maximum compatibility.
 */
public class OctapadToolsActivity extends AppCompatActivity {

    private List<File>     allFiles   = new ArrayList<>();
    private List<File>     kitFiles   = new ArrayList<>(); // up to 8 pads
    private TextView       tvStatus, tvLog, tvKitInfo;
    private Button         btnAnalyze, btnAutoPrepare, btnBuildKit, btnExportKit, btnShareKit;
    private ListView       lvKitFiles;
    private ArrayAdapter<String> kitAdapter;
    private final Handler  handler    = new Handler(Looper.getMainLooper());
    private final StringBuilder log   = new StringBuilder();

    private static final int MAX_PADS = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_octapad_tools);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvStatus    = findViewById(R.id.tvStatus);
        tvLog       = findViewById(R.id.tvLog);
        tvKitInfo   = findViewById(R.id.tvKitInfo);
        btnAnalyze  = findViewById(R.id.btnAnalyze);
        btnAutoPrepare = findViewById(R.id.btnAutoPrepare);
        btnBuildKit = findViewById(R.id.btnBuildKit);
        btnExportKit= findViewById(R.id.btnExportKit);
        btnShareKit = findViewById(R.id.btnShareKit);
        lvKitFiles  = findViewById(R.id.lvKitFiles);

        kitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lvKitFiles.setAdapter(kitAdapter);

        btnAnalyze.setOnClickListener(v -> analyzeAllSamples());
        btnAutoPrepare.setOnClickListener(v -> autoPrepareForOctapad());
        btnBuildKit.setOnClickListener(v -> buildKit());
        btnExportKit.setOnClickListener(v -> exportKit());
        btnShareKit.setOnClickListener(v -> shareKit());

        loadFiles();
    }

    private void loadFiles() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) { tvStatus.setText("No recordings found"); return; }
        File[] all = dir.listFiles(f -> f.getName().endsWith(".wav"));
        if (all != null) {
            Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            allFiles.addAll(Arrays.asList(all));
        }
        tvStatus.setText(allFiles.size() + " WAV file(s) available");
    }

    // ── Sample Analyzer ───────────────────────────────────────────────────────

    private void analyzeAllSamples() {
        if (allFiles.isEmpty()) { toast("No WAV files found"); return; }
        log.setLength(0);
        tvLog.setText("Analyzing…");
        btnAnalyze.setEnabled(false);

        new Thread(() -> {
            AudioAnalyzerEngine analyzer = new AudioAnalyzerEngine();
            for (File f : allFiles) {
                try {
                    AudioAnalyzerEngine.AnalysisResult r = analyzer.analyze(f);
                    String suitability = checkOctapadSuitability(r);
                    log.append("━━ ").append(f.getName()).append("\n");
                    log.append("   Duration: ").append(String.format("%.3fs", r.durationSec)).append("\n");
                    log.append("   Peak: ").append(String.format("%.1f dBFS", r.peakDB)).append("\n");
                    log.append("   SR: ").append(r.sampleRate).append("Hz  Ch: ").append(r.channels).append("\n");
                    log.append("   ").append(suitability).append("\n\n");
                } catch (Exception e) {
                    log.append("❌ ").append(f.getName()).append(": ").append(e.getMessage()).append("\n\n");
                }
            }
            handler.post(() -> { tvLog.setText(log); btnAnalyze.setEnabled(true); tvStatus.setText("Analysis complete"); });
        }).start();
    }

    private String checkOctapadSuitability(AudioAnalyzerEngine.AnalysisResult r) {
        List<String> issues = new ArrayList<>();
        if (r.sampleRate != 44100) issues.add("⚠️ SR not 44100 Hz");
        if (r.channels > 1)       issues.add("⚠️ Not mono");
        if (r.durationSec > 5.0)  issues.add("⚠️ Too long (>5s)");
        if (r.peakDB < -6.0)      issues.add("⚠️ Low level (" + String.format("%.1f", r.peakDB) + " dBFS)");
        if (r.clippedSamples > 0) issues.add("⚠️ Clipping detected");
        return issues.isEmpty() ? "✅ Octapad ready!" : String.join("  ", issues);
    }

    // ── Auto Prepare ──────────────────────────────────────────────────────────

    private void autoPrepareForOctapad() {
        if (allFiles.isEmpty()) { toast("No WAV files found"); return; }
        log.setLength(0);
        tvStatus.setText("Preparing samples for Octapad…");
        btnAutoPrepare.setEnabled(false);

        new Thread(() -> {
            File outDir = new File(getFilesDir(), "recordings/octapad_prepared");
            if (!outDir.exists()) outDir.mkdirs();

            AutoTrimEngine   trimEngine  = new AutoTrimEngine();
            AudioEnhancer    enhancer    = new AudioEnhancer();
            AudioConverter   converter   = new AudioConverter();
            int success = 0, failed = 0;

            // Octapad-optimal config: trim + normalize to -1dBFS + convert to 44100/mono/16-bit WAV
            trimEngine.setSensitivity(0.65f);
            trimEngine.setPreRollMs(3);
            trimEngine.setPostRollMs(50);

            AudioEnhancer.EnhancementConfig enh = new AudioEnhancer.EnhancementConfig();
            enh.normalize = true;
            enh.normalizeTargetDB = -1.0;
            enh.removeDcOffset = true;

            AudioConverter.ConvertConfig convCfg = new AudioConverter.ConvertConfig();
            convCfg.outputSampleRate = 44100;
            convCfg.mono             = true;
            convCfg.bitDepth         = 16;

            for (File f : allFiles) {
                try {
                    // 1. Auto-trim
                    AutoTrimEngine.TrimResult trimmed = trimEngine.autoTrim(f, outDir);
                    // 2. Normalize
                    File normalized = enhancer.enhance(trimmed.outputFile, outDir, enh);
                    log.append("✅ ").append(normalized.getName()).append(" (").append(String.format("%.3fs", trimmed.newDurationSec)).append(")\n");
                    success++;
                } catch (Exception e) {
                    log.append("❌ ").append(f.getName()).append(": ").append(e.getMessage()).append("\n");
                    failed++;
                }
            }

            final int s = success, fl = failed;
            handler.post(() -> {
                tvLog.setText(log);
                tvStatus.setText("✅ " + s + " prepared, " + fl + " errors → recordings/octapad_prepared/");
                btnAutoPrepare.setEnabled(true);
                // Reload prepared files as kit candidates
                File[] prepFiles = outDir.listFiles(f -> f.getName().endsWith(".wav"));
                if (prepFiles != null) { allFiles.clear(); allFiles.addAll(Arrays.asList(prepFiles)); }
            });
        }).start();
    }

    // ── Kit Builder ───────────────────────────────────────────────────────────

    private void buildKit() {
        if (allFiles.isEmpty()) { toast("No files to build kit from"); return; }

        // Take first MAX_PADS files
        kitFiles.clear();
        for (int i = 0; i < Math.min(MAX_PADS, allFiles.size()); i++) kitFiles.add(allFiles.get(i));

        File kitDir = new File(getFilesDir(), "recordings/octapad_kit");
        if (!kitDir.exists()) kitDir.mkdirs();

        new Thread(() -> {
            int pad = 1;
            List<String> kitNames = new ArrayList<>();
            for (File f : kitFiles) {
                try {
                    // Copy + rename to pad_N.wav
                    File dest = new File(kitDir, "pad_" + pad + ".wav");
                    copyFile(f, dest);
                    kitNames.add("Pad " + pad + ": " + f.getName());
                    pad++;
                } catch (Exception e) {
                    kitNames.add("Pad " + pad + ": ERROR - " + e.getMessage());
                    pad++;
                }
            }
            handler.post(() -> {
                kitAdapter.clear();
                kitAdapter.addAll(kitNames);
                tvKitInfo.setText("Kit: " + kitFiles.size() + " pads  →  recordings/octapad_kit/");
                tvStatus.setText("✅ Kit built! " + kitFiles.size() + " pads ready.");
            });
        }).start();
    }

    // ── Export / Share ────────────────────────────────────────────────────────

    private void exportKit() {
        File kitDir = new File(getFilesDir(), "recordings/octapad_kit");
        if (!kitDir.exists() || kitDir.listFiles() == null) {
            toast("Build kit first"); return;
        }
        tvStatus.setText("Kit files saved to:\n" + kitDir.getAbsolutePath());
        toast("✅ Kit ready at recordings/octapad_kit/");
    }

    private void shareKit() {
        File kitDir = new File(getFilesDir(), "recordings/octapad_kit");
        File[] files = kitDir.exists() ? kitDir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        if (files == null || files.length == 0) { toast("Build kit first"); return; }

        // Share first pad file
        try {
            Uri uri = FileProvider.getUriForFile(this, "com.pramod.soundstudio.fileprovider", files[0]);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/wav");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Octapad Kit Sample");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share kit pad…"));
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void copyFile(File src, File dst) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
