package com.pramod.soundstudio;

import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import java.io.File;
import java.util.*;

/** Format Converter Activity — WAV ↔ AAC/M4A, resample, mono/stereo. */
public class ConverterActivity extends AppCompatActivity {

    private AudioConverter converter = new AudioConverter();
    private List<File>     selectedFiles = new ArrayList<>();
    private TextView       tvStatus, tvResult, tvFileCount;
    private Spinner        spFormat, spSampleRate;
    private CheckBox       cbMono;
    private SeekBar        sbBitrate;
    private TextView       tvBitrate;
    private Button         btnPickFiles, btnConvert;
    private final Handler  handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_converter);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvStatus    = findViewById(R.id.tvStatus);
        tvResult    = findViewById(R.id.tvResult);
        tvFileCount = findViewById(R.id.tvFileCount);
        spFormat    = findViewById(R.id.spFormat);
        spSampleRate= findViewById(R.id.spSampleRate);
        cbMono      = findViewById(R.id.cbMono);
        sbBitrate   = findViewById(R.id.sbBitrate);
        tvBitrate   = findViewById(R.id.tvBitrate);
        btnPickFiles= findViewById(R.id.btnPickFiles);
        btnConvert  = findViewById(R.id.btnConvert);

        // Format spinner
        ArrayAdapter<String> fmtAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"WAV", "AAC / M4A"});
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFormat.setAdapter(fmtAdapter);

        // Sample rate spinner
        ArrayAdapter<String> srAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"44100 Hz", "48000 Hz", "22050 Hz", "16000 Hz", "8000 Hz"});
        srAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSampleRate.setAdapter(srAdapter);

        // Bitrate
        sbBitrate.setMax(320);
        sbBitrate.setProgress(192);
        sbBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvBitrate.setText("Bitrate: " + Math.max(32, p) + " kbps");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        tvBitrate.setText("Bitrate: 192 kbps");

        btnPickFiles.setOnClickListener(v -> pickFiles());
        btnConvert.setOnClickListener(v -> convert());
        loadAllFiles();
    }

    private void loadAllFiles() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) return;
        File[] all = dir.listFiles(f ->
            f.getName().endsWith(".wav") || f.getName().endsWith(".m4a") ||
            f.getName().endsWith(".mp3") || f.getName().endsWith(".aac"));
        if (all != null) {
            Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            selectedFiles.addAll(Arrays.asList(all));
            tvFileCount.setText(selectedFiles.size() + " file(s) selected");
        }
    }

    private void pickFiles() {
        File dir = new File(getFilesDir(), "recordings");
        File[] all = dir.exists() ? dir.listFiles() : null;
        if (all == null || all.length == 0) { toast("No files found"); return; }
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].getName();
        boolean[] checked = new boolean[all.length];
        final File[] files = all;
        new AlertDialog.Builder(this).setTitle("Select files")
            .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("OK", (d, w) -> {
                selectedFiles.clear();
                for (int i = 0; i < files.length; i++) if (checked[i]) selectedFiles.add(files[i]);
                tvFileCount.setText(selectedFiles.size() + " file(s) selected");
            }).show();
    }

    private void convert() {
        if (selectedFiles.isEmpty()) { toast("Select files first"); return; }

        AudioConverter.ConvertConfig cfg = new AudioConverter.ConvertConfig();
        int[] sampleRates = {44100, 48000, 22050, 16000, 8000};
        cfg.outputSampleRate = sampleRates[spSampleRate.getSelectedItemPosition()];
        cfg.mono    = cbMono.isChecked();
        cfg.bitrate = Math.max(32, sbBitrate.getProgress()) * 1000;

        AudioConverter.OutputFormat fmt = spFormat.getSelectedItemPosition() == 1
                ? AudioConverter.OutputFormat.AAC_M4A
                : AudioConverter.OutputFormat.WAV;

        File outDir = new File(getFilesDir(), "recordings/converted");
        if (!outDir.exists()) outDir.mkdirs();

        tvStatus.setText("Converting " + selectedFiles.size() + " file(s)…");
        btnConvert.setEnabled(false);
        final StringBuilder log = new StringBuilder();

        new Thread(() -> {
            int success = 0, failed = 0;
            for (File f : selectedFiles) {
                try {
                    File out = converter.convert(f, outDir, fmt, cfg, null);
                    log.append("✅ ").append(out.getName()).append("\n");
                    success++;
                } catch (Exception e) {
                    log.append("❌ ").append(f.getName()).append(": ").append(e.getMessage()).append("\n");
                    failed++;
                }
            }
            final int s = success, fl = failed;
            handler.post(() -> {
                tvStatus.setText("Done: " + s + " converted, " + fl + " errors");
                tvResult.setText(log.toString());
                btnConvert.setEnabled(true);
            });
        }).start();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
