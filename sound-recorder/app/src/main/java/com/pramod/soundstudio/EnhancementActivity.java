package com.pramod.soundstudio;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import java.io.File;
import java.util.*;

/**
 * Audio Enhancement Activity
 * EQ, Compressor, Limiter, Normalize, De-Noise, De-Esser, DC Offset Removal.
 */
public class EnhancementActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_FILE = 9001;

    private AudioEnhancer enhancer = new AudioEnhancer();
    private File selectedFile;

    private TextView tvStatus, tvResult, tvFileName;
    private SeekBar  sbLow, sbMid, sbHigh, sbBass, sbTreble;
    private TextView tvLow, tvMid, tvHigh, tvBass, tvTreble;
    private SeekBar  sbThreshold, sbRatio, sbAttack, sbRelease, sbMakeup;
    private TextView tvThreshold, tvRatio, tvAttack, tvRelease, tvMakeup;
    private SeekBar  sbNoise;
    private TextView tvNoise;
    private CheckBox cbDcRemove, cbDeEsser, cbLimiter, cbNormalize;
    private Button   btnPickFile, btnApply, btnPreview;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enhancement);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvStatus   = findViewById(R.id.tvStatus);
        tvResult   = findViewById(R.id.tvResult);
        tvFileName = findViewById(R.id.tvFileName);

        sbLow    = findViewById(R.id.sbLow);    tvLow    = findViewById(R.id.tvLow);
        sbMid    = findViewById(R.id.sbMid);    tvMid    = findViewById(R.id.tvMid);
        sbHigh   = findViewById(R.id.sbHigh);   tvHigh   = findViewById(R.id.tvHigh);
        sbBass   = findViewById(R.id.sbBass);   tvBass   = findViewById(R.id.tvBass);
        sbTreble = findViewById(R.id.sbTreble); tvTreble = findViewById(R.id.tvTreble);

        sbThreshold = findViewById(R.id.sbThreshold); tvThreshold = findViewById(R.id.tvThreshold);
        sbRatio     = findViewById(R.id.sbRatio);     tvRatio     = findViewById(R.id.tvRatio);
        sbAttack    = findViewById(R.id.sbAttack);    tvAttack    = findViewById(R.id.tvAttack);
        sbRelease   = findViewById(R.id.sbRelease);   tvRelease   = findViewById(R.id.tvRelease);
        sbMakeup    = findViewById(R.id.sbMakeup);    tvMakeup    = findViewById(R.id.tvMakeup);

        sbNoise  = findViewById(R.id.sbNoise);   tvNoise  = findViewById(R.id.tvNoise);
        cbDcRemove  = findViewById(R.id.cbDcRemove);
        cbDeEsser   = findViewById(R.id.cbDeEsser);
        cbLimiter   = findViewById(R.id.cbLimiter);
        cbNormalize = findViewById(R.id.cbNormalize);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnApply    = findViewById(R.id.btnApply);

        setupEqSliders();
        setupCompressorSliders();
        setupNoiseSlider();

        btnPickFile.setOnClickListener(v -> pickFile());
        btnApply.setOnClickListener(v -> applyEnhancement());

        loadFirstFile();
    }

    private void setupEqSliders() {
        // EQ sliders: -12 to +12 dB, centre=12 → 0 dB
        for (Object[] s : new Object[][]{ {sbLow, tvLow, "Low"}, {sbMid, tvMid, "Mid"}, {sbHigh, tvHigh, "High"}, {sbBass, tvBass, "Bass"}, {sbTreble, tvTreble, "Treble"} }) {
            SeekBar sb = (SeekBar)s[0]; TextView tv = (TextView)s[1]; String name = (String)s[2];
            sb.setMax(24); sb.setProgress(12);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar se, int p, boolean u) {
                    tv.setText(name + ": " + (p - 12) + " dB");
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            tv.setText(name + ": 0 dB");
        }
    }

    private void setupCompressorSliders() {
        sbThreshold.setMax(60); sbThreshold.setProgress(42); // -18 dB default
        sbThreshold.setOnSeekBarChangeListener(simple(tvThreshold, p -> "Threshold: -" + p + " dB"));

        sbRatio.setMax(200); sbRatio.setProgress(40); // 4.0 default (40/10)
        sbRatio.setOnSeekBarChangeListener(simple(tvRatio, p -> "Ratio: " + (p/10.0) + ":1"));

        sbAttack.setMax(100); sbAttack.setProgress(5);
        sbAttack.setOnSeekBarChangeListener(simple(tvAttack, p -> "Attack: " + p + "ms"));

        sbRelease.setMax(500); sbRelease.setProgress(100);
        sbRelease.setOnSeekBarChangeListener(simple(tvRelease, p -> "Release: " + p + "ms"));

        sbMakeup.setMax(24); sbMakeup.setProgress(0);
        sbMakeup.setOnSeekBarChangeListener(simple(tvMakeup, p -> "Makeup: +" + p + " dB"));

        tvThreshold.setText("Threshold: -18 dB");
        tvRatio.setText("Ratio: 4.0:1");
        tvAttack.setText("Attack: 5ms");
        tvRelease.setText("Release: 100ms");
        tvMakeup.setText("Makeup: +0 dB");
    }

    private void setupNoiseSlider() {
        sbNoise.setMax(10); sbNoise.setProgress(0);
        sbNoise.setOnSeekBarChangeListener(simple(tvNoise, p -> "Noise Reduction: " + (p * 10) + "%"));
        tvNoise.setText("Noise Reduction: 0%");
    }

    private void loadFirstFile() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) return;
        File[] wavs = dir.listFiles(f -> f.getName().endsWith(".wav"));
        if (wavs != null && wavs.length > 0) {
            Arrays.sort(wavs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            selectedFile = wavs[0];
            tvFileName.setText(selectedFile.getName());
        }
    }

    private void pickFile() {
        File dir = new File(getFilesDir(), "recordings");
        File[] all = dir.exists() ? dir.listFiles(f -> f.getName().endsWith(".wav")) : null;
        List<File> list = new ArrayList<>();
        if (all != null) list.addAll(Arrays.asList(all));

        List<String> names = new ArrayList<>();
        for (File f : list) names.add(f.getName());
        names.add("📱 Browse Device Storage…");
        final int browseIndex = names.size() - 1;
        final File[] files = list.toArray(new File[0]);

        new AlertDialog.Builder(this).setTitle("Select file")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                if (which == browseIndex) {
                    DeviceFileImporter.launchPicker(this, REQUEST_DEVICE_FILE, false);
                } else {
                    selectedFile = files[which];
                    tvFileName.setText(files[which].getName());
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

    private void applyEnhancement() {
        if (selectedFile == null) { toast("Select a file first"); return; }
        AudioEnhancer.EnhancementConfig cfg = buildConfig();
        tvStatus.setText("Applying enhancement…");
        btnApply.setEnabled(false);
        new Thread(() -> {
            try {
                File out = enhancer.enhance(selectedFile, new File(getFilesDir(), "recordings"), cfg);
                handler.post(() -> {
                    tvStatus.setText("Done");
                    tvResult.setText("✅ Saved: " + out.getName());
                    btnApply.setEnabled(true);
                });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("Error"); tvResult.setText("❌ " + e.getMessage()); btnApply.setEnabled(true); });
            }
        }).start();
    }

    private AudioEnhancer.EnhancementConfig buildConfig() {
        AudioEnhancer.EnhancementConfig c = new AudioEnhancer.EnhancementConfig();
        c.eqLowGainDB    = sbLow.getProgress()    - 12;
        c.eqMidGainDB    = sbMid.getProgress()    - 12;
        c.eqHighGainDB   = sbHigh.getProgress()   - 12;
        c.bassBoostDB    = sbBass.getProgress()   - 12;
        c.trebleBoostDB  = sbTreble.getProgress() - 12;
        c.compressionThresholdDB = -(sbThreshold.getProgress());
        c.compressionRatio       = sbRatio.getProgress() / 10.0;
        c.attackMs               = sbAttack.getProgress();
        c.releaseMs              = sbRelease.getProgress();
        c.makeupGainDB           = sbMakeup.getProgress();
        c.noiseReductionStrength = sbNoise.getProgress() / 10.0f;
        c.removeDcOffset = cbDcRemove.isChecked();
        c.deEsser        = cbDeEsser.isChecked();
        c.limiter        = cbLimiter.isChecked();
        c.normalize      = cbNormalize.isChecked();
        return c;
    }

    private SeekBar.OnSeekBarChangeListener simple(TextView tv, LabelFn fn) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { tv.setText(fn.label(p)); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }
    interface LabelFn { String label(int p); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
