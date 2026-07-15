package com.pramod.soundstudio;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import java.io.File;
import java.util.*;

/**
 * RhythmSlicerActivity — UI for the Ultra-Precision AI Rhythm Slicer.
 *
 * Workflow:
 *  1. Pick WAV file from internal recordings
 *  2. Adjust sensitivity / pre-roll / post-roll / min-gap
 *  3. Detect Slices → list shows each hit: type emoji, time, dBFS, duration
 *  4. Tap any slice to preview it in EditorActivity
 *  5. Export all slices | Build Octapad kit | Share as ZIP | Save to Music
 *  6. Batch prepare: auto-trim + normalize + 44100/mono for all slices
 */
public class RhythmSlicerActivity extends AppCompatActivity {

    // ── UI refs ──────────────────────────────────────────────────────────────
    private TextView       tvStatus, tvSummary, tvSelectedFile, tvLog;
    private Button         btnPickFile, btnDetect, btnExportAll, btnBuildKit,
                           btnShareKit, btnSaveDownloads, btnBatchPrep;
    private SeekBar        sbSensitivity, sbPreRoll, sbPostRoll, sbMinGap;
    private TextView       tvSensVal, tvPreRollVal, tvPostRollVal, tvMinGapVal;
    private ListView       lvSlices;
    private ScrollView     scrollLog;

    // ── State ────────────────────────────────────────────────────────────────
    private File                           selectedFile;
    private RhythmSlicerEngine             engine;
    private RhythmSlicerEngine.SliceResult result;
    private final List<String>             sliceLabels = new ArrayList<>();
    private ArrayAdapter<String>           sliceAdapter;
    private final Handler                  handler     = new Handler(Looper.getMainLooper());
    private final StringBuilder            log         = new StringBuilder();

    // ── Config defaults ───────────────────────────────────────────────────────
    private float sensitivity = 0.50f;
    private int   preRollMs   = 5;
    private int   postRollMs  = 80;
    private int   minGapMs    = 50;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rhythm_slicer);
        bindViews();
        setupSliders();
        engine       = new RhythmSlicerEngine();
        sliceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, sliceLabels);
        lvSlices.setAdapter(sliceAdapter);
        lvSlices.setOnItemClickListener((p, v, pos, id) -> {
            if (result != null && pos < result.slices.size())
                previewSlice(result.slices.get(pos));
        });
        btnPickFile.setOnClickListener(v -> pickFile());
        btnDetect.setOnClickListener(v -> detectSlices());
        btnExportAll.setOnClickListener(v -> exportAll());
        btnBuildKit.setOnClickListener(v -> buildKit());
        btnShareKit.setOnClickListener(v -> shareKit());
        btnSaveDownloads.setOnClickListener(v -> saveToDownloads());
        btnBatchPrep.setOnClickListener(v -> batchPrepare());
        setExportEnabled(false);
    }

    private void bindViews() {
        tvStatus       = findViewById(R.id.tvStatus);
        tvSummary      = findViewById(R.id.tvSummary);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvLog          = findViewById(R.id.tvLog);
        btnPickFile    = findViewById(R.id.btnPickFile);
        btnDetect      = findViewById(R.id.btnDetect);
        btnExportAll   = findViewById(R.id.btnExportAll);
        btnBuildKit    = findViewById(R.id.btnBuildKit);
        btnShareKit    = findViewById(R.id.btnShareKit);
        btnSaveDownloads = findViewById(R.id.btnSaveDownloads);
        btnBatchPrep   = findViewById(R.id.btnBatchPrep);
        sbSensitivity  = findViewById(R.id.sbSensitivity);
        sbPreRoll      = findViewById(R.id.sbPreRoll);
        sbPostRoll     = findViewById(R.id.sbPostRoll);
        sbMinGap       = findViewById(R.id.sbMinGap);
        tvSensVal      = findViewById(R.id.tvSensVal);
        tvPreRollVal   = findViewById(R.id.tvPreRollVal);
        tvPostRollVal  = findViewById(R.id.tvPostRollVal);
        tvMinGapVal    = findViewById(R.id.tvMinGapVal);
        lvSlices       = findViewById(R.id.lvSlices);
        scrollLog      = findViewById(R.id.scrollLog);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupSliders() {
        // Sensitivity 0–100 → 0.0–1.0
        sbSensitivity.setMax(100); sbSensitivity.setProgress(50); tvSensVal.setText("0.50");
        sbSensitivity.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                sensitivity = p / 100f; tvSensVal.setText(String.format(Locale.US, "%.2f", sensitivity));
            }
        });
        // Pre-roll 0–50 ms
        sbPreRoll.setMax(50); sbPreRoll.setProgress(5); tvPreRollVal.setText("5 ms");
        sbPreRoll.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                preRollMs = p; tvPreRollVal.setText(p + " ms");
            }
        });
        // Post-roll 20–300 ms (offset 20)
        sbPostRoll.setMax(280); sbPostRoll.setProgress(60); tvPostRollVal.setText("80 ms");
        sbPostRoll.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                postRollMs = p + 20; tvPostRollVal.setText(postRollMs + " ms");
            }
        });
        // Min gap 20–500 ms (offset 20)
        sbMinGap.setMax(480); sbMinGap.setProgress(30); tvMinGapVal.setText("50 ms");
        sbMinGap.setOnSeekBarChangeListener(new SL() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                minGapMs = p + 20; tvMinGapVal.setText(minGapMs + " ms");
            }
        });
    }

    // ── File picker ───────────────────────────────────────────────────────────
    private void pickFile() {
        File dir = new File(getFilesDir(), "recordings");
        File[] wavs = dir.exists() ? dir.listFiles(f -> f.isFile() && f.getName().endsWith(".wav")) : null;
        if (wavs == null || wavs.length == 0) { toast("No WAV files in recordings/"); return; }
        Arrays.sort(wavs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] names = new String[wavs.length];
        for (int i = 0; i < wavs.length; i++) names[i] = wavs[i].getName();
        final File[] fw = wavs;
        new AlertDialog.Builder(this).setTitle("📂 Select WAV File")
            .setItems(names, (d, w) -> {
                selectedFile = fw[w];
                tvSelectedFile.setText("📄 " + selectedFile.getName()
                        + "  (" + UniversalExportHelper.formatSize(selectedFile.length()) + ")");
                tvStatus.setText("File selected. Adjust settings and tap Detect Slices.");
                result = null; sliceLabels.clear(); sliceAdapter.notifyDataSetChanged();
                setExportEnabled(false); clearLog();
            }).setNegativeButton("Cancel", null).show();
    }

    // ── Detect ────────────────────────────────────────────────────────────────
    private void detectSlices() {
        if (selectedFile == null) { toast("Pick a WAV file first"); return; }
        tvStatus.setText("⚙️ Analyzing…");
        btnDetect.setEnabled(false); setExportEnabled(false);
        sliceLabels.clear(); sliceAdapter.notifyDataSetChanged(); clearLog();
        engine.setSensitivity(sensitivity); engine.setPreRollMs(preRollMs);
        engine.setPostRollMs(postRollMs);   engine.setMinGapMs(minGapMs);

        new Thread(() -> {
            try {
                result = engine.detectSlices(selectedFile);
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < result.slices.size(); i++) {
                    RhythmSlicerEngine.SlicePoint sp = result.slices.get(i);
                    labels.add(String.format(Locale.US,
                            "%3d │ %s %-7s │ @%.3fs │ %.1fdBFS │ %.3fs%s%s",
                            i + 1, sp.type.emoji(), sp.type.name(),
                            (float) sp.onsetSample / result.sampleRate,
                            sp.energyDb, sp.durationSec,
                            sp.isRoll ? " [ROLL]" : "", sp.isFill ? " [FILL]" : ""));
                }
                handler.post(() -> {
                    sliceLabels.clear(); sliceLabels.addAll(labels);
                    sliceAdapter.notifyDataSetChanged();
                    tvSummary.setText(result.patternSummary);
                    tvSummary.setVisibility(View.VISIBLE);
                    tvStatus.setText("✅ " + result.slices.size() + " slices"
                            + (result.bpm > 0 ? String.format(Locale.US, " • %.1f BPM", result.bpm) : ""));
                    btnDetect.setEnabled(true);
                    setExportEnabled(!result.slices.isEmpty());
                });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("❌ " + e.getMessage()); btnDetect.setEnabled(true); });
            }
        }).start();
    }

    // ── Export all ────────────────────────────────────────────────────────────
    private void exportAll() {
        if (result == null) return;
        tvStatus.setText("Exporting " + result.slices.size() + " slices…");
        new Thread(() -> {
            try {
                File outDir = new File(getFilesDir(), "recordings/rhythm_slices");
                String prefix = selectedFile.getName().replace(".wav", "");
                List<File> exported = engine.exportAllSlices(result, outDir, prefix);
                appendLog("✅ " + exported.size() + " slices → recordings/rhythm_slices/");
                for (File f : exported) appendLog("  " + f.getName());
                handler.post(() -> tvStatus.setText("✅ " + exported.size() + " slices exported"));
            } catch (Exception e) {
                handler.post(() -> tvStatus.setText("❌ Export: " + e.getMessage()));
            }
        }).start();
    }

    // ── Build Octapad kit ─────────────────────────────────────────────────────
    private void buildKit() {
        if (result == null) return;
        tvStatus.setText("🥁 Building kit…");
        new Thread(() -> {
            try {
                File kitDir = new File(getFilesDir(), "recordings/octapad_kit_slicer");
                List<File> kit = engine.buildOctapadKit(result, kitDir);
                appendLog("🥁 Kit: " + kit.size() + " pads → recordings/octapad_kit_slicer/");
                for (File f : kit) appendLog("  " + f.getName());
                handler.post(() -> tvStatus.setText("✅ Octapad kit: " + kit.size() + " pads"));
            } catch (Exception e) {
                handler.post(() -> tvStatus.setText("❌ Kit: " + e.getMessage()));
            }
        }).start();
    }

    // ── Share kit as ZIP ──────────────────────────────────────────────────────
    private void shareKit() {
        File kitDir = new File(getFilesDir(), "recordings/octapad_kit_slicer");
        File[] files = kitDir.exists() ? kitDir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        if (files == null || files.length == 0) { toast("Build Octapad kit first"); return; }
        String zipName = "octapad_kit_" + selectedFile.getName().replace(".wav", "") + ".zip";
        tvStatus.setText("Zipping…");
        List<File> kitList = Arrays.asList(files);
        new Thread(() -> UniversalExportHelper.shareFilesAsZip(this, kitList, zipName,
            new UniversalExportHelper.ZipReadyCallback() {
                @Override public void onReady(Intent i)    { handler.post(() -> { startActivity(i); tvStatus.setText("✅ Shared as ZIP"); }); }
                @Override public void onError(String msg)  { handler.post(() -> tvStatus.setText("❌ Zip: " + msg)); }
            })).start();
    }

    // ── Save to Music ─────────────────────────────────────────────────────────
    private void saveToDownloads() {
        File kitDir   = new File(getFilesDir(), "recordings/octapad_kit_slicer");
        File sliceDir = new File(getFilesDir(), "recordings/rhythm_slices");
        File[] kitF   = kitDir.exists()   ? kitDir.listFiles(f -> f.getName().endsWith(".wav"))   : null;
        File[] slcF   = sliceDir.exists() ? sliceDir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        List<File> toSave = new ArrayList<>();
        if (kitF   != null) toSave.addAll(Arrays.asList(kitF));
        else if (slcF != null) toSave.addAll(Arrays.asList(slcF));
        if (toSave.isEmpty()) { toast("Export or build kit first"); return; }
        tvStatus.setText("Saving to Music/PramodSoundStudio…");
        List<File> fl = toSave;
        new Thread(() -> {
            int saved = UniversalExportHelper.saveBatchToDownloads(this, fl, "PramodSoundStudio");
            handler.post(() -> tvStatus.setText("✅ " + saved + "/" + fl.size() + " saved to Music/PramodSoundStudio"));
        }).start();
    }

    // ── Batch prepare ─────────────────────────────────────────────────────────
    private void batchPrepare() {
        if (result == null || result.slices.isEmpty()) { toast("Detect slices first"); return; }
        tvStatus.setText("⚙️ Batch preparing…"); btnBatchPrep.setEnabled(false); clearLog();
        new Thread(() -> {
            try {
                File rawDir  = new File(getFilesDir(), "recordings/rhythm_slices_raw");
                File prepDir = new File(getFilesDir(), "recordings/rhythm_slices_prep");
                String prefix = selectedFile.getName().replace(".wav", "");
                List<File> raw = engine.exportAllSlices(result, rawDir, prefix);
                List<File> prepared = engine.batchPrepare(raw, prepDir, new RhythmSlicerEngine.ProgressListener() {
                    @Override public void onProgress(int d, int t, String f) {
                        handler.post(() -> tvStatus.setText("Preparing " + d + "/" + t + ": " + f));
                    }
                    @Override public void onError(String f, String e) { appendLog("❌ " + f + ": " + e); }
                });
                appendLog("✅ " + prepared.size() + " samples prepared (44100Hz/Mono/16-bit)");
                for (File f : prepared) appendLog("  " + f.getName());
                handler.post(() -> { tvStatus.setText("✅ " + prepared.size() + " samples ready"); btnBatchPrep.setEnabled(true); });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("❌ Batch: " + e.getMessage()); btnBatchPrep.setEnabled(true); });
            }
        }).start();
    }

    // ── Preview slice ─────────────────────────────────────────────────────────
    private void previewSlice(RhythmSlicerEngine.SlicePoint sp) {
        try {
            File tmpDir = new File(getCacheDir(), "preview");
            File tmp    = engine.exportSlice(result, sp, tmpDir, "preview_" + sp.type.name());
            Intent i    = new Intent(this, EditorActivity.class);
            i.putExtra(EditorActivity.EXTRA_FILE_PATH, tmp.getAbsolutePath());
            startActivity(i);
        } catch (Exception e) { toast("Preview error: " + e.getMessage()); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void setExportEnabled(boolean e) {
        btnExportAll.setEnabled(e); btnBuildKit.setEnabled(e);
        btnShareKit.setEnabled(e); btnSaveDownloads.setEnabled(e); btnBatchPrep.setEnabled(e);
    }
    private void toast(String m)     { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void clearLog()          { log.setLength(0); tvLog.setText(""); }
    private void appendLog(String s) {
        log.append(s).append("\n");
        handler.post(() -> { tvLog.setText(log); scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN)); });
    }

    // Minimal SeekBar listener
    private abstract static class SL implements SeekBar.OnSeekBarChangeListener {
        @Override public abstract void onProgressChanged(SeekBar sb, int p, boolean u);
        @Override public void onStartTrackingTouch(SeekBar sb) {}
        @Override public void onStopTrackingTouch(SeekBar sb) {}
    }
}
