package com.pramod.soundstudio;

import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.recyclerview.widget.*;
import java.io.File;
import java.util.*;

/** Batch Processing Activity — auto-trim, normalize, convert, rename, apply effects to all files. */
public class BatchActivity extends AppCompatActivity {

    private BatchProcessor processor = new BatchProcessor();
    private List<File>     files     = new ArrayList<>();
    private TextView       tvStatus, tvLog, tvFileCount;
    private Spinner        spOperation;
    private Button         btnSelectAll, btnRun;
    private RecyclerView   rvFiles;
    private FilesCheckAdapter adapter;
    private final Handler  handler = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvStatus    = findViewById(R.id.tvStatus);
        tvLog       = findViewById(R.id.tvLog);
        tvFileCount = findViewById(R.id.tvFileCount);
        spOperation = findViewById(R.id.spOperation);
        btnSelectAll= findViewById(R.id.btnSelectAll);
        btnRun      = findViewById(R.id.btnRun);
        rvFiles     = findViewById(R.id.rvFiles);

        String[] ops = {"Auto-Trim All", "Normalize All (–1 dBFS)", "Convert to WAV", "Convert to AAC", "Rename (sequential)", "Add Fade In/Out"};
        ArrayAdapter<String> opsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ops);
        opsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spOperation.setAdapter(opsAdapter);

        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FilesCheckAdapter();
        rvFiles.setAdapter(adapter);

        btnSelectAll.setOnClickListener(v -> { for (File f : files) adapter.checked.add(f); adapter.notifyDataSetChanged(); });
        btnRun.setOnClickListener(v -> runBatch());
        loadFiles();
    }

    private void loadFiles() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) { tvStatus.setText("No recordings found"); return; }
        File[] all = dir.listFiles(f -> !f.isDirectory() && (f.getName().endsWith(".wav") || f.getName().endsWith(".m4a") || f.getName().endsWith(".mp3")));
        if (all != null) {
            Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            files.addAll(Arrays.asList(all));
            adapter.notifyDataSetChanged();
            tvFileCount.setText(files.size() + " files available");
        }
    }

    private void runBatch() {
        List<File> selected = new ArrayList<>(adapter.checked);
        if (selected.isEmpty()) { toast("Select files first"); return; }

        int op = spOperation.getSelectedItemPosition();
        logBuilder.setLength(0);
        tvLog.setText("");
        btnRun.setEnabled(false);
        tvStatus.setText("Processing " + selected.size() + " files…");

        File outDir = new File(getFilesDir(), "recordings/batch_output");
        if (!outDir.exists()) outDir.mkdirs();

        BatchProcessor.BatchProgressListener listener = new BatchProcessor.BatchProgressListener() {
            public void onFileStart(int i, int tot, String fn) {
                handler.post(() -> tvStatus.setText("[" + (i+1) + "/" + tot + "] " + fn));
            }
            public void onFileComplete(int i, int tot, File out) {
                logBuilder.append("✅ ").append(out.getName()).append("\n");
                handler.post(() -> tvLog.setText(logBuilder));
            }
            public void onFileError(int i, int tot, String fn, String err) {
                logBuilder.append("❌ ").append(fn).append(": ").append(err).append("\n");
                handler.post(() -> tvLog.setText(logBuilder));
            }
            public void onBatchComplete(int s, int f, long ms) {
                handler.post(() -> {
                    tvStatus.setText("✅ Done: " + s + " ok, " + f + " errors  (" + ms + "ms)");
                    btnRun.setEnabled(true);
                });
            }
        };

        new Thread(() -> {
            switch (op) {
                case 0: processor.batchAutoTrim(selected, outDir, 0.6f, 5, 50, listener); break;
                case 1: processor.batchNormalize(selected, outDir, -1.0, listener); break;
                case 2: processor.batchConvert(selected, outDir, AudioConverter.OutputFormat.WAV, new AudioConverter.ConvertConfig(), listener); break;
                case 3: processor.batchConvert(selected, outDir, AudioConverter.OutputFormat.AAC_M4A, new AudioConverter.ConvertConfig(), listener); break;
                case 4: processor.batchRename(selected, "sample", true, listener); break;
                case 5:
                    AudioEffectsEngine.EffectsConfig fx = new AudioEffectsEngine.EffectsConfig();
                    fx.fadeInMs = 10; fx.fadeOutMs = 100;
                    processor.batchApplyEffects(selected, outDir, fx, listener);
                    break;
            }
        }).start();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    class FilesCheckAdapter extends RecyclerView.Adapter<FilesCheckAdapter.VH> {
        Set<File> checked = new HashSet<>();
        public VH onCreateViewHolder(ViewGroup p, int t) {
            CheckBox cb = new CheckBox(BatchActivity.this);
            cb.setPadding(24, 12, 24, 12);
            cb.setTextColor(0xFFCCCCCC);
            return new VH(cb);
        }
        public void onBindViewHolder(VH h, int pos) {
            File f = files.get(pos);
            CheckBox cb = (CheckBox) h.itemView;
            cb.setText(f.getName() + "  (" + f.length()/1024 + " KB)");
            cb.setChecked(checked.contains(f));
            cb.setOnCheckedChangeListener((btn, c) -> { if (c) checked.add(f); else checked.remove(f); });
        }
        public int getItemCount() { return files.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
