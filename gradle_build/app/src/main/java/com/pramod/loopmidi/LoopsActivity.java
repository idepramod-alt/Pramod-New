package com.pramod.loopmidi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.documentfile.provider.DocumentFile;
import com.pramod.loopmidi.AudioEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import kotlin.UByte;
import org.json.JSONException;
import org.json.JSONObject;
/* loaded from: classes3.dex */
public class LoopsActivity extends Activity implements DialogInterface.OnClickListener {
    private static final String KEY_LOOP_INDEX = "current_loop_index";
    private static final int LOOP_PAD_COUNT = 8;
    private static final int MAX_LOOPS = 50;
    private static final String PREF_NAME = "OctapadSettings";
    private static final int REQ_LOAD_LOOP_FOLDER = 6003;
    private static final int REQ_PICK_LOOP_WAV = 6001;
    private static final int REQ_SAVE_LOOP_FOLDER = 6002;
    public static LoopsActivity globalInstance;
    private View advancedControlPanel;
    private Button btnAdvancedLoops;
    private Button btnBack;
    private Button btnEditLoops;
    private Button btnLoadLoop;
    private Button btnNextLoop;
    private Button btnPrevLoop;
    private Button btnRenameLoop;
    private Button btnSaveLoop;
    private Button btnSetBpm;
    private Button btnTapTempo;
    private Button btnTempoMinus;
    private Button btnTempoPlus;
    private CheckBox chkMultiMode;
    private CheckBox chkOneShotMode;
    private int currentScaleOffset;
    private EditText editCustomBpm;
    private Equalizer globalEq;
    private PresetReverb globalReverb;
    private boolean isVisible;
    private MidiManager midiManager;
    private MidiOutputPort midiOutputPort;
    private MidiDevice openedMidiDevice;
    private SharedPreferences prefs;
    private SeekBar seekMasterVolume;
    private SeekBar seekPitch;
    private SeekBar seekTempo;
    private ArrayList tempKitFolders;
    private TextView txtLoopChannel;
    private TextView txtLoopStatus;
    private TextView txtMasterVolVal;
    private TextView txtMidiStatus;
    private TextView txtPitchVal;
    private TextView txtTempoVal;
    private Button[] loopPads = new Button[8];
    private String currentLoopName = "LOOP 1";
    private String pendingSaveLoopName = null;
    private int loopChannelIndex = 1;
    private float currentSpeed = 1.0f;
    private float currentPitch = 1.0f;
    private float masterVolume = 1.0f;
    private int reverbLevel = 0;
    private boolean isMultiMode = false;
    private boolean isOneShotMode = false;
    private long[] tapTimes = new long[4];
    private int tapIndex = 0;
    private boolean editMode = false;
    private int selectedPad = 0;
    private Uri[] loopUris = new Uri[8];
    private AudioEngine audioEngine;
    private AudioEngine.SampleData[] loopSamples = new AudioEngine.SampleData[8];
    private boolean[] loopPlaying = new boolean[8];
    // Load-generation token: incremented on every kit/channel change.
    // Background threads capture the value at start and discard results if it changed.
    private volatile int loadGeneration = 0;
    // Per-pad flag: true while a background decode is in progress for that pad.
    // Prevents duplicate decode threads when user taps a not-yet-loaded pad rapidly.
    private boolean[] padLoadingInFlight = new boolean[8];

    public static void callKitChange(LoopsActivity loopsActivity, int i) {
        loopsActivity.handleProgramChange(i);
    }

    private boolean prepareLoopSample(int index) {
        Uri uri = this.loopUris[index];
        if (uri == null || this.audioEngine == null) {
            return false;
        }
        try {
            AudioEngine.SampleData sampleData = this.audioEngine.loadWavFromUri(index, uri);
            if (sampleData == null || !sampleData.loaded) {
                return false;
            }
            this.loopSamples[index] = sampleData;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void toggleLoop(final int index) {
        // A pad has content if it has a user-picked URI (custom loop) OR a sample already
        // decoded from assets (preset kit). The URI is null for asset-based kits because
        // Android assets don't have content:// URIs — only loopSamples[] is populated.
        AudioEngine.SampleData sampleData = this.loopSamples[index];
        boolean hasContent = this.loopUris[index] != null
                || (sampleData != null && sampleData.loaded);
        if (!hasContent) {
            this.txtLoopStatus.setText("LOOP " + (index + 1) + " IS EMPTY");
            return;
        }
        if (sampleData == null || !sampleData.loaded) {
            // Prevent duplicate decode threads: if a load is already in progress for this
            // pad, ignore the tap — the completion handler will replay it.
            if (this.padLoadingInFlight[index]) return;
            this.padLoadingInFlight[index] = true;
            // Show status immediately so the user knows something is happening.
            this.txtLoopStatus.setText("LOADING LOOP " + (index + 1) + "...");
            // Snapshot engine — safe against onDestroy races
            final AudioEngine engine = this.audioEngine;
            if (engine == null) { this.padLoadingInFlight[index] = false; return; }
            new Thread(() -> {
                final boolean ok = prepareLoopSample(index);
                new Handler(Looper.getMainLooper()).post(() -> {
                    this.padLoadingInFlight[index] = false;
                    if (!ok) {
                        this.txtLoopStatus.setText("ERROR LOADING LOOP " + (index + 1));
                        return;
                    }
                    // Replay the tap now that the sample is ready
                    toggleLoop(index);
                });
            }).start();
            return;
        }
        if (this.isOneShotMode) {
            // ONE-SHOT: play once on each tap, no auto-repeat.
            if (this.loopPlaying[index]) {
                this.loopPlaying[index] = false;
                this.loopPads[index].setBackgroundResource(R.drawable.pad_black_selector);
            }
            this.audioEngine.playSample(index, sampleData, this.masterVolume, this.currentSpeed, this.currentPitch, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f);
            this.txtLoopStatus.setText("ONE-SHOT: LOOP " + (index + 1));
            if (!this.isMultiMode) {
                for (int i = 0; i < 8; i++) {
                    if (i != index && this.loopPlaying[i]) {
                        this.audioEngine.stopPad(i);
                        this.loopPlaying[i] = false;
                        this.loopPads[i].setBackgroundResource(R.drawable.pad_black_selector);
                    }
                }
            }
            return;
        }
        // AUTO-REPEAT mode: loopMode=1 tells the native engine to loop the sample
        // continuously until stopPad() is called. Tapping the pad again stops it.
        if (this.loopPlaying[index]) {
            this.audioEngine.stopPad(index);
            this.loopPlaying[index] = false;
            this.txtLoopStatus.setText("LOOP " + (index + 1) + " STOPPED");
            this.loopPads[index].setBackgroundResource(R.drawable.pad_black_selector);
            return;
        }
        // loopMode=1 → auto-repeat until explicitly stopped
        this.audioEngine.playSample(index, sampleData, this.masterVolume, this.currentSpeed, this.currentPitch, 1, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f);
        this.audioEngine.playLoopSP(index, this.masterVolume, this.currentSpeed, this.currentPitch);
        this.loopPlaying[index] = true;
        this.txtLoopStatus.setText("PLAYING LOOP " + (index + 1));
        this.loopPads[index].setBackgroundResource(R.drawable.pad_blue_glow_selector);
        if (this.isMultiMode) {
            return;
        }
        // Single-pad mode: new pad starts → previous pad stops automatically
        for (int i = 0; i < 8; i++) {
            if (i != index && this.loopPlaying[i]) {
                this.audioEngine.stopPad(i);
                this.loopPlaying[i] = false;
                this.loopPads[i].setBackgroundResource(R.drawable.pad_black_selector);
            }
        }
    }

    private void updateEQ() {
        try {
            if (this.globalEq == null) {
                Equalizer equalizer = new Equalizer(0, 0);
                this.globalEq = equalizer;
                equalizer.setEnabled(true);
            }
            View decorView = getWindow().getDecorView();
            SeekBar seekBar = (SeekBar) decorView.findViewWithTag("seekEqHi");
            SeekBar seekBar2 = (SeekBar) decorView.findViewWithTag("seekEqMid");
            SeekBar seekBar3 = (SeekBar) decorView.findViewWithTag("seekEqLow");
            if (seekBar == null || seekBar2 == null || seekBar3 == null) {
                return;
            }
            int progress = seekBar.getProgress();
            int progress2 = seekBar2.getProgress();
            short s = (short) ((progress - 50) * 30);
            short progress3 = (short) ((seekBar3.getProgress() - 50) * 30);
            Equalizer equalizer2 = this.globalEq;
            equalizer2.setBandLevel((short) 0, progress3);
            equalizer2.setBandLevel((short) 1, progress3);
            equalizer2.setBandLevel((short) 2, (short) ((progress2 - 50) * 30));
            equalizer2.setBandLevel((short) 3, s);
            equalizer2.setBandLevel((short) 4, s);
        } catch (Throwable th) {
        }
    }

    private void updateScaleUI() {
        int pow = (int) (Math.pow(2.0d, this.currentScaleOffset / 12.0d) * 100.0d);
        SeekBar seekBar = (SeekBar) findViewById(R.id.seekPitch);
        if (seekBar != null) {
            seekBar.setProgress(pow);
        }
        TextView textView = (TextView) getWindow().getDecorView().findViewWithTag("txtScaleDisplay");
        if (textView != null) {
            String[] split = "C,C#,D,D#,E,F,F#,G,G#,A,A#,B".split(",");
            int i = this.currentScaleOffset % 12;
            if (i < 0) {
                i += 12;
            }
            textView.setText(split[i]);
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            updateEQ();
        } catch (Throwable unused) {
        }
        return super.dispatchTouchEvent(ev);
    }

    public void handleProgramChange(int i) {
        this.loopChannelIndex = i + 1;
        loadCurrentKit();
    }

    public void loadCurrentKit() {
        int i = this.loopChannelIndex;
        if (i > 10) {
            loadLoopsFromMemory();
        } else {
            loadPresetFromAssets(i);
        }
    }

    public void loadPresetFromAssets(final int i) {
        // Bump load generation — any in-flight background thread from a prior kit
        // change will see a different token and discard its stale results.
        final int myGeneration = ++this.loadGeneration;
        // Stop currently playing loops on UI thread immediately (zero-latency feedback)
        for (int i2 = 0; i2 < 8; i2++) {
            if (this.loopPlaying[i2]) {
                this.audioEngine.stopPad(i2);
                this.loopPlaying[i2] = false;
            }
            this.loopSamples[i2] = null;
            this.loopUris[i2] = null;
        }
        // Snapshot engine reference — engine can become null in onDestroy
        final AudioEngine engine = this.audioEngine;
        if (engine == null) return;
        // Decode audio on a background thread — MediaCodec decoding on the UI thread
        // adds 100-500 ms of jank before the first tap can play. Moving it off-thread
        // means pads are ready for playback as soon as loading finishes, with zero UI freeze.
        new Thread(() -> {
            final AudioEngine.SampleData[] loaded = new AudioEngine.SampleData[8];
            for (int i2 = 0; i2 < 8; i2++) {
                try {
                    String assetPath = "kit" + i + "/loop_pad_" + (i2 + 1) + ".wav";
                    loaded[i2] = engine.loadWavFromAsset(i2, assetPath);
                } catch (Exception e) {
                    loaded[i2] = null;
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                // Discard if user switched kit/channel while we were loading
                if (myGeneration != this.loadGeneration) return;
                for (int i2 = 0; i2 < 8; i2++) {
                    this.loopSamples[i2] = loaded[i2];
                }
            });
        }).start();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != -1 || data == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri data2 = data.getData();
        if (data2 == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (requestCode == REQ_PICK_LOOP_WAV) {
            try {
                getContentResolver().takePersistableUriPermission(data2, data.getFlags() & 3);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            int padIndex = this.selectedPad;
            this.loopUris[padIndex] = data2;
            this.loopSamples[padIndex] = null;
            this.loopPlaying[padIndex] = false;
            if (this.audioEngine != null) {
                try {
                    AudioEngine.SampleData sampleData = this.audioEngine.loadWavFromUri(padIndex, data2);
                    this.loopSamples[padIndex] = sampleData;
                    if (sampleData != null && sampleData.loaded) {
                        this.preloadLoop(padIndex);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            this.saveLoopsToMemory();
            Toast.makeText(this, "Loop Audio Loaded!", 0).show();
        } else if (requestCode == REQ_SAVE_LOOP_FOLDER) {
            try {
                getContentResolver().takePersistableUriPermission(data2, 3);
                String str = this.pendingSaveLoopName;
                if (str != null && str.length() > 0) {
                    this.currentLoopName = str;
                    this.txtLoopChannel.setText(str);
                    SharedPreferences.Editor edit = this.prefs.edit();
                    edit.putString("loop_name_ch_" + this.loopChannelIndex, this.currentLoopName).apply();
                }
                saveLoopToFolder(data2);
                this.pendingSaveLoopName = null;
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        } else if (requestCode == REQ_LOAD_LOOP_FOLDER) {
            try {
                getContentResolver().takePersistableUriPermission(data2, 1);
                showKitListDialog(data2);
            } catch (Exception e3) {
                e3.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialogInterface, int i) {
        ArrayList arrayList = this.tempKitFolders;
        if (arrayList != null) {
            try {
                loadLoopFromFolder(((DocumentFile) arrayList.get(i)).getUri());
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Load Error: " + e.getMessage(), 0).show();
            }
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < 8; i++) {
            if (this.loopPlaying[i]) {
                this.audioEngine.stopPad(i);
                this.loopPlaying[i] = false;
            }
            this.loopSamples[i] = null;
        }
        if (this.audioEngine != null) {
            this.audioEngine.stop();
            this.audioEngine = null;
        }
        try {
            closeMidiDevice();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        this.isVisible = false;
        for (int i = 0; i < 8; i++) {
            if (this.loopPlaying[i]) {
                this.audioEngine.stopPad(i);
                this.loopPlaying[i] = false;
                this.loopPads[i].setBackgroundResource(R.drawable.pad_black_selector);
            }
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.isVisible = true;
    }

    public void onScaleMinus(View view) {
        int i = this.currentScaleOffset - 1;
        if (i < -12) {
            i = -12;
        }
        this.currentScaleOffset = i;
        updateScaleUI();
    }

    public void onScalePlus(View view) {
        int i = this.currentScaleOffset + 1;
        if (i > 12) {
            i = 12;
        }
        this.currentScaleOffset = i;
        updateScaleUI();
    }

    public void onStopLoopClick(View view) {
        try {
            for (int i = 0; i < 8; i++) {
                if (this.loopPlaying[i]) {
                    this.audioEngine.stopPad(i);
                    this.loopPlaying[i] = false;
                    this.loopPads[i].setBackgroundResource(R.drawable.pad_black_selector);
                }
            }
        } catch (Throwable th) {
        }
    }

    public void scanForMcnFolders(DocumentFile documentFile, ArrayList arrayList, ArrayList arrayList2) {
        DocumentFile[] listFiles;
        String name;
        for (DocumentFile documentFile2 : documentFile.listFiles()) {
            if (documentFile2 != null && (name = documentFile2.getName()) != null && documentFile2.isDirectory()) {
                if (name.toLowerCase().endsWith(".mcn")) {
                    arrayList.add(documentFile2);
                    arrayList2.add(name.replace("_loop.mcn", ""));
                } else {
                    scanForMcnFolders(documentFile2, arrayList, arrayList2);
                }
            }
        }
    }

    public void showKitListDialog(Uri uri) {
        DocumentFile fromTreeUri = DocumentFile.fromTreeUri(this, uri);
        if (fromTreeUri == null || !fromTreeUri.exists() || !fromTreeUri.isDirectory()) {
            Toast.makeText(this, "Invalid Folder", 0).show();
            return;
        }
        String name = fromTreeUri.getName();
        if (name != null && name.toLowerCase().endsWith(".mcn")) {
            try {
                loadLoopFromFolder(uri);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Load Error: " + e.getMessage(), 0).show();
            }
            return;
        }
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        scanForMcnFolders(fromTreeUri, arrayList, arrayList2);
        if (arrayList2.size() == 0) {
            Toast.makeText(this, "No .mcn folders found!", 0).show();
            return;
        }
        this.tempKitFolders = arrayList;
        new AlertDialog.Builder(this).setTitle("Select Loop Folder").setItems((String[]) arrayList2.toArray(new String[0]), this).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    static int access$508(LoopsActivity x0) {
        int i = x0.loopChannelIndex;
        x0.loopChannelIndex = i + 1;
        return i;
    }

    static int access$510(LoopsActivity x0) {
        int i = x0.loopChannelIndex;
        x0.loopChannelIndex = i - 1;
        return i;
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) throws IllegalStateException {
        globalInstance = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loops);
        hideSystemUI();
        setupMidi();
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, 0);
        this.prefs = sharedPreferences;
        this.loopChannelIndex = sharedPreferences.getInt(KEY_LOOP_INDEX, 1);
        this.btnBack = (Button) findViewById(R.id.btnBack);
        this.btnEditLoops = (Button) findViewById(R.id.btnEditLoops);
        this.btnAdvancedLoops = (Button) findViewById(R.id.btnAdvancedLoops);
        this.advancedControlPanel = findViewById(R.id.advancedControlPanel);
        this.txtLoopStatus = (TextView) findViewById(R.id.txtLoopStatus);
        this.txtMidiStatus = (TextView) findViewById(R.id.txtMidiStatus);
        if (this.txtMidiStatus != null) {
            this.txtMidiStatus.setText("MIDI status: disconnected");
        }
        this.btnPrevLoop = (Button) findViewById(R.id.btnPrevLoop);
        this.btnNextLoop = (Button) findViewById(R.id.btnNextLoop);
        this.txtLoopChannel = (TextView) findViewById(R.id.txtLoopChannel);
        this.btnRenameLoop = (Button) findViewById(R.id.btnRenameLoop);
        this.btnSaveLoop = (Button) findViewById(R.id.btnSaveLoop);
        this.btnLoadLoop = (Button) findViewById(R.id.btnLoadLoop);
        this.btnTempoMinus = (Button) findViewById(R.id.btnTempoMinus);
        this.btnTempoPlus = (Button) findViewById(R.id.btnTempoPlus);
        this.seekTempo = (SeekBar) findViewById(R.id.seekTempo);
        this.seekPitch = (SeekBar) findViewById(R.id.seekPitch);
        this.txtTempoVal = (TextView) findViewById(R.id.txtTempoVal);
        this.txtPitchVal = (TextView) findViewById(R.id.txtPitchVal);
        this.editCustomBpm = (EditText) findViewById(R.id.editCustomBpm);
        this.btnSetBpm = (Button) findViewById(R.id.btnSetBpm);
        this.seekMasterVolume = (SeekBar) findViewById(R.id.seekMasterVolume);
        this.txtMasterVolVal = (TextView) findViewById(R.id.txtMasterVolVal);
        this.chkMultiMode = (CheckBox) findViewById(R.id.chkMultiMode);
        this.chkOneShotMode = (CheckBox) findViewById(R.id.chkOneShotMode);
        this.btnTapTempo = (Button) findViewById(R.id.btnTapTempo);
        String string = this.prefs.getString("loop_name_ch_" + this.loopChannelIndex, "LOOP " + this.loopChannelIndex);
        this.currentLoopName = string;
        this.txtLoopChannel.setText(string);
        this.masterVolume = this.prefs.getFloat("loop_master_volume", 1.0f);
        this.reverbLevel = this.prefs.getInt("loop_reverb_level", 0);
        this.isMultiMode = this.prefs.getBoolean("loop_multi_mode", false);
        this.isOneShotMode = this.prefs.getBoolean("loop_one_shot_mode", false);
        SeekBar seekBar = this.seekMasterVolume;
        if (seekBar != null) {
            seekBar.setProgress((int) (this.masterVolume * 100.0f));
        }
        CheckBox checkBox = this.chkMultiMode;
        if (checkBox != null) {
            checkBox.setChecked(this.isMultiMode);
        }
        CheckBox checkBox2 = this.chkOneShotMode;
        if (checkBox2 != null) {
            checkBox2.setChecked(this.isOneShotMode);
        }
        setupReverb();
        setupControls();
        initPads();
        this.audioEngine = new AudioEngine(this);
        this.audioEngine.start();
        loadCurrentKit();
        this.btnBack.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                LoopsActivity.this.finish();
            }
        });
        this.btnEditLoops.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                LoopsActivity.this.editMode = !LoopsActivity.this.editMode;
                LoopsActivity.this.btnEditLoops.setText(LoopsActivity.this.editMode ? "EDIT ON" : "EDIT OFF");
                LoopsActivity.this.btnEditLoops.setBackgroundResource(LoopsActivity.this.editMode ? R.drawable.btn_3d_red : R.drawable.btn_3d_dark);
            }
        });
        Button button = this.btnAdvancedLoops;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.3
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    if (LoopsActivity.this.advancedControlPanel != null) {
                        if (LoopsActivity.this.advancedControlPanel.getVisibility() == 0) {
                            LoopsActivity.this.advancedControlPanel.setVisibility(8);
                            LoopsActivity.this.btnAdvancedLoops.setBackgroundResource(R.drawable.btn_3d_dark);
                            return;
                        }
                        LoopsActivity.this.advancedControlPanel.setVisibility(0);
                        LoopsActivity.this.btnAdvancedLoops.setBackgroundResource(R.drawable.btn_3d_orange);
                    }
                }
            });
        }
        Button button2 = this.btnTapTempo;
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.4
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    LoopsActivity.this.handleTapTempo();
                }
            });
        }
        this.btnPrevLoop.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View v) throws IllegalStateException {
                if (LoopsActivity.this.loopChannelIndex > 1) {
                    LoopsActivity.this.saveLoopsToMemory();
                    LoopsActivity.access$510(LoopsActivity.this);
                    LoopsActivity.this.prefs.edit().putInt(LoopsActivity.KEY_LOOP_INDEX, LoopsActivity.this.loopChannelIndex).apply();
                    LoopsActivity loopsActivity = LoopsActivity.this;
                    loopsActivity.currentLoopName = loopsActivity.prefs.getString("loop_name_ch_" + LoopsActivity.this.loopChannelIndex, "LOOP " + LoopsActivity.this.loopChannelIndex);
                    LoopsActivity.this.txtLoopChannel.setText(LoopsActivity.this.currentLoopName);
                    LoopsActivity.this.loadCurrentKit();
                    return;
                }
                Toast.makeText(LoopsActivity.this, "Already First Loop Channel!", 0).show();
            }
        });
        this.btnNextLoop.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View v) throws IllegalStateException {
                if (LoopsActivity.this.loopChannelIndex < 50) {
                    LoopsActivity.this.saveLoopsToMemory();
                    LoopsActivity.access$508(LoopsActivity.this);
                    LoopsActivity.this.prefs.edit().putInt(LoopsActivity.KEY_LOOP_INDEX, LoopsActivity.this.loopChannelIndex).apply();
                    LoopsActivity loopsActivity = LoopsActivity.this;
                    loopsActivity.currentLoopName = loopsActivity.prefs.getString("loop_name_ch_" + LoopsActivity.this.loopChannelIndex, "LOOP " + LoopsActivity.this.loopChannelIndex);
                    LoopsActivity.this.txtLoopChannel.setText(LoopsActivity.this.currentLoopName);
                    LoopsActivity.this.loadCurrentKit();
                    return;
                }
                Toast.makeText(LoopsActivity.this, "Max Loop Channel Reached!", 0).show();
            }
        });
        Button button3 = this.btnRenameLoop;
        if (button3 != null) {
            button3.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.7
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    LoopsActivity.this.renameLoopDialog();
                }
            });
        }
        Button button4 = this.btnSaveLoop;
        if (button4 != null) {
            button4.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.8
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    LoopsActivity.this.showSaveLoopNameDialog();
                }
            });
        }
        Button button5 = this.btnLoadLoop;
        if (button5 != null) {
            button5.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.9
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
                    intent.addFlags(1);
                    LoopsActivity.this.startActivityForResult(intent, LoopsActivity.REQ_LOAD_LOOP_FOLDER);
                }
            });
        }
        this.btnTempoMinus.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.10
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                int progress = LoopsActivity.this.seekTempo.getProgress();
                if (progress > 0) {
                    LoopsActivity.this.seekTempo.setProgress(progress - 1);
                }
            }
        });
        this.btnTempoPlus.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.11
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                int progress = LoopsActivity.this.seekTempo.getProgress();
                if (progress < LoopsActivity.this.seekTempo.getMax()) {
                    LoopsActivity.this.seekTempo.setProgress(progress + 1);
                }
            }
        });
        Button button6 = this.btnSetBpm;
        if (button6 != null && this.editCustomBpm != null) {
            button6.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.LoopsActivity.12
                @Override // android.view.View.OnClickListener
                public void onClick(View v) throws NumberFormatException {
                    String bpmText = LoopsActivity.this.editCustomBpm.getText().toString();
                    if (!bpmText.isEmpty()) {
                        try {
                            float bpm = Float.parseFloat(bpmText);
                            float speed = bpm / 120.0f;
                            float speed2 = Math.max(0.1f, Math.min(2.0f, speed));
                            if (LoopsActivity.this.seekTempo != null) {
                                LoopsActivity.this.seekTempo.setProgress((int) (100.0f * speed2));
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(LoopsActivity.this, "Invalid BPM", 0).show();
                        }
                    }
                }
            });
        }
    }

    public void handleTapTempo() {
        long now = System.currentTimeMillis();
        long[] jArr = this.tapTimes;
        int i = this.tapIndex;
        jArr[i] = now;
        this.tapIndex = (i + 1) % 4;
        int validTaps = 0;
        long totalDelta = 0;
        for (int i2 = 0; i2 < 3; i2++) {
            int i3 = this.tapIndex;
            int current = (((i3 - 1) - i2) + 4) % 4;
            int previous = (((i3 - 2) - i2) + 4) % 4;
            long[] jArr2 = this.tapTimes;
            long delta = jArr2[current] - jArr2[previous];
            if (delta <= 250 || delta >= 2000) {
                if (delta != 0) {
                    break;
                }
            } else {
                totalDelta += delta;
                validTaps++;
            }
        }
        if (validTaps > 0) {
            long avgDelta = totalDelta / validTaps;
            float bpm = 60000.0f / ((float) avgDelta);
            float speed = bpm / 120.0f;
            float speed2 = Math.max(0.1f, Math.min(2.0f, speed));
            SeekBar seekBar = this.seekTempo;
            if (seekBar != null) {
                seekBar.setProgress((int) (100.0f * speed2));
            }
        }
    }

    private void setupReverb() {
        try {
            PresetReverb presetReverb = this.globalReverb;
            if (presetReverb != null) {
                presetReverb.release();
            }
            this.globalReverb = new PresetReverb(0, 0);
            updateReverbLevel(this.reverbLevel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateReverbLevel(int progress) throws IllegalStateException, UnsupportedOperationException, IllegalArgumentException {
        short preset;
        PresetReverb presetReverb = this.globalReverb;
        if (presetReverb != null) {
            try {
                if (progress == 0) {
                    presetReverb.setEnabled(false);
                    return;
                }
                presetReverb.setEnabled(true);
                if (progress < 20) {
                    preset = 1;
                } else if (progress < 40) {
                    preset = 2;
                } else if (progress < 60) {
                    preset = 3;
                } else {
                    preset = progress < 80 ? (short) 4 : (short) 5;
                }
                this.globalReverb.setPreset(preset);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupControls() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.LoopsActivity.13
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) throws IllegalStateException, UnsupportedOperationException, IllegalArgumentException {
                float value = Math.max(0.1f, progress / 100.0f);
                if (seekBar.getId() == R.id.seekTempo) {
                    LoopsActivity.this.currentSpeed = value;
                    if (LoopsActivity.this.txtTempoVal != null) {
                        LoopsActivity.this.txtTempoVal.setText(String.format("%.1fx", Float.valueOf(LoopsActivity.this.currentSpeed)));
                    }
                    LoopsActivity.this.updateAllActiveLoops();
                } else if (seekBar.getId() == R.id.seekPitch) {
                    LoopsActivity.this.currentPitch = value;
                    if (LoopsActivity.this.txtPitchVal != null) {
                        LoopsActivity.this.txtPitchVal.setText(String.format("%.1fx", Float.valueOf(LoopsActivity.this.currentPitch)));
                    }
                    LoopsActivity.this.updateAllActiveLoops();
                } else if (seekBar.getId() == R.id.seekMasterVolume) {
                    LoopsActivity.this.masterVolume = progress / 100.0f;
                    if (LoopsActivity.this.txtMasterVolVal != null) {
                        LoopsActivity.this.txtMasterVolVal.setText(progress + "%");
                    }
                    LoopsActivity.this.prefs.edit().putFloat("loop_master_volume", LoopsActivity.this.masterVolume).apply();
                    LoopsActivity.this.updateAllActiveLoops();
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        SeekBar seekBar = this.seekTempo;
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(listener);
        }
        SeekBar seekBar2 = this.seekPitch;
        if (seekBar2 != null) {
            seekBar2.setOnSeekBarChangeListener(listener);
        }
        SeekBar seekBar3 = this.seekMasterVolume;
        if (seekBar3 != null) {
            seekBar3.setOnSeekBarChangeListener(listener);
        }
        CompoundButton.OnCheckedChangeListener checkListener = new CompoundButton.OnCheckedChangeListener() { // from class: com.pramod.loopmidi.LoopsActivity.14
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.getId() == R.id.chkMultiMode) {
                    LoopsActivity.this.isMultiMode = isChecked;
                    LoopsActivity.this.prefs.edit().putBoolean("loop_multi_mode", LoopsActivity.this.isMultiMode).apply();
                } else if (buttonView.getId() == R.id.chkOneShotMode) {
                    LoopsActivity.this.isOneShotMode = isChecked;
                    LoopsActivity.this.prefs.edit().putBoolean("loop_one_shot_mode", LoopsActivity.this.isOneShotMode).apply();
                    // When switching to one-shot mode, stop all active loops and clear stale state
                    // to avoid overlapping loop+oneshot playback and stuck-blue pads.
                    if (isChecked) {
                        for (int i = 0; i < 8; i++) {
                            if (LoopsActivity.this.loopPlaying[i]) {
                                LoopsActivity.this.audioEngine.stopPad(i);
                                LoopsActivity.this.loopPlaying[i] = false;
                                LoopsActivity.this.loopPads[i].setBackgroundResource(R.drawable.pad_black_selector);
                            }
                        }
                    }
                }
            }
        };
        CheckBox checkBox = this.chkMultiMode;
        if (checkBox != null) {
            checkBox.setOnCheckedChangeListener(checkListener);
        }
        CheckBox checkBox2 = this.chkOneShotMode;
        if (checkBox2 != null) {
            checkBox2.setOnCheckedChangeListener(checkListener);
        }
    }

    public void updateAllActiveLoops() {
        for (int i = 0; i < 8; i++) {
            if (this.loopPlaying[i] && this.loopSamples[i] != null && this.audioEngine != null) {
                this.audioEngine.stopPad(i);
                this.audioEngine.playSample(i, this.loopSamples[i], this.masterVolume, this.currentSpeed, this.currentPitch, this.isOneShotMode ? 0 : 1, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f);
                this.audioEngine.updateLoopSpeedPitch(i, this.masterVolume, this.currentSpeed, this.currentPitch);
            }
        }
    }

    private void applyPlaybackParams(Object unused) {
    }

    private void initPads() {
        int[] padIds = {R.id.loopPad1, R.id.loopPad2, R.id.loopPad3, R.id.loopPad4, R.id.loopPad5, R.id.loopPad6, R.id.loopPad7, R.id.loopPad8};
        for (int i = 0; i < 8; i++) {
            this.loopPads[i] = (Button) findViewById(padIds[i]);
            this.loopPads[i].setSoundEffectsEnabled(false);
            final int index = i;
            this.loopPads[i].setOnTouchListener(new View.OnTouchListener(this) { // from class: com.pramod.loopmidi.LoopsActivity.15
                final /* synthetic */ LoopsActivity this$0;

                {
                    this.this$0 = this;
                }

                @Override // android.view.View.OnTouchListener
                public boolean onTouch(View v, MotionEvent event) throws IllegalStateException {
                    if (event.getAction() == 0) {
                        v.setPressed(true);
                        this.this$0.handlePadClick(index);
                        return true;
                    } else if (event.getAction() == 1 || event.getAction() == 3) {
                        v.setPressed(false);
                        return true;
                    } else {
                        return false;
                    }
                }
            });
        }
    }

    public void handlePadClick(int index) throws IllegalStateException {
        this.selectedPad = index;
        if (this.editMode) {
            showEditOptions(index);
        } else {
            toggleLoop(index);
        }
    }

    private void showEditOptions(final int index) {
        String[] options = {"Select Loop Audio", "Clear Loop"};
        new AlertDialog.Builder(this).setTitle("EDIT LOOP " + (index + 1)).setItems(options, new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.LoopsActivity.17
            final /* synthetic */ LoopsActivity this$0;

            {
                this.this$0 = this;
            }

            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) throws IllegalStateException {
                if (which == 0) {
                    Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
                    intent.addCategory("android.intent.category.OPENABLE");
                    intent.setType("audio/*");
                    intent.addFlags(1);
                    intent.addFlags(64);
                    this.this$0.startActivityForResult(intent, LoopsActivity.REQ_PICK_LOOP_WAV);
                } else if (which == 1) {
                    this.this$0.clearLoop(index);
                }
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public void clearLoop(int index) throws IllegalStateException {
        if (this.loopPlaying[index]) {
            this.audioEngine.stopPad(index);
            this.loopPlaying[index] = false;
        }
        this.loopSamples[index] = null;
        this.loopUris[index] = null;
        this.loopPads[index].setBackgroundResource(R.drawable.pad_black_selector);
        saveLoopsToMemory();
        Toast.makeText(this, "Loop " + (index + 1) + " Cleared!", 0).show();
    }

    public void renameLoopDialog() {
        final EditText edt = new EditText(this);
        edt.setText(this.currentLoopName);
        new AlertDialog.Builder(this).setTitle("Enter Loop Name").setView(edt).setPositiveButton("OK", new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.LoopsActivity.18
            final /* synthetic */ LoopsActivity this$0;

            {
                this.this$0 = this;
            }

            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface d, int w) {
                this.this$0.currentLoopName = edt.getText().toString().trim();
                if (this.this$0.currentLoopName.length() == 0) {
                    this.this$0.currentLoopName = "LOOP " + this.this$0.loopChannelIndex;
                }
                this.this$0.txtLoopChannel.setText(this.this$0.currentLoopName);
                this.this$0.prefs.edit().putString("loop_name_ch_" + this.this$0.loopChannelIndex, this.this$0.currentLoopName).apply();
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public void showSaveLoopNameDialog() {
        final EditText edt = new EditText(this);
        edt.setHint("Enter Loop Group Name");
        edt.setText(this.currentLoopName);
        new AlertDialog.Builder(this).setTitle("Save Loop Group As").setView(edt).setPositiveButton("NEXT", new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.LoopsActivity.19
            final /* synthetic */ LoopsActivity this$0;

            {
                this.this$0 = this;
            }

            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = edt.getText().toString().trim();
                if (name.length() != 0) {
                    this.this$0.pendingSaveLoopName = this.this$0.sanitizeFileName(name);
                    this.this$0.startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), LoopsActivity.REQ_SAVE_LOOP_FOLDER);
                    return;
                }
                Toast.makeText(this.this$0, "Name required!", 0).show();
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void saveLoopToFolder(Uri folderUri) throws JSONException, IOException {
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, folderUri);
            if (root == null) {
                Toast.makeText(this, "Folder access error!", 0).show();
                return;
            }
            DocumentFile loopFolder = root.findFile(this.currentLoopName + "_loop.mcn");
            if (loopFolder == null) {
                loopFolder = root.createDirectory(this.currentLoopName + "_loop.mcn");
            }
            if (loopFolder == null) {
                Toast.makeText(this, "Cannot create loop folder!", 0).show();
                return;
            }
            for (int i = 0; i < 8; i++) {
                if (this.loopUris[i] != null) {
                    String fileName = "loop_pad_" + (i + 1) + ".wav";
                    DocumentFile old = loopFolder.findFile(fileName);
                    if (old != null) {
                        old.delete();
                    }
                    DocumentFile dest = loopFolder.createFile("audio/wav", fileName);
                    if (dest != null) {
                        FileUtil.copyUriToUri(this, this.loopUris[i], dest.getUri());
                    }
                }
            }
            DocumentFile dataFile = loopFolder.findFile("loop_data.json");
            if (dataFile != null) {
                dataFile.delete();
            }
            DocumentFile dataFile2 = loopFolder.createFile("application/json", "loop_data.json");
            if (dataFile2 != null) {
                try {
                    JSONObject jsonData = new JSONObject();
                    jsonData.put("speed", this.currentSpeed);
                    jsonData.put("pitch", this.currentPitch);
                    jsonData.put("masterVolume", this.masterVolume);
                    jsonData.put("reverbLevel", this.reverbLevel);
                    jsonData.put("isMultiMode", this.isMultiMode);
                    jsonData.put("isOneShotMode", this.isOneShotMode);
                    OutputStream out = getContentResolver().openOutputStream(dataFile2.getUri());
                    if (out != null) {
                        out.write(jsonData.toString().getBytes());
                        out.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Toast.makeText(this, "Loop Saved Successfully!", 0).show();
        } catch (Exception e2) {
            e2.printStackTrace();
            Toast.makeText(this, "Save Error: " + e2.getMessage(), 0).show();
        }
    }

    public void loadLoopFromFolder(Uri folderUri) throws IOException {
        DocumentFile loopFolder = null;
        try {
            loopFolder = DocumentFile.fromTreeUri(this, folderUri);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (loopFolder == null || !loopFolder.isDirectory()) {
            Toast.makeText(this, "Invalid folder!", 0).show();
            return;
        }
        String folderName = loopFolder.getName();
        if (folderName != null && folderName.endsWith("_loop.mcn")) {
            String strReplace = folderName.replace("_loop.mcn", "");
            this.currentLoopName = strReplace;
            this.txtLoopChannel.setText(strReplace);
            this.prefs.edit().putString("loop_name_ch_" + this.loopChannelIndex, this.currentLoopName).apply();
        }
        for (int i = 0; i < 8; i++) {
            this.loopUris[i] = null;
            String fileName = "loop_pad_" + (i + 1) + ".wav";
            DocumentFile wav = loopFolder.findFile(fileName);
            if (wav != null) {
                this.loopUris[i] = wav.getUri();
            }
        }
        DocumentFile dataFile = loopFolder.findFile("loop_data.json");
        if (dataFile != null) {
            try {
                InputStream in2 = getContentResolver().openInputStream(dataFile.getUri());
                if (in2 != null) {
                    try {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in2));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        in2.close();
                        org.json.JSONObject jsonData = new org.json.JSONObject(sb.toString());
                        if (jsonData.has("speed")) {
                            try { this.currentSpeed = (float) jsonData.getDouble("speed"); } catch (Exception ignored) {}
                        }
                        if (jsonData.has("pitch")) {
                            try { this.currentPitch = (float) jsonData.getDouble("pitch"); } catch (Exception ignored) {}
                        }
                        if (jsonData.has("masterVolume")) {
                            try { this.masterVolume = (float) jsonData.getDouble("masterVolume"); } catch (Exception ignored) {}
                        }
                        if (jsonData.has("reverbLevel")) {
                            try { this.reverbLevel = jsonData.getInt("reverbLevel"); } catch (Exception ignored) {}
                        }
                        if (jsonData.has("isMultiMode")) {
                            try { this.isMultiMode = jsonData.getBoolean("isMultiMode"); } catch (Exception ignored) {}
                        }
                        if (jsonData.has("isOneShotMode")) {
                            try { this.isOneShotMode = jsonData.getBoolean("isOneShotMode"); } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Load Error: " + e.getMessage(), 0).show();
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Load Error: " + e.getMessage(), 0).show();
                return;
            }
        }
        SeekBar seekBar = this.seekTempo;
        if (seekBar != null) {
            seekBar.setProgress((int) (this.currentSpeed * 100.0f));
        }
        SeekBar seekBar2 = this.seekPitch;
        if (seekBar2 != null) {
            seekBar2.setProgress((int) (this.currentPitch * 100.0f));
        }
        SeekBar seekBar3 = this.seekMasterVolume;
        if (seekBar3 != null) {
            seekBar3.setProgress((int) (this.masterVolume * 100.0f));
        }
        CheckBox checkBox = this.chkMultiMode;
        if (checkBox != null) {
            checkBox.setChecked(this.isMultiMode);
        }
        CheckBox checkBox2 = this.chkOneShotMode;
        if (checkBox2 != null) {
            checkBox2.setChecked(this.isOneShotMode);
        }
        updateReverbLevel(this.reverbLevel);
        saveLoopsToMemory();
        loadLoopsFromMemory();
        Toast.makeText(this, "Loop Loaded Successfully!", 0).show();
    }

    public void saveLoopsToMemory() {
        SharedPreferences.Editor editor = this.prefs.edit();
        for (int i = 0; i < 8; i++) {
            if (this.loopUris[i] != null) {
                editor.putString("loop_uri_ch_" + this.loopChannelIndex + "_" + i, this.loopUris[i].toString());
            } else {
                editor.remove("loop_uri_ch_" + this.loopChannelIndex + "_" + i);
            }
        }
        editor.apply();
    }

    public void loadLoopsFromMemory() throws IllegalStateException {
        for (int i = 0; i < 8; i++) {
            if (this.loopPlaying[i]) {
                this.audioEngine.stopPad(i);
                this.loopPlaying[i] = false;
            }
            this.loopSamples[i] = null;
            this.loopUris[i] = null;
            Button button = this.loopPads[i];
            if (button != null) {
                button.setBackgroundResource(R.drawable.pad_black_selector);
            }
        }
        TextView textView = this.txtLoopStatus;
        if (textView != null) {
            textView.setText("TAP A PAD TO PLAY/STOP LOOP");
        }
        // Bump generation — stale background threads will discard their results
        final int myGeneration = ++this.loadGeneration;
        // Resolve URIs on the UI thread (cheap string parsing only)
        final Uri[] urisToLoad = new Uri[8];
        for (int i2 = 0; i2 < 8; i2++) {
            String uriStr = this.prefs.getString("loop_uri_ch_" + this.loopChannelIndex + "_" + i2, null);
            if (uriStr != null) {
                this.loopUris[i2] = Uri.parse(uriStr);
                urisToLoad[i2]    = this.loopUris[i2];
            }
        }
        // Snapshot engine reference — safe against onDestroy races
        final AudioEngine engine = this.audioEngine;
        if (engine == null) return;
        // Decode audio on a background thread — avoids blocking the UI thread for
        // potentially hundreds of milliseconds while MediaCodec decodes each file.
        new Thread(() -> {
            final AudioEngine.SampleData[] loaded = new AudioEngine.SampleData[8];
            for (int i2 = 0; i2 < 8; i2++) {
                if (urisToLoad[i2] != null) {
                    try {
                        loaded[i2] = engine.loadWavFromUri(i2, urisToLoad[i2]);
                    } catch (Exception e) {
                        e.printStackTrace();
                        loaded[i2] = null;
                    }
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                // Discard if user switched loop channel while we were decoding
                if (myGeneration != this.loadGeneration) return;
                for (int i2 = 0; i2 < 8; i2++) {
                    this.loopSamples[i2] = loaded[i2];
                    if (loaded[i2] != null && loaded[i2].loaded) {
                        preloadLoop(i2);
                    }
                }
            });
        }).start();
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(5894);
    }

    private void setupMidi() {
        MidiManager midiManager = (MidiManager) getSystemService("midi");
        this.midiManager = midiManager;
        if (midiManager == null) {
            return;
        }
        MidiDeviceInfo[] infos = midiManager.getDevices();
        for (MidiDeviceInfo info : infos) {
            openMidiDevice(info);
        }
        this.midiManager.registerDeviceCallback(new MidiManager.DeviceCallback() { // from class: com.pramod.loopmidi.LoopsActivity.20
            @Override // android.media.midi.MidiManager.DeviceCallback
            public void onDeviceAdded(MidiDeviceInfo device) {
                LoopsActivity.this.openMidiDevice(device);
            }

            @Override // android.media.midi.MidiManager.DeviceCallback
            public void onDeviceRemoved(MidiDeviceInfo device) {
                if (LoopsActivity.this.openedMidiDevice != null && LoopsActivity.this.openedMidiDevice.getInfo().getId() == device.getId()) {
                    try {
                        LoopsActivity.this.closeMidiDevice();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (LoopsActivity.this.txtMidiStatus != null) {
                        LoopsActivity.this.txtMidiStatus.setText("MIDI disconnected");
                    }
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }

    public void openMidiDevice(MidiDeviceInfo info) {
        if (info.getOutputPortCount() > 0) {
            this.midiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() { // from class: com.pramod.loopmidi.LoopsActivity.21
                @Override // android.media.midi.MidiManager.OnDeviceOpenedListener
                public void onDeviceOpened(MidiDevice device) {
                    LoopsActivity.this.openedMidiDevice = device;
                    LoopsActivity.this.midiOutputPort = device.openOutputPort(0);
                    if (LoopsActivity.this.midiOutputPort != null) {
                        if (LoopsActivity.this.txtMidiStatus != null) {
                            LoopsActivity.this.txtMidiStatus.setText("MIDI connected");
                        }
                        LoopsActivity.this.midiOutputPort.connect(new MidiReceiver() { // from class: com.pramod.loopmidi.LoopsActivity.21.1
                            @Override // android.media.midi.MidiReceiver
                            public void onSend(byte[] msg, int offset, int count, long timestamp) {
                                LoopsActivity loopsActivity;
                                int i = offset + count;
                                int i2 = 0;
                                int i3 = offset;
                                while (i3 < i) {
                                    int i4 = msg[i3] & UByte.MAX_VALUE;
                                    if (i4 >= 128) {
                                        i2 = i4;
                                    } else if ((i2 & 240) == 144) {
                                        if (i3 + 1 >= i) {
                                            return;
                                        }
                                        byte b = (byte) i4;
                                        byte b2 = msg[i3 + 1];
                                        if (b2 > 0 && (loopsActivity = LoopsActivity.globalInstance) != null) {
                                            loopsActivity.handleMidiNoteOn(b, b2);
                                        }
                                        i3++;
                                    } else if ((i2 & 240) == 192) {
                                        LoopsActivity loopsActivity2 = LoopsActivity.globalInstance;
                                        if (loopsActivity2 != null) {
                                            loopsActivity2.handleProgramChange(i4);
                                        }
                                    } else if ((i2 & 240) == 128) {
                                        i3++;
                                    }
                                    i3++;
                                }
                            }
                        });
                        ((TextView) LoopsActivity.this.findViewById(R.id.txtMidiStatus)).setText("MIDI connected");
                    }
                }
            }, new Handler(Looper.getMainLooper()));
        }
    }

    public void closeMidiDevice() throws IOException {
        try {
            MidiOutputPort midiOutputPort = this.midiOutputPort;
            if (midiOutputPort != null) {
                midiOutputPort.close();
                this.midiOutputPort = null;
            }
            MidiDevice midiDevice = this.openedMidiDevice;
            if (midiDevice != null) {
                midiDevice.close();
                this.openedMidiDevice = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleMidiNoteOn(byte note, byte velocity) {
        if (this.isVisible) {
            int padIndex = -1;
            switch (note) {
                case 36:
                    padIndex = 4;
                    break;
                case 37:
                    padIndex = 2;
                    break;
                case 38:
                case 40:
                    padIndex = 5;
                    break;
                case 39:
                    padIndex = 3;
                    break;
                case 42:
                case 44:
                    padIndex = 7;
                    break;
                case 45:
                case 47:
                case ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE /* 48 */:
                case 50:
                    padIndex = 1;
                    break;
                case 46:
                    padIndex = 6;
                    break;
                case ConstraintLayout.LayoutParams.Table.LAYOUT_EDITOR_ABSOLUTEX /* 49 */:
                    padIndex = 0;
                    break;
            }
            if (padIndex == -1) {
                padIndex = note % 8;
            }
            final int finalPadIndex = padIndex;
            runOnUiThread(new Runnable(this) { // from class: com.pramod.loopmidi.LoopsActivity.22
                final /* synthetic */ LoopsActivity this$0;

                {
                    this.this$0 = this;
                }

                @Override // java.lang.Runnable
                public void run() throws IllegalStateException {
                    int i = finalPadIndex;
                    if (i >= 0 && i < 8) {
                        this.this$0.loopPads[finalPadIndex].setPressed(true);
                        this.this$0.handlePadClick(finalPadIndex);
                        Handler handler = new Handler(Looper.getMainLooper());
                        final int i2 = finalPadIndex;
                        handler.postDelayed(new Runnable(this) { // from class: com.pramod.loopmidi.LoopsActivity.22.1
                            final /* synthetic */ AnonymousClass22 this$1;

                            {
                                this.this$1 = this;
                            }

                            @Override // java.lang.Runnable
                            public void run() {
                                this.this$1.this$0.loopPads[i2].setPressed(false);
                            }
                        }, 100L);
                    }
                }
            });
        }
    }

    public void preloadLoop(int index) {
        try {
            AudioEngine.SampleData sampleData = this.loopSamples[index];
            if (sampleData != null && sampleData.loaded && this.audioEngine != null) {
                this.audioEngine.preloadSample(sampleData);
            }
        } catch (Exception e) {
        }
    }
}
