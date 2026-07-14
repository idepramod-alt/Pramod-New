package com.pramod.soundstudio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ProjectManager — save/load/history for named sessions.
 *
 * A "project" contains:
 *   - Project name and creation date
 *   - List of associated audio file paths
 *   - Last used settings (format, sample rate, auto-trim params)
 *   - Notes / description
 *
 * Projects stored as JSON files in filesDir/projects/
 * Recent file history stored in SharedPreferences (fast lookup)
 */
public class ProjectManager {

    private static final String PREFS_NAME     = "soundstudio_projects";
    private static final String KEY_RECENT     = "recent_files";
    private static final int    MAX_RECENT     = 20;

    private final Context context;
    private final File    projectsDir;

    // ─────────────────────────────────────────────────────────────────────────

    public ProjectManager(Context context) {
        this.context     = context;
        this.projectsDir = new File(context.getFilesDir(), "projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();
    }

    // ── Project CRUD ─────────────────────────────────────────────────────────

    public Project createProject(String name, List<String> filePaths, String notes) throws Exception {
        Project p = new Project();
        p.id         = UUID.randomUUID().toString().substring(0, 8);
        p.name       = name;
        p.createdAt  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        p.filePaths  = filePaths != null ? new ArrayList<>(filePaths) : new ArrayList<>();
        p.notes      = notes != null ? notes : "";
        saveProject(p);
        return p;
    }

    public void saveProject(Project p) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id",        p.id);
        json.put("name",      p.name);
        json.put("createdAt", p.createdAt);
        json.put("notes",     p.notes);

        JSONArray arr = new JSONArray();
        for (String fp : p.filePaths) arr.put(fp);
        json.put("filePaths", arr);

        File f = new File(projectsDir, p.id + ".json");
        try (FileWriter w = new FileWriter(f)) { w.write(json.toString(2)); }
    }

    public Project loadProject(String id) throws Exception {
        File f = new File(projectsDir, id + ".json");
        if (!f.exists()) throw new FileNotFoundException("Project not found: " + id);
        String raw = readFile(f);
        return parseProject(new JSONObject(raw));
    }

    public void deleteProject(String id) {
        new File(projectsDir, id + ".json").delete();
    }

    public List<Project> listProjects() {
        List<Project> list = new ArrayList<>();
        File[] files = projectsDir.listFiles(f -> f.getName().endsWith(".json"));
        if (files == null) return list;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            try {
                list.add(parseProject(new JSONObject(readFile(f))));
            } catch (Exception ignored) {}
        }
        return list;
    }

    // ── Recent Files History ─────────────────────────────────────────────────

    public void addRecentFile(String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<String> recent = getRecentFiles();
        recent.remove(path);           // Remove if already present (dedup)
        recent.add(0, path);           // Add to front
        if (recent.size() > MAX_RECENT) recent = recent.subList(0, MAX_RECENT);

        JSONArray arr = new JSONArray();
        for (String s : recent) arr.put(s);
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply();
    }

    public List<String> getRecentFiles() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }

    public List<File> getRecentFileObjects() {
        List<File> files = new ArrayList<>();
        for (String path : getRecentFiles()) {
            File f = new File(path);
            if (f.exists()) files.add(f);
        }
        return files;
    }

    public void clearRecentFiles() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_RECENT).apply();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project parseProject(JSONObject json) throws Exception {
        Project p = new Project();
        p.id        = json.optString("id",        "");
        p.name      = json.optString("name",      "Untitled");
        p.createdAt = json.optString("createdAt", "");
        p.notes     = json.optString("notes",     "");
        p.filePaths = new ArrayList<>();
        JSONArray arr = json.optJSONArray("filePaths");
        if (arr != null) for (int i = 0; i < arr.length(); i++) p.filePaths.add(arr.getString(i));
        return p;
    }

    private String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ── Project Model ─────────────────────────────────────────────────────────

    public static class Project {
        public String       id;
        public String       name;
        public String       createdAt;
        public String       notes;
        public List<String> filePaths = new ArrayList<>();

        @Override
        public String toString() { return name + " (" + filePaths.size() + " files)"; }
    }
}
