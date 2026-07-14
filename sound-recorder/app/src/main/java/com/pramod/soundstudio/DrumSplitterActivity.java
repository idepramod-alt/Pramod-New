package com.pramod.soundstudio;

import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.recyclerview.widget.*;
import java.io.File;
import java.util.*;

public class DrumSplitterActivity extends AppCompatActivity {

    private DrumSplitter splitter = new DrumSplitter();
    private List<File>   files    = new ArrayList<>();
    private List<DrumSplitter.SplitResult> results = new ArrayList<>();
    private TextView     tvStatus, tvLog;
    private SeekBar      sbSensitivity, sbMinGap, sbPostRoll;
    private TextView     tvSensLabel, tvMinGapLabel, tvPostRollLabel;
    private EditText     etPrefix;
    private CheckBox     cbAutoTrim;
    private Button       btnPickFile, btnSplit, btnExportAll;
    private RecyclerView rvResults;
    private ResultsAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drum_splitter);

        tvStatus       = findViewById(R.id.tvStatus);
        tvLog          = findViewById(R.id.tvLog);
        sbSensitivity  = findViewById(R.id.sbSensitivity);
        sbMinGap       = findViewById(R.id.sbMinGap);
        sbPostRoll     = findViewById(R.id.sbPostRoll);
        tvSensLabel    = findViewById(R.id.tvSensLabel);
        tvMinGapLabel  = findViewById(R.id.tvMinGapLabel);
        tvPostRollLabel= findViewById(R.id.tvPostRollLabel);
        etPrefix       = findViewById(R.id.etPrefix);
        cbAutoTrim     = findViewById(R.id.cbAutoTrim);
        btnPickFile    = findViewById(R.id.btnPickFile);
        btnSplit       = findViewById(R.id.btnSplit);
        btnExportAll   = findViewById(R.id.btnExportAll);
        rvResults      = findViewById(R.id.rvResults);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter();
        rvResults.setAdapter(adapter);

        setupSliders();

        btnPickFile.setOnClickListener(v -> pickFile());
        btnSplit.setOnClickListener(v -> splitSelected());
        btnExportAll.setOnClickListener(v -> showExportDone());

        autoLoadFirst();
    }

    private void setupSliders() {
        sbSensitivity.setMax(100); sbSensitivity.setProgress(60);
        sbSensitivity.setOnSeekBarChangeListener(slider(tvSensLabel, "Sensitivity", p -> splitter.setSensitivity(p / 100.0f)));

        sbMinGap.setMax(200); sbMinGap.setProgress(40);
        sbMinGap.setOnSeekBarChangeListener(slider(tvMinGapLabel, "Min gap", p -> splitter.setMinHitGapMs(p)));

        sbPostRoll.setMax(300); sbPostRoll.setProgress(80);
        sbPostRoll.setOnSeekBarChangeListener(slider(tvPostRollLabel, "Post-roll", p -> splitter.setPostRollMs(p)));

        tvSensLabel.setText("Sensitivity: 60%");
        tvMinGapLabel.setText("Min gap: 40ms");
        tvPostRollLabel.setText("Post-roll: 80ms");
    }

    private void autoLoadFirst() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) return;
        File[] wavs = dir.listFiles(f -> f.getName().endsWith(".wav"));
        if (wavs != null && wavs.length > 0) {
            Arrays.sort(wavs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            files.clear(); files.add(wavs[0]);
            tvStatus.setText("Loaded: " + wavs[0].getName());
        }
    }

    private void pickFile() {
        File dir = new File(getFilesDir(), "recordings");
        File[] wavs = dir.exists() ? dir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        if (wavs == null || wavs.length == 0) { toast("No WAV files found"); return; }
        String[] names = new String[wavs.length];
        for (int i = 0; i < wavs.length; i++) names[i] = wavs[i].getName();
        final File[] copy = wavs;
        new AlertDialog.Builder(this).setTitle("Select WAV")
            .setItems(names, (d, which) -> {
                files.clear(); files.add(copy[which]);
                tvStatus.setText("Loaded: " + copy[which].getName());
            }).show();
    }

    private void splitSelected() {
        if (files.isEmpty()) { toast("Select a file first"); return; }
        String prefix = etPrefix.getText().toString().trim();
        if (prefix.isEmpty()) prefix = "hit";
        splitter.setOutputPrefix(prefix);
        splitter.setAutoTrimEach(cbAutoTrim.isChecked());

        final File src = files.get(0);
        final File outDir = new File(getFilesDir(), "recordings/splits");
        if (!outDir.exists()) outDir.mkdirs();

        tvStatus.setText("Splitting…");
        btnSplit.setEnabled(false);
        final StringBuilder log = new StringBuilder();

        new Thread(() -> {
            try {
                List<DrumSplitter.SplitResult> res = splitter.splitFile(src, outDir, new DrumSplitter.SplitListener() {
                    public void onHitExported(int num, int total, String fn, double dur) {
                        log.append(String.format("Hit %d/%d: %s (%.3fs)\n", num, total, fn, dur));
                        handler.post(() -> tvLog.setText(log));
                    }
                    public void onComplete(int total) {}
                });
                handler.post(() -> {
                    results.clear(); results.addAll(res);
                    adapter.notifyDataSetChanged();
                    tvStatus.setText("✅ " + res.size() + " hits exported to /splits/");
                    btnSplit.setEnabled(true);
                });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("Error: " + e.getMessage()); btnSplit.setEnabled(true); });
            }
        }).start();
    }

    private void showExportDone() {
        if (results.isEmpty()) { toast("Split first"); return; }
        toast("✅ " + results.size() + " files ready in recordings/splits/");
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private SeekBar.OnSeekBarChangeListener slider(TextView label, String name, Callback<Integer> cb) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                String unit = name.contains("gap") || name.contains("roll") ? "ms" : "%";
                label.setText(name + ": " + p + unit);
                cb.call(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    interface Callback<T> { void call(T value); }

    class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.VH> {
        public VH onCreateViewHolder(ViewGroup p, int t) {
            TextView tv = new TextView(DrumSplitterActivity.this);
            tv.setPadding(24, 12, 24, 12);
            tv.setTextColor(0xFFCCCCCC);
            tv.setTextSize(12);
            return new VH(tv);
        }
        public void onBindViewHolder(VH h, int pos) {
            DrumSplitter.SplitResult r = results.get(pos);
            ((TextView)h.itemView).setText(String.format("  %s  |  %.3fs", r.outputFile.getName(), r.durationSec));
        }
        public int getItemCount() { return results.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
