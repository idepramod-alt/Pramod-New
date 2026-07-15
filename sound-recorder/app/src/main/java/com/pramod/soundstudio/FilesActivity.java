package com.pramod.soundstudio;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.*;

public class FilesActivity extends AppCompatActivity {

    private static final int REQUEST_DEVICE_FILE = 9001;

    private RecyclerView    recyclerView;
    private FilesAdapter    adapter;
    private List<File>      fileList = new ArrayList<>();
    private TextView        tvEmpty;
    private ProjectManager  projectManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files);

        tvEmpty      = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerFiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FilesAdapter();
        recyclerView.setAdapter(adapter);

        projectManager = new ProjectManager(this);

        // FAB: go to RecordActivity
        View fab = findViewById(R.id.fabRecord);
        if (fab != null) fab.setOnClickListener(v ->
                startActivity(new Intent(this, RecordActivity.class)));

        // FAB: import an audio file from anywhere on the device
        View fabImport = findViewById(R.id.fabImport);
        if (fabImport != null) fabImport.setOnClickListener(v ->
                DeviceFileImporter.launchPicker(this, REQUEST_DEVICE_FILE, true));

        // Top bar: named projects (save/load groups of recordings)
        View btnProjects = findViewById(R.id.btnProjects);
        if (btnProjects != null) btnProjects.setOnClickListener(v -> showProjectsDialog());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_FILE && resultCode == RESULT_OK && data != null) {
            File dir = new File(getFilesDir(), "recordings");
            List<File> imported = DeviceFileImporter.handleResult(this, data, dir);
            if (!imported.isEmpty()) {
                loadFiles();
                Toast.makeText(this, "Imported " + imported.size() + " file(s)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFiles();
    }

    private void loadFiles() {
        fileList.clear();
        File dir = new File(getFilesDir(), "recordings");
        if (dir.exists()) {
            File[] files = dir.listFiles(f ->
                    f.getName().endsWith(".wav")
                 || f.getName().endsWith(".m4a")
                 || f.getName().endsWith(".mp3"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                fileList.addAll(Arrays.asList(files));
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(fileList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(fileList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── RecyclerView Adapter ─────────────────────────────────────────────────

    class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_audio_file, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            File file = fileList.get(position);
            h.bind(file);
        }

        @Override public int getItemCount() { return fileList.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo, tvBadge;
            Button   btnEdit, btnConvert, btnOctapad, btnDelete;

            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tvFileName);
                tvInfo    = v.findViewById(R.id.tvFileInfo);
                tvBadge   = v.findViewById(R.id.tvFormatBadge);
                btnEdit   = v.findViewById(R.id.btnEdit);
                btnConvert= v.findViewById(R.id.btnConvert);
                btnOctapad= v.findViewById(R.id.btnOctapad);
                btnDelete = v.findViewById(R.id.btnDelete);
            }

            void bind(File file) {
                tvName.setText(file.getName());

                String ext = file.getName().contains(".")
                        ? file.getName().substring(file.getName().lastIndexOf('.') + 1).toUpperCase()
                        : "?";
                tvBadge.setText(ext);

                // Duration + size
                String dur = getDuration(file);
                long   kb  = file.length() / 1024;
                tvInfo.setText(dur + "  •  " + kb + " KB");

                // ── Edit ──────────────────────────────────────────────────────
                btnEdit.setOnClickListener(v -> {
                    Intent i = new Intent(FilesActivity.this, EditorActivity.class);
                    i.putExtra(EditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                    startActivity(i);
                });

                // ── Convert WAV ↔ Info ────────────────────────────────────────
                btnConvert.setOnClickListener(v -> showConvertDialog(file));

                // ── Load to Octapad ───────────────────────────────────────────
                btnOctapad.setOnClickListener(v -> loadToOctapad(file));

                // ── Delete ─────────────────────────────────────────────────────
                btnDelete.setOnClickListener(v -> confirmDelete(file));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getDuration(File file) {
        try {
            if (file.getName().endsWith(".wav")) {
                int[] hdr = AudioTrimmer.readWavHeader(file);
                long  dataBytes = file.length() - AudioTrimmer.WAV_HEADER_SIZE;
                double secs = (double) dataBytes / (hdr[0] * hdr[1] * hdr[2] / 8.0);
                return String.format(Locale.US, "%.1fs", secs);
            } else {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(file.getAbsolutePath());
                String ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                mmr.release();
                if (ms != null) {
                    double secs = Long.parseLong(ms) / 1000.0;
                    return String.format(Locale.US, "%.1fs", secs);
                }
            }
        } catch (Exception ignored) {}
        return "?s";
    }

    private void loadToOctapad(File file) {
        try {
            android.net.Uri uri = FileProvider.getUriForFile(
                    this, "com.pramod.soundstudio.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = file.getName().endsWith(".wav") ? "audio/wav" : "audio/mp4";
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setPackage("com.pramod.loopmidi");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
            intent.setPackage(null);
            startActivity(Intent.createChooser(intent, "Open with…"));
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showConvertDialog(File file) {
        if (!file.getName().endsWith(".wav")) {
            Toast.makeText(this, "Only WAV files can be converted here", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {
                "Copy to 22.05kHz WAV",
                "Copy to 44.1kHz WAV",
                "Copy to 48kHz WAV",
                "Share file"
        };

        new AlertDialog.Builder(this)
                .setTitle("Convert / Export: " + file.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 3) {
                        shareFile(file);
                    } else {
                        int[] targetRates = {22050, 44100, 48000};
                        resampleWav(file, targetRates[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resampleWav(File src, int targetRate) {
        Toast.makeText(this, "⏳ Converting…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                int[] hdr = AudioTrimmer.readWavHeader(src);
                if (hdr[0] == targetRate) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Already " + targetRate + "Hz", Toast.LENGTH_SHORT).show());
                    return;
                }
                short[] samples = AudioTrimmer.readWavPcm(src);
                // Simple nearest-neighbour resample
                double ratio  = (double) targetRate / hdr[0];
                int    newLen = (int)(samples.length * ratio);
                short[] resampled = new short[newLen];
                for (int i = 0; i < newLen; i++) {
                    int srcIdx = Math.min((int)(i / ratio), samples.length - 1);
                    resampled[i] = samples[srcIdx];
                }
                String outName = src.getName().replace(".wav",
                        "_" + (targetRate / 1000) + "k.wav");
                File outFile = new File(src.getParentFile(), outName);
                AudioTrimmer.writePcmToWav(resampled, targetRate, hdr[1], outFile);
                runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Saved: " + outName, Toast.LENGTH_SHORT).show();
                    loadFiles();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void shareFile(File file) {
        try {
            android.net.Uri uri = FileProvider.getUriForFile(
                    this, "com.pramod.soundstudio.fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("audio/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share audio…"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Delete " + file.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    file.delete();
                    loadFiles();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Projects (named groups of recordings, saved via ProjectManager) ──────

    private void showProjectsDialog() {
        List<ProjectManager.Project> projects = projectManager.listProjects();
        String[] items = new String[projects.size() + 1];
        items[0] = "＋ New Project (save current recordings)";
        for (int i = 0; i < projects.size(); i++) items[i + 1] = "📁 " + projects.get(i).toString();

        new AlertDialog.Builder(this)
                .setTitle("My Projects")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showCreateProjectDialog();
                    } else {
                        showProjectDetailDialog(projects.get(which - 1));
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showCreateProjectDialog() {
        if (fileList.isEmpty()) {
            Toast.makeText(this, "No recordings to save yet", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(this);
        input.setHint("Project name");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);

        List<String> paths = new ArrayList<>();
        for (File f : fileList) paths.add(f.getAbsolutePath());

        new AlertDialog.Builder(this)
                .setTitle("New Project")
                .setMessage(fileList.size() + " recording(s) will be included")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "Untitled";
                    try {
                        projectManager.createProject(name, paths, "");
                        Toast.makeText(this, "✅ Project saved: " + name, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showProjectDetailDialog(ProjectManager.Project project) {
        List<File> files = new ArrayList<>();
        for (String p : project.filePaths) {
            File f = new File(p);
            if (f.exists()) files.add(f);
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();

        new AlertDialog.Builder(this)
                .setTitle(project.name)
                .setItems(names.length > 0 ? names : new String[]{"(no files found)"},
                        (dialog, which) -> {
                            if (which < files.size()) {
                                Intent i = new Intent(this, EditorActivity.class);
                                i.putExtra(EditorActivity.EXTRA_FILE_PATH, files.get(which).getAbsolutePath());
                                startActivity(i);
                            }
                        })
                .setPositiveButton("Delete Project", (d, w) -> {
                    projectManager.deleteProject(project.id);
                    Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
