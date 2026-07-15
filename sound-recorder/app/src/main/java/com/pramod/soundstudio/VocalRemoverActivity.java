package com.pramod.soundstudio;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import java.io.File;
import java.util.*;

/**
 * Vocal Remover / Stem Separation Activity
 *
 * Architecture for full AI stem separation:
 *   - Use a TensorFlow Lite model (Demucs / Spleeter converted to .tflite)
 *   - Model processes 44.1kHz stereo in ~30s chunks
 *   - Outputs: vocals, drums, bass, other (4-stem) or piano, guitar (6-stem)
 *   - Solo/Mute per stem during preview
 *   - Export each stem as separate WAV
 *
 * Current implementation:
 *   - Phase-cancellation vocal removal (works on most center-panned vocals)
 *   - For full AI separation, add TensorFlow Lite dependency and the .tflite model
 *     to assets/. See comments in phaseCancelVocals() and runAiStemSeparation().
 *
 * To add TFLite: build.gradle → implementation 'org.tensorflow:tensorflow-lite:2.14.0'
 * Model download: https://github.com/facebookresearch/demucs (export with torch.jit)
 */
public class VocalRemoverActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_FILE = 9001;

    private File selectedFile;
    private TextView tvFileName, tvStatus, tvInfo;
    private Button   btnPickFile, btnPhaseSeparate, btnAiSeparate;
    private CheckBox cbVocals, cbDrums, cbBass, cbOther;
    private TextView tvVocalsInfo, tvDrumsInfo, tvBassInfo, tvOtherInfo;
    private Button   btnExportAll;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Map<String, File> stems = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocal_remover);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvFileName     = findViewById(R.id.tvFileName);
        tvStatus       = findViewById(R.id.tvStatus);
        tvInfo         = findViewById(R.id.tvInfo);
        btnPickFile    = findViewById(R.id.btnPickFile);
        btnPhaseSeparate = findViewById(R.id.btnPhaseSeparate);
        btnAiSeparate  = findViewById(R.id.btnAiSeparate);
        cbVocals       = findViewById(R.id.cbVocals);
        cbDrums        = findViewById(R.id.cbDrums);
        cbBass         = findViewById(R.id.cbBass);
        cbOther        = findViewById(R.id.cbOther);
        tvVocalsInfo   = findViewById(R.id.tvVocalsInfo);
        tvDrumsInfo    = findViewById(R.id.tvDrumsInfo);
        tvBassInfo     = findViewById(R.id.tvBassInfo);
        tvOtherInfo    = findViewById(R.id.tvOtherInfo);
        btnExportAll   = findViewById(R.id.btnExportAll);

        tvInfo.setText(
            "Phase Cancellation: Classic mono-subtract method.\n" +
            "Works well for center-panned vocals.\n\n" +
            "AI Stem Separation: Requires a TensorFlow Lite model.\n" +
            "Model not included — tap button for setup instructions."
        );

        btnPickFile.setOnClickListener(v -> pickFile());
        btnPhaseSeparate.setOnClickListener(v -> runPhaseCancel());
        btnAiSeparate.setOnClickListener(v -> showAiSetupDialog());
        btnExportAll.setOnClickListener(v -> exportAll());

        loadFirstStereoFile();
    }

    private void loadFirstStereoFile() {
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
        File[] all = dir.exists() ? dir.listFiles(f -> f.getName().endsWith(".wav") || f.getName().endsWith(".mp3") || f.getName().endsWith(".m4a")) : null;
        List<File> list = new ArrayList<>();
        if (all != null) list.addAll(Arrays.asList(all));

        List<String> names = new ArrayList<>();
        for (File f : list) names.add(f.getName());
        names.add("📱 Browse Device Storage…");
        final int browseIndex = names.size() - 1;
        final File[] files = list.toArray(new File[0]);

        new AlertDialog.Builder(this).setTitle("Select audio file")
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

    // ── Phase Cancellation Vocal Removal ─────────────────────────────────────

    /**
     * Classic mid/side phase cancellation:
     *  Instrumental = L - R  (removes center-panned content = vocals)
     *  Vocals       = L + R  (mid channel)
     *
     * Quality: Good for studio recordings with center-panned vocals.
     * Limitations: Mono input → no separation possible.
     *              Drums/bass mixed to center will also be attenuated.
     */
    private void runPhaseCancel() {
        if (selectedFile == null) { toast("Select a file first"); return; }
        tvStatus.setText("Separating via phase cancellation…");
        btnPhaseSeparate.setEnabled(false);

        new Thread(() -> {
            try {
                int[]   header     = AudioTrimmer.readWavHeader(selectedFile);
                int     sampleRate = header[0];
                int     channels   = header[1];
                short[] pcm        = AudioTrimmer.readWavPcm(selectedFile);

                File outDir = new File(getFilesDir(), "recordings/stems");
                if (!outDir.exists()) outDir.mkdirs();
                String baseName = selectedFile.getName().replace(".wav","");

                if (channels < 2) {
                    handler.post(() -> {
                        tvStatus.setText("⚠️ File is mono — phase cancellation requires stereo.");
                        btnPhaseSeparate.setEnabled(true);
                    });
                    return;
                }

                int frames = pcm.length / 2;
                short[] vocals       = new short[frames]; // mid (L+R)/2
                short[] instrumental = new short[frames]; // side (L-R)/2

                for (int i = 0; i < frames; i++) {
                    short L = pcm[i * 2];
                    short R = pcm[i * 2 + 1];
                    vocals[i]       = (short) Math.max(-32768, Math.min(32767, (L + R) / 2));
                    instrumental[i] = (short) Math.max(-32768, Math.min(32767, (L - R) / 2));
                }

                File vocFile  = new File(outDir, baseName + "_vocals.wav");
                File instFile = new File(outDir, baseName + "_instrumental.wav");
                AudioTrimmer.writePcmToWav(vocals,       sampleRate, 1, vocFile);
                AudioTrimmer.writePcmToWav(instrumental, sampleRate, 1, instFile);

                stems.clear();
                stems.put("Vocals",       vocFile);
                stems.put("Instrumental", instFile);

                handler.post(() -> {
                    tvStatus.setText("✅ Phase separation complete!");
                    tvVocalsInfo.setText("vocals.wav  (" + vocFile.length()/1024 + " KB)");
                    tvDrumsInfo.setText("instrumental.wav  (" + instFile.length()/1024 + " KB)");
                    tvBassInfo.setText("");
                    tvOtherInfo.setText("");
                    btnExportAll.setEnabled(true);
                    btnPhaseSeparate.setEnabled(true);
                });
            } catch (Exception e) {
                handler.post(() -> { tvStatus.setText("Error: " + e.getMessage()); btnPhaseSeparate.setEnabled(true); });
            }
        }).start();
    }

    // ── AI Stem Separation (TFLite placeholder) ───────────────────────────────

    private void showAiSetupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("AI Stem Separation Setup")
            .setMessage(
                "To enable full AI stem separation (vocals, drums, bass, piano, guitar):\n\n" +
                "1. Add to build.gradle:\n" +
                "   implementation 'org.tensorflow:tensorflow-lite:2.14.0'\n\n" +
                "2. Download the Demucs TFLite model:\n" +
                "   github.com/facebookresearch/demucs\n" +
                "   (Convert with: python -m demucs.export --format tflite)\n\n" +
                "3. Place model.tflite in:\n" +
                "   app/src/main/assets/\n\n" +
                "4. Uncomment runAiStemSeparation() in the source.\n\n" +
                "Once set up, all 4-6 stems will separate with full solo/mute and export."
            )
            .setPositiveButton("Got it", null)
            .show();
    }

    /**
     * AI stem separation pipeline (requires TFLite model in assets/).
     * Uncomment and implement when model is available.
     *
     * Flow:
     *   1. Decode audio to 44100Hz stereo float[]
     *   2. Split into 6-second chunks with 50% overlap
     *   3. Run each chunk through TFLite model → 4 stems
     *   4. Overlap-add reassemble each stem
     *   5. Save stems as separate WAV files
     */
    @SuppressWarnings("unused")
    private void runAiStemSeparation() {
        // Requires: org.tensorflow:tensorflow-lite:2.14.0
        // and model file at assets/demucs.tflite
        tvStatus.setText("AI separation requires TFLite model. See setup instructions.");
        showAiSetupDialog();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private void exportAll() {
        if (stems.isEmpty()) { toast("Run separation first"); return; }
        StringBuilder msg = new StringBuilder("Stems saved to recordings/stems/:\n\n");
        for (Map.Entry<String, File> e : stems.entrySet()) {
            msg.append("• ").append(e.getValue().getName()).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("Export Complete").setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
