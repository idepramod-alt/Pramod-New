package com.pramod.soundstudio;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.*;

/**
 * DashboardActivity — Main launcher (v3.0, backward-compatible).
 *
 * Changes from v2.0:
 *  • 🎯 AI Rhythm Slicer module added (top-3 priority position)
 *  • Recorder card opens a dialog: Normal mode → RecordActivity (unchanged)
 *                                   Echo mode  → EchoRecordActivity (new)
 *  • All existing modules and their targets are untouched.
 */
public class DashboardActivity extends AppCompatActivity {

    static class Module {
        final String icon, title, subtitle;
        final int    accentColor;
        final Class<?> target;
        final int    actionId;   // 0 = direct launch, >0 = custom action
        Module(String i, String t, String s, int c, Class<?> cls) { this(i,t,s,c,cls,0); }
        Module(String i, String t, String s, int c, Class<?> cls, int a) {
            icon=i; title=t; subtitle=s; accentColor=c; target=cls; actionId=a;
        }
    }

    private static final int ACTION_RECORDER = 1;

    private static final int RED    = 0xFFE53935;
    private static final int AMBER  = 0xFFFF8F00;
    private static final int BLUE   = 0xFF0288D1;
    private static final int GREEN  = 0xFF2E7D32;
    private static final int PURPLE = 0xFF7B1FA2;
    private static final int TEAL   = 0xFF00695C;
    private static final int ORANGE = 0xFFE65100;
    private static final int INDIGO = 0xFF283593;
    private static final int PINK   = 0xFFC2185B;
    private static final int BROWN  = 0xFF4E342E;
    private static final int DEEP_O = 0xFFBF360C;

    private final List<Module> modules = Arrays.asList(
        new Module("🎙️", "Recorder",       "Normal · Echo · Waveform · USB",          RED,    null,                    ACTION_RECORDER),
        new Module("📁", "My Files",        "Browse · Play · Share · Delete",           BLUE,   FilesActivity.class),
        new Module("🎯", "AI Rhythm Slicer","Drum hits · BPM · Octapad kit export",    DEEP_O, RhythmSlicerActivity.class),
        new Module("⚡", "Auto Trim",       "Ultra-precision silence removal",           AMBER,  AutoTrimActivity.class),
        new Module("🥁", "Drum Splitter",   "Onset detection · Per-hit WAV export",     ORANGE, DrumSplitterActivity.class),
        new Module("🎤", "Vocal Remover",   "Phase cancel · AI stem separation",        PURPLE, VocalRemoverActivity.class),
        new Module("✨", "Enhancement",     "EQ · Compressor · Noise reduction",        TEAL,   EnhancementActivity.class),
        new Module("🎛️", "Audio Editor",   "Cut · Copy · Fade · Markers · Merge",      INDIGO, EditorLauncherActivity.class),
        new Module("📊", "Analysis",        "BPM · LUFS · Peak · Clipping",             GREEN,  AnalysisActivity.class),
        new Module("🎵", "Effects",         "Reverb · Echo · Pitch · Chorus",           PINK,   EffectsActivity.class),
        new Module("🔄", "Converter",       "WAV · MP3 · FLAC · AAC · OGG",             BROWN,  ConverterActivity.class),
        new Module("📦", "Batch Process",   "Trim · Normalize · Convert · Export all",  0xFF37474F, BatchActivity.class),
        new Module("🥁", "Octapad Tools",   "Kit builder · Sample prep · Export",       0xFF1B5E20, OctapadToolsActivity.class),
        new Module("⚙️", "Settings",        "Format · Sample rate · Paths · Theme",      0xFF424242, SettingsActivity.class)
    );

    private RecyclerView  rvModules;
    private TextView      tvRecentHeader;
    private LinearLayout  llRecentFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        rvModules      = findViewById(R.id.rvModules);
        tvRecentHeader = findViewById(R.id.tvRecentHeader);
        llRecentFiles  = findViewById(R.id.llRecentFiles);
        rvModules.setLayoutManager(new GridLayoutManager(this, 2));
        rvModules.setAdapter(new ModuleAdapter());
        loadRecentFiles();
    }

    @Override protected void onResume() { super.onResume(); loadRecentFiles(); }

    // ── Recent files ──────────────────────────────────────────────────────────
    private void loadRecentFiles() {
        llRecentFiles.removeAllViews();
        File dir = new File(getFilesDir(), "recordings");
        List<File> recent = new ArrayList<>();
        if (dir.exists()) {
            File[] files = dir.listFiles(f -> f.isFile() &&
                (f.getName().endsWith(".wav") || f.getName().endsWith(".m4a") ||
                 f.getName().endsWith(".mp3") || f.getName().endsWith(".flac") ||
                 f.getName().endsWith(".aac") || f.getName().endsWith(".ogg")));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (int i = 0; i < Math.min(5, files.length); i++) recent.add(files[i]);
            }
        }
        tvRecentHeader.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        for (File f : recent) {
            View row = getLayoutInflater().inflate(R.layout.item_recent_file, llRecentFiles, false);
            ((TextView) row.findViewById(R.id.tvRecentName)).setText(f.getName());
            ((TextView) row.findViewById(R.id.tvRecentSize)).setText(UniversalExportHelper.formatSize(f.length()));
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, EditorActivity.class);
                i.putExtra(EditorActivity.EXTRA_FILE_PATH, f.getAbsolutePath());
                startActivity(i);
            });
            llRecentFiles.addView(row);
        }
    }

    // ── Module tap ────────────────────────────────────────────────────────────
    private void onModuleTapped(Module m) {
        if (m.actionId == ACTION_RECORDER) showRecorderDialog();
        else if (m.target != null)         startActivity(new Intent(this, m.target));
    }

    private void showRecorderDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🎙️ Choose Recording Mode")
            .setItems(new CharSequence[]{
                "🎙️  Normal  —  High-quality mic / USB recording",
                "🔊  Echo    —  Real-time delay · feedback · dry/wet"
            }, (d, which) -> startActivity(new Intent(this,
                    which == 0 ? RecordActivity.class : EchoRecordActivity.class)))
            .setNegativeButton("Cancel", null).show();
    }

    // ── RecyclerView adapter ──────────────────────────────────────────────────
    class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.VH> {
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_dashboard_module, parent, false));
        }
        @Override
        public void onBindViewHolder(VH h, int pos) {
            Module m = modules.get(pos);
            h.tvIcon.setText(m.icon);
            h.tvTitle.setText(m.title);
            h.tvSubtitle.setText(m.subtitle);
            h.vAccent.setBackgroundColor(m.accentColor);
            // Highlight Rhythm Slicer as featured module
            h.card.setCardBackgroundColor(m.target == RhythmSlicerActivity.class
                    ? 0xFF1F1008 : 0xFF1A1A1A);
            h.card.setOnClickListener(v -> onModuleTapped(m));
        }
        @Override public int getItemCount() { return modules.size(); }

        class VH extends RecyclerView.ViewHolder {
            CardView card; TextView tvIcon, tvTitle, tvSubtitle; View vAccent;
            VH(View v) {
                super(v);
                card       = v.findViewById(R.id.card);
                tvIcon     = v.findViewById(R.id.tvModuleIcon);
                tvTitle    = v.findViewById(R.id.tvModuleTitle);
                tvSubtitle = v.findViewById(R.id.tvModuleSubtitle);
                vAccent    = v.findViewById(R.id.vAccentBar);
            }
        }
    }
}
