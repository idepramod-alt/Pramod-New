package com.pramod.soundstudio;

import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;

import java.io.File;
import java.util.*;

/**
 * Ultra Precision Auto-Trim Activity
 *
 * - File picker (from recordings folder)
 * - Sensitivity, pre-roll, post-roll sliders
 * - Single auto-trim or batch auto-trim
 * - Result preview with before/after stats
 * - Undo/redo support
 */
public class AutoTrimActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_FILE = 9001;

    private AutoTrimEngine       engine       = new AutoTrimEngine();
    private List<File>           selectedFiles = new ArrayList<>();
    private TextView             tvStatus, tvResult;
    private SeekBar              sbSensitivity, sbPreRoll, sbPostRoll;
    private TextView             tvSensLabel, tvPreRollLabel, tvPostRollLabel;
    private Button               btnPickFiles, btnTrim, btnBatchTrim, btnUndo;
    private RecyclerView         rvFiles;
    private SelectedFilesAdapter adapter;
    private final Handler        uiHandler    = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_trim);

        tvStatus        = findViewById(R.id.tvStatus);
        tvResult        = findViewById(R.id.tvResult);
        sbSensitivity   = findViewById(R.id.sbSensitivity);
        sbPreRoll       = findViewById(R.id.sbPreRoll);
        sbPostRoll      = findViewById(R.id.sbPostRoll);
        tvSensLabel     = findViewById(R.id.tvSensLabel);
        tvPreRollLabel  = findViewById(R.id.tvPreRollLabel);
        tvPostRollLabel = findViewById(R.id.tvPostRollLabel);
        btnPickFiles    = findViewById(R.id.btnPickFiles);
        btnTrim         = findViewById(R.id.btnTrim);
        btnBatchTrim    = findViewById(R.id.btnBatchTrim);
        btnUndo         = findViewById(R.id.btnUndo);
        rvFiles         = findViewById(R.id.rvFiles);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SelectedFilesAdapter();
        rvFiles.setAdapter(adapter);

        setupSliders();
        setupButtons();
        autoLoadRecordings();
    }

    private void setupSliders() {
        // Sensitivity: 0–100 → 0.0–1.0
        sbSensitivity.setMax(100);
        sbSensitivity.setProgress(60); // default 0.6
        sbSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                tvSensLabel.setText("Sensitivity: " + p + "%");
                engine.setSensitivity(p / 100.0f);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        tvSensLabel.setText("Sensitivity: 60%");

        // Pre-roll: 0–50ms
        sbPreRoll.setMax(50);
        sbPreRoll.setProgress(5);
        sbPreRoll.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                tvPreRollLabel.setText("Pre-roll: " + p + "ms");
                engine.setPreRollMs(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        tvPreRollLabel.setText("Pre-roll: 5ms");

        // Post-roll: 0–300ms
        sbPostRoll.setMax(300);
        sbPostRoll.setProgress(50);
        sbPostRoll.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                tvPostRollLabel.setText("Post-roll: " + p + "ms");
                engine.setPostRollMs(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        tvPostRollLabel.setText("Post-roll: 50ms");
    }

    private void setupButtons() {
        btnPickFiles.setOnClickListener(v -> showFilePicker());

        btnTrim.setOnClickListener(v -> {
            if (selectedFiles.isEmpty()) {
                showMsg("No files selected"); return;
            }
            trimSingle(selectedFiles.get(0));
        });

        btnBatchTrim.setOnClickListener(v -> {
            if (selectedFiles.isEmpty()) {
                showMsg("No files selected"); return;
            }
            batchTrim();
        });

        btnUndo.setOnClickListener(v -> {
            engine.undo();
            tvResult.setText("↩️ Undo applied");
        });
    }

    private void autoLoadRecordings() {
        File dir = new File(getFilesDir(), "recordings");
        if (dir.exists()) {
            File[] wavs = dir.listFiles(f -> f.getName().endsWith(".wav"));
            if (wavs != null) {
                Arrays.sort(wavs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                selectedFiles.addAll(Arrays.asList(wavs));
                adapter.notifyDataSetChanged();
                tvStatus.setText(selectedFiles.size() + " WAV file(s) loaded");
            }
        }
    }

    private void showFilePicker() {
        // Show list of WAV files to select (internal recordings + option to browse the device)
        File dir   = new File(getFilesDir(), "recordings");
        File[] internal = dir.exists() ? dir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        List<File> all = new ArrayList<>();
        if (internal != null) all.addAll(Arrays.asList(internal));

        List<String> names = new ArrayList<>();
        for (File f : all) names.add(f.getName());
        names.add("📱 Browse Device Storage…");
        final int browseIndex = names.size() - 1;
        final File[] files = all.toArray(new File[0]);

        new AlertDialog.Builder(this)
            .setTitle("Select files")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                if (which == browseIndex) {
                    DeviceFileImporter.launchPicker(this, REQUEST_DEVICE_FILE, true);
                } else if (!selectedFiles.contains(files[which])) {
                    selectedFiles.add(files[which]);
                    adapter.notifyDataSetChanged();
                }
            }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_FILE && resultCode == RESULT_OK && data != null) {
            File dir = new File(getFilesDir(), "recordings");
            List<File> imported = DeviceFileImporter.handleResult(this, data, dir);
            for (File f : imported) if (!selectedFiles.contains(f)) selectedFiles.add(f);
            if (!imported.isEmpty()) {
                adapter.notifyDataSetChanged();
                showMsg("Imported " + imported.size() + " file(s)");
            }
        }
    }

    private void trimSingle(File f) {
        setUiBusy(true, "Trimming " + f.getName() + "…");
        new Thread(() -> {
            try {
                File outDir = new File(getFilesDir(), "recordings");
                AutoTrimEngine.TrimResult r = engine.autoTrim(f, outDir);
                uiHandler.post(() -> {
                    setUiBusy(false, "Done");
                    tvResult.setText(String.format(
                        "✅ %s\n" +
                        "Removed start: %.3fs  |  end: %.3fs\n" +
                        "New duration: %.3fs",
                        r.outputFile.getName(),
                        r.removedStartSec, r.removedEndSec, r.newDurationSec));
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                uiHandler.post(() -> { setUiBusy(false, "Error"); showMsg("Error: " + e.getMessage()); });
            }
        }).start();
    }

    private void batchTrim() {
        setUiBusy(true, "Batch trimming " + selectedFiles.size() + " files…");
        new Thread(() -> {
            File outDir = new File(getFilesDir(), "recordings");
            int[] counts = {0, 0};
            for (File f : selectedFiles) {
                try {
                    engine.autoTrim(f, outDir);
                    counts[0]++;
                } catch (Exception e) { counts[1]++; }
            }
            uiHandler.post(() -> {
                setUiBusy(false, "Batch done");
                tvResult.setText("✅ Batch complete: " + counts[0] + " trimmed, " + counts[1] + " errors");
            });
        }).start();
    }

    private void setUiBusy(boolean busy, String status) {
        tvStatus.setText(status);
        btnTrim.setEnabled(!busy);
        btnBatchTrim.setEnabled(!busy);
    }

    private void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class SelectedFilesAdapter extends RecyclerView.Adapter<SelectedFilesAdapter.VH> {
        public VH onCreateViewHolder(ViewGroup p, int t) {
            TextView tv = new TextView(AutoTrimActivity.this);
            tv.setPadding(24, 12, 24, 12);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(13);
            return new VH(tv);
        }
        public void onBindViewHolder(VH h, int pos) {
            File f = selectedFiles.get(pos);
            ((TextView)h.itemView).setText(f.getName() + "  (" + f.length()/1024 + " KB)");
            h.itemView.setOnLongClickListener(v -> {
                selectedFiles.remove(pos); notifyDataSetChanged(); return true;
            });
        }
        public int getItemCount() { return selectedFiles.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
