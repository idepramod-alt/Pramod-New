package com.pramod.soundstudio;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.recyclerview.widget.*;
import java.io.File;
import java.util.*;

/**
 * Editor Launcher — shows all audio files, lets user pick one for EditorActivity.
 * Bridges the Dashboard to the existing EditorActivity without modifying it.
 */
public class EditorLauncherActivity extends AppCompatActivity {

    private List<File>    files   = new ArrayList<>();
    private RecyclerView  rv;
    private TextView      tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor_launcher);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rv      = findViewById(R.id.rvFiles);
        tvEmpty = findViewById(R.id.tvEmpty);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new Adapter());
        loadFiles();
    }

    @Override protected void onResume() { super.onResume(); loadFiles(); }

    private void loadFiles() {
        files.clear();
        File dir = new File(getFilesDir(), "recordings");
        if (dir.exists()) {
            File[] all = dir.listFiles(f -> !f.isDirectory());
            if (all != null) {
                Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                files.addAll(Arrays.asList(all));
            }
        }
        rv.getAdapter().notifyDataSetChanged();
        tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(R.layout.item_audio_file, p, false);
            return new VH(v);
        }
        public void onBindViewHolder(VH h, int pos) {
            File f = files.get(pos);
            h.tvName.setText(f.getName());
            h.tvInfo.setText(f.length() / 1024 + " KB");
            String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf('.')+1).toUpperCase() : "?";
            h.tvBadge.setText(ext);
            h.btnEdit.setOnClickListener(v -> {
                Intent i = new Intent(EditorLauncherActivity.this, EditorActivity.class);
                i.putExtra(EditorActivity.EXTRA_FILE_PATH, f.getAbsolutePath());
                startActivity(i);
            });
            // Hide other buttons not needed here
            if (h.btnConvert  != null) h.btnConvert.setVisibility(View.GONE);
            if (h.btnOctapad  != null) h.btnOctapad.setVisibility(View.GONE);
            if (h.btnDelete   != null) h.btnDelete.setVisibility(View.GONE);
        }
        public int getItemCount() { return files.size(); }
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
        }
    }
}
