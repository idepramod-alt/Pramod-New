package com.pramod.soundstudio;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.*;

/**
 * DashboardActivity — Main entry point.
 * Shows all professional modules as cards + quick access to recent files.
 * All existing features (Recorder, Trim) are accessible from here.
 */
public class DashboardActivity extends AppCompatActivity {

    // ── Module definitions ────────────────────────────────────────────────────

    static class Module {
        final String icon, title, subtitle;
        final int accentColor;
        final Class<?> target;

        Module(String icon, String title, String subtitle, int accent, Class<?> target) {
            this.icon = icon; this.title = title; this.subtitle = subtitle;
            this.accentColor = accent; this.target = target;
        }
    }

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

    private final List<Module> modules = Arrays.asList(
        new Module("🎙️", "Recorder",       "High-quality audio recording",       RED,    RecordActivity.class),
        new Module("📁", "My Files",        "Browse & manage recordings",          BLUE,   FilesActivity.class),
        new Module("⚡", "Auto Trim",       "Precision drum sample auto-trim",     AMBER,  AutoTrimActivity.class),
        new Module("🥁", "Drum Splitter",   "Split hits into separate samples",    ORANGE, DrumSplitterActivity.class),
        new Module("🎤", "Vocal Remover",   "Separate vocals, drums, bass...",     PURPLE, VocalRemoverActivity.class),
        new Module("✨", "Enhancement",     "EQ, compressor, de-noise, normalize", TEAL,   EnhancementActivity.class),
        new Module("🎛️", "Audio Editor",   "Cut, copy, fade, markers, merge",     INDIGO, EditorLauncherActivity.class),
        new Module("📊", "Analysis",        "BPM, frequency, loudness, peaks",     GREEN,  AnalysisActivity.class),
        new Module("🎵", "Effects",         "Reverb, echo, pitch, chorus",         PINK,   EffectsActivity.class),
        new Module("🔄", "Converter",       "WAV · MP3 · FLAC · AAC · OGG",       BROWN,  ConverterActivity.class),
        new Module("📦", "Batch Process",   "Auto-trim, normalize, export all",    0xFF37474F, BatchActivity.class),
        new Module("🥁", "Octapad Tools",   "Kit builder, sample prep, export",    0xFF1B5E20, OctapadToolsActivity.class),
        new Module("⚙️", "Settings",        "Format defaults, paths, theme",        0xFF424242, SettingsActivity.class)
    );

    private RecyclerView  rvModules;
    private TextView      tvRecentHeader;
    private LinearLayout  llRecentFiles;
    private ModuleAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        rvModules       = findViewById(R.id.rvModules);
        tvRecentHeader  = findViewById(R.id.tvRecentHeader);
        llRecentFiles   = findViewById(R.id.llRecentFiles);

        // 2-column grid
        rvModules.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ModuleAdapter();
        rvModules.setAdapter(adapter);

        loadRecentFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentFiles();
    }

    // ── Recent files ─────────────────────────────────────────────────────────

    private void loadRecentFiles() {
        llRecentFiles.removeAllViews();
        File dir = new File(getFilesDir(), "recordings");
        List<File> recent = new ArrayList<>();

        if (dir.exists()) {
            File[] files = dir.listFiles(f ->
                f.getName().endsWith(".wav") || f.getName().endsWith(".m4a") ||
                f.getName().endsWith(".mp3") || f.getName().endsWith(".flac") ||
                f.getName().endsWith(".aac") || f.getName().endsWith(".ogg"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (int i = 0; i < Math.min(5, files.length); i++) recent.add(files[i]);
            }
        }

        tvRecentHeader.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);

        for (File f : recent) {
            View row = getLayoutInflater().inflate(R.layout.item_recent_file, llRecentFiles, false);
            TextView tvName = row.findViewById(R.id.tvRecentName);
            TextView tvSize = row.findViewById(R.id.tvRecentSize);
            tvName.setText(f.getName());
            tvSize.setText(f.length() / 1024 + " KB");
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, EditorActivity.class);
                i.putExtra(EditorActivity.EXTRA_FILE_PATH, f.getAbsolutePath());
                startActivity(i);
            });
            llRecentFiles.addView(row);
        }
    }

    // ── RecyclerView adapter ──────────────────────────────────────────────────

    class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_dashboard_module, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            Module m = modules.get(pos);
            h.tvIcon.setText(m.icon);
            h.tvTitle.setText(m.title);
            h.tvSubtitle.setText(m.subtitle);
            h.card.setCardBackgroundColor(0xFF1A1A1A);
            h.vAccent.setBackgroundColor(m.accentColor);
            h.card.setOnClickListener(v -> {
                if (m.target != null) startActivity(new Intent(DashboardActivity.this, m.target));
            });
        }

        @Override public int getItemCount() { return modules.size(); }

        class VH extends RecyclerView.ViewHolder {
            CardView card;
            TextView tvIcon, tvTitle, tvSubtitle;
            View     vAccent;
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
