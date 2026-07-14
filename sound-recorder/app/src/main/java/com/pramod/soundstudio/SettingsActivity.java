package com.pramod.soundstudio;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** Settings Activity — format defaults, recording paths, theme, misc. */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "soundstudio_prefs";
    private SharedPreferences prefs;

    private Spinner  spDefaultFormat, spDefaultSampleRate, spDefaultBitDepth;
    private CheckBox cbAutoTrimOnSave, cbNormalizeOnSave, cbShowWaveform;
    private EditText etOutputPath;
    private Button   btnSave, btnReset;
    private TextView tvVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        spDefaultFormat    = findViewById(R.id.spDefaultFormat);
        spDefaultSampleRate= findViewById(R.id.spDefaultSampleRate);
        spDefaultBitDepth  = findViewById(R.id.spDefaultBitDepth);
        cbAutoTrimOnSave   = findViewById(R.id.cbAutoTrimOnSave);
        cbNormalizeOnSave  = findViewById(R.id.cbNormalizeOnSave);
        cbShowWaveform     = findViewById(R.id.cbShowWaveform);
        etOutputPath       = findViewById(R.id.etOutputPath);
        btnSave            = findViewById(R.id.btnSave);
        btnReset           = findViewById(R.id.btnReset);
        tvVersion          = findViewById(R.id.tvVersion);

        // Format spinner
        ArrayAdapter<String> fmtAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, new String[]{"WAV (lossless)", "AAC / M4A"});
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDefaultFormat.setAdapter(fmtAdapter);

        // Sample rate spinner
        ArrayAdapter<String> srAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"44100 Hz", "48000 Hz", "22050 Hz", "16000 Hz"});
        srAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDefaultSampleRate.setAdapter(srAdapter);

        // Bit depth spinner
        ArrayAdapter<String> bdAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, new String[]{"16-bit", "24-bit"});
        bdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDefaultBitDepth.setAdapter(bdAdapter);

        loadSettings();
        tvVersion.setText("Pramod Sound Studio  v2.0.0  |  © Pramod");

        btnSave.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });
        btnReset.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            loadSettings();
            Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSettings() {
        spDefaultFormat.setSelection(prefs.getInt("default_format", 0));
        spDefaultSampleRate.setSelection(prefs.getInt("default_sample_rate", 0));
        spDefaultBitDepth.setSelection(prefs.getInt("default_bit_depth", 0));
        cbAutoTrimOnSave.setChecked(prefs.getBoolean("auto_trim_on_save", false));
        cbNormalizeOnSave.setChecked(prefs.getBoolean("normalize_on_save", false));
        cbShowWaveform.setChecked(prefs.getBoolean("show_waveform", true));
        etOutputPath.setText(prefs.getString("output_path", "Default (internal/recordings/)"));
    }

    private void saveSettings() {
        prefs.edit()
            .putInt("default_format",      spDefaultFormat.getSelectedItemPosition())
            .putInt("default_sample_rate", spDefaultSampleRate.getSelectedItemPosition())
            .putInt("default_bit_depth",   spDefaultBitDepth.getSelectedItemPosition())
            .putBoolean("auto_trim_on_save",   cbAutoTrimOnSave.isChecked())
            .putBoolean("normalize_on_save",   cbNormalizeOnSave.isChecked())
            .putBoolean("show_waveform",        cbShowWaveform.isChecked())
            .putString("output_path",           etOutputPath.getText().toString())
            .apply();
    }
}
