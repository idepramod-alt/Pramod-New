package com.pramod.soundstudio;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import java.io.File;
import java.util.*;

/** Audio Effects Activity — Reverb, Delay, Chorus, Pitch Shift, Stereo Width, Fade. */
public class EffectsActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_FILE = 9001;

    private AudioEffectsEngine fxEngine = new AudioEffectsEngine();
    private File selectedFile;
    private TextView tvFileName, tvStatus, tvResult;
    private Button   btnPickFile, btnApply;

    // Reverb
    private SeekBar sbReverbRoom, sbReverbDecay, sbReverbMix;
    private TextView tvReverbRoom, tvReverbDecay, tvReverbMix;
    // Delay
    private SeekBar sbDelayMs, sbDelayFeedback, sbDelayMix;
    private TextView tvDelayMs, tvDelayFeedback, tvDelayMix;
    // Chorus
    private SeekBar sbChorusRate, sbChorusDepth, sbChorusMix;
    private TextView tvChorusRate, tvChorusDepth, tvChorusMix;
    // Pitch
    private SeekBar sbPitch;
    private TextView tvPitch;
    // Fades
    private SeekBar sbFadeIn, sbFadeOut;
    private TextView tvFadeIn, tvFadeOut;
    // Stereo
    private SeekBar sbStereoWidth;
    private TextView tvStereoWidth;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_effects);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvFileName  = findViewById(R.id.tvFileName);
        tvStatus    = findViewById(R.id.tvStatus);
        tvResult    = findViewById(R.id.tvResult);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnApply    = findViewById(R.id.btnApply);

        sbReverbRoom    = findViewById(R.id.sbReverbRoom);    tvReverbRoom    = findViewById(R.id.tvReverbRoom);
        sbReverbDecay   = findViewById(R.id.sbReverbDecay);   tvReverbDecay   = findViewById(R.id.tvReverbDecay);
        sbReverbMix     = findViewById(R.id.sbReverbMix);     tvReverbMix     = findViewById(R.id.tvReverbMix);
        sbDelayMs       = findViewById(R.id.sbDelayMs);       tvDelayMs       = findViewById(R.id.tvDelayMs);
        sbDelayFeedback = findViewById(R.id.sbDelayFeedback); tvDelayFeedback = findViewById(R.id.tvDelayFeedback);
        sbDelayMix      = findViewById(R.id.sbDelayMix);      tvDelayMix      = findViewById(R.id.tvDelayMix);
        sbChorusRate    = findViewById(R.id.sbChorusRate);    tvChorusRate    = findViewById(R.id.tvChorusRate);
        sbChorusDepth   = findViewById(R.id.sbChorusDepth);   tvChorusDepth   = findViewById(R.id.tvChorusDepth);
        sbChorusMix     = findViewById(R.id.sbChorusMix);     tvChorusMix     = findViewById(R.id.tvChorusMix);
        sbPitch         = findViewById(R.id.sbPitch);         tvPitch         = findViewById(R.id.tvPitch);
        sbFadeIn        = findViewById(R.id.sbFadeIn);        tvFadeIn        = findViewById(R.id.tvFadeIn);
        sbFadeOut       = findViewById(R.id.sbFadeOut);       tvFadeOut       = findViewById(R.id.tvFadeOut);
        sbStereoWidth   = findViewById(R.id.sbStereoWidth);   tvStereoWidth   = findViewById(R.id.tvStereoWidth);

        initSliders();
        btnPickFile.setOnClickListener(v -> pickFile());
        btnApply.setOnClickListener(v -> applyFx());
        loadFirstFile();
    }

    private void initSliders() {
        initSlider(sbReverbRoom,    tvReverbRoom,    "Room Size",  100, 50,  p -> p + "%");
        initSlider(sbReverbDecay,   tvReverbDecay,   "Decay",      100, 50,  p -> p + "%");
        initSlider(sbReverbMix,     tvReverbMix,     "Reverb Mix", 100, 0,   p -> p + "%");
        initSlider(sbDelayMs,       tvDelayMs,       "Delay",      1000, 300, p -> p + "ms");
        initSlider(sbDelayFeedback, tvDelayFeedback, "Feedback",   95,  40,  p -> p + "%");
        initSlider(sbDelayMix,      tvDelayMix,      "Delay Mix",  100, 0,   p -> p + "%");
        initSlider(sbChorusRate,    tvChorusRate,    "Chorus Rate",100, 30,  p -> String.format("%.1f Hz", p/20.0));
        initSlider(sbChorusDepth,   tvChorusDepth,   "Depth",      30,  5,   p -> p + "ms");
        initSlider(sbChorusMix,     tvChorusMix,     "Chorus Mix", 100, 0,   p -> p + "%");
        initSlider(sbPitch,         tvPitch,         "Pitch",      24,  12,  p -> (p-12) + " st");
        initSlider(sbFadeIn,        tvFadeIn,        "Fade In",    2000, 0,  p -> p + "ms");
        initSlider(sbFadeOut,       tvFadeOut,       "Fade Out",   2000, 0,  p -> p + "ms");
        initSlider(sbStereoWidth,   tvStereoWidth,   "Stereo Width",200, 100, p -> String.format("%.1f×", p/100.0));
    }

    private void initSlider(SeekBar sb, TextView tv, String name, int max, int def, LabelFn fn) {
        sb.setMax(max); sb.setProgress(def);
        tv.setText(name + ": " + fn.label(def));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { tv.setText(name + ": " + fn.label(p)); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }
    interface LabelFn { String label(int p); }

    private void loadFirstFile() {
        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) return;
        File[] all = dir.listFiles(f -> f.getName().endsWith(".wav"));
        if (all != null && all.length > 0) { Arrays.sort(all, (a,b)->Long.compare(b.lastModified(),a.lastModified())); selectedFile = all[0]; tvFileName.setText(all[0].getName()); }
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

        new AlertDialog.Builder(this).setTitle("Select WAV")
            .setItems(names.toArray(new String[0]), (d, w) -> {
                if (w == browseIndex) {
                    DeviceFileImporter.launchPicker(this, REQUEST_DEVICE_FILE, false);
                } else {
                    selectedFile = files[w]; tvFileName.setText(files[w].getName());
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

    private void applyFx() {
        if (selectedFile == null) { toast("Select a file first"); return; }
        AudioEffectsEngine.EffectsConfig cfg = new AudioEffectsEngine.EffectsConfig();
        cfg.reverbRoomSize = sbReverbRoom.getProgress() / 100.0f;
        cfg.reverbDecay    = sbReverbDecay.getProgress() / 100.0f;
        cfg.reverbWetMix   = sbReverbMix.getProgress() / 100.0f;
        cfg.delayMs        = sbDelayMs.getProgress();
        cfg.delayFeedback  = sbDelayFeedback.getProgress() / 100.0f;
        cfg.delayWetMix    = sbDelayMix.getProgress() / 100.0f;
        cfg.chorusRateHz   = sbChorusRate.getProgress() / 20.0f;
        cfg.chorusDepthMs  = sbChorusDepth.getProgress();
        cfg.chorusWetMix   = sbChorusMix.getProgress() / 100.0f;
        cfg.pitchSemitones = sbPitch.getProgress() - 12;
        cfg.fadeInMs       = sbFadeIn.getProgress();
        cfg.fadeOutMs      = sbFadeOut.getProgress();
        cfg.stereoWidth    = sbStereoWidth.getProgress() / 100.0f;

        tvStatus.setText("Applying effects…");
        btnApply.setEnabled(false);
        new Thread(() -> {
            try {
                File out = fxEngine.applyEffects(selectedFile, new File(getFilesDir(), "recordings"), cfg);
                handler.post(() -> { tvStatus.setText("Done"); tvResult.setText("✅ Saved: " + out.getName()); btnApply.setEnabled(true); });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("Error"); tvResult.setText("❌ " + e.getMessage()); btnApply.setEnabled(true); });
            }
        }).start();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
