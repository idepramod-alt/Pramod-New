package com.pramod.loopmidi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
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
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
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
    private Button btnTempoMinus;
    private Button btnTempoPlus;
    private Button btnResetSpeedPitch;
    private Button btnDrumOctapad;
    private CheckBox chkMultiMode;
    private CheckBox chkOneShotMode;
    private boolean isDrumOctapadMode = false;
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
    private boolean editMode = false;
    private int selectedPad = 0;
    private Uri[] loopUris = new Uri[8];
    private AudioEngine audioEngine;
    private AudioEngine.SampleData[] loopSamples = new AudioEngine.SampleData[8];
    private boolean[] loopPlaying = new boolean[8];

    // ── Master Volume Mode ────────────────────────────────────────────────────
    // true  = slider controls ALL pads simultaneously (original behaviour)
    // false = slider controls only the most-recently tapped pad individually
    private boolean isMasterVolumeMode = true;
    private Button    btnMasterVolMode  = null;
    private android.widget.TextView txtMasterVolLabel = null;
    // Per-pad volumes — active when isMasterVolumeMode == false
    private float[] padVolume = new float[]{1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};

    // ── Per-pad Loop / Drum mode ──────────────────────────────────────────────
    // false = LOOP mode  (continuous loop, tap again to stop)
    // true  = DRUM mode  (one-shot hit on every tap, like a real drum pad)
    // Long-press any pad to toggle its mode.
    private boolean[] padDrumMode = new boolean[8];  // default all LOOP

    // ── Loop Mode / Drum Mode (Roland SPD-SX Pro style) ──────────────────────
    // LOOP MODE (default): pads continuously loop their sample, tap again to stop
    // DRUM MODE: pads play one-shot on every hit, ALL pads can fire simultaneously
    private boolean isGlobalDrumMode = false;
    private Button btnLoopMode = null;
    private Button btnDrumMode = null;
    // Saved pre-drum-mode values so switching back to Loop Mode truly restores them
    private boolean savedMultiMode    = false;
    private boolean savedOneShotMode  = false;

    // ── Recording system ──────────────────────────────────────────────────────
    private Button btnRecordStart   = null;
    private Button btnRecordStop    = null;
    private Button btnPlayRecording = null;
    private Button btnStopPlayRec   = null;
    private TextView txtRecStatus   = null;
    private MediaRecorder mediaRecorder  = null;
    private MediaPlayer   mediaPlayer    = null;
    private boolean isRecording         = false;
    private boolean hasRecording        = false;
    private String  recFilePath         = null;
    // Debounce handler: prevents flooding the native command queue when sliders
    // are dragged (onProgressChanged fires 60+ times/sec). Audio update fires
    // 40ms after the last slider move; UI labels update immediately as before.
    private final Handler speedPitchHandler = new Handler(Looper.getMainLooper());
    private Runnable speedPitchRunnable = null;

    // ── Audio routing ─────────────────────────────────────────────────────────
    // Handles earphone/BT plug & unplug so the Oboe stream restarts on the
    // correct output device without losing the currently playing loops.
    private AudioDeviceCallback audioDeviceCallback = null;
    private BroadcastReceiver   noisyReceiver       = null;
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
        // Per-pad mode: DRUM mode acts like one-shot (global isOneShotMode is an override too)
        boolean isDrumPad = this.isGlobalDrumMode || this.padDrumMode[index] || this.isOneShotMode;
        if (isDrumPad) {
            // DRUM / ONE-SHOT: play once on each tap, no auto-repeat.
            if (this.loopPlaying[index]) {
                this.loopPlaying[index] = false;
                updatePadLabel(index);
            }
            // chokeGroup = index+1 (unique per pad) so retapping the SAME pad cuts off
            // its previous still-playing instance before starting the new one. Without
            // this, every tap grabbed a brand-new drum voice while the earlier instance
            // kept ringing out, so repeated taps piled up overlapping copies of the same
            // sample — the "mix-up"/garbled sound instead of a clean one-shot retrigger.
            this.audioEngine.playSample(index, sampleData, effectiveVolume(index), this.currentSpeed, this.currentPitch, 0, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, index + 1, 0.0f, 0.0f);
            this.txtLoopStatus.setText((this.padDrumMode[index] ? "DRUM" : "ONE-SHOT") + ": PAD " + (index + 1));
            if (!this.isMultiMode) {
                // Always send stopPad() for every other pad, regardless of loopPlaying[i] —
                // one-shot hits never set loopPlaying=true, so gating this on that flag
                // meant a still-ringing one-shot on another pad was NEVER actually choked
                // when a new pad was tapped, letting both sounds play together. stopPad()
                // is a harmless no-op on the native side if that pad has nothing active.
                for (int i = 0; i < 8; i++) {
                    if (i != index) {
                        this.audioEngine.stopPad(i);
                        if (this.loopPlaying[i]) {
                            this.loopPlaying[i] = false;
                            updatePadLabel(i);
                        }
                    }
                }
            }
            return;
        }
        // LOOP mode: loops the sample continuously until pad is tapped again.
        if (this.loopPlaying[index]) {
            this.audioEngine.stopPad(index);
            this.loopPlaying[index] = false;
            this.txtLoopStatus.setText("LOOP " + (index + 1) + " STOPPED");
            updatePadLabel(index);
            return;
        }
        // Start the loop via playLoopSP only. Previously this ALSO called
        // playSample(..., loopMode=1, ...) right before playLoopSP — both routes
        // end up issuing a native "start loop" command for the same voice slot,
        // so the second call immediately tore down and recreated the Sonic
        // stream the first call had just set up, causing an audible restart
        // click at the moment playback began.
        this.audioEngine.playLoopSP(index, effectiveVolume(index), this.currentSpeed, this.currentPitch);
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
                updatePadLabel(i);
            }
        }
    }

    /**
     * Returns the volume to use when triggering pad {@code padIndex}.
     * <ul>
     *   <li>Master mode ON  → global {@link #masterVolume} applies to all pads</li>
     *   <li>Master mode OFF → per-pad {@link #padVolume}[padIndex] applies only
     *       to that pad (volume slider controls the last-tapped pad)</li>
     * </ul>
     */
    private float effectiveVolume(int padIndex) {
        return isMasterVolumeMode ? masterVolume : padVolume[padIndex];
    }

    /**
     * Refresh the visual state of a pad button:
     * <ul>
     *   <li>Playing + loop  → blue glow background</li>
     *   <li>Idle   + drum   → orange border (signals one-shot/drum mode)</li>
     *   <li>Idle   + loop   → dark background (default)</li>
     * </ul>
     * Call whenever {@code loopPlaying[i]} or {@code padDrumMode[i]} changes.
     */
    private void updatePadLabel(int index) {
        if (this.loopPads[index] == null) return;
        if (this.loopPlaying[index]) {
            // Playing state handled by the caller (blue glow already set on play)
        } else if (this.padDrumMode[index]) {
            this.loopPads[index].setBackgroundResource(R.drawable.pad_orange_selector);
        } else {
            this.loopPads[index].setBackgroundResource(R.drawable.pad_black_selector);
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
                // Abort BEFORE touching the native pad buffer if a newer kit switch
                // has already started. Without this check, a slow/stale load thread
                // from a previously-selected kit can call nativeLoadSample() after a
                // newer kit's thread already loaded the correct sound into the same
                // pad slot — silently overwriting it with the wrong kit's audio, so
                // a sound "not even in this kit" plays when the pad is tapped.
                if (myGeneration != this.loadGeneration) return;
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
        teardownAudioRouting();
        // Stop recorder/player cleanly
        stopMicRecording();
        stopPlayback();
        if (this.mediaRecorder != null) {
            try { this.mediaRecorder.release(); } catch (Exception ignored) {}
            this.mediaRecorder = null;
        }
        if (this.mediaPlayer != null) {
            try { this.mediaPlayer.release(); } catch (Exception ignored) {}
            this.mediaPlayer = null;
        }
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
                updatePadLabel(i);    // preserve drum-mode orange, don't force black
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
            // Use stopAll() unconditionally instead of checking loopPlaying[i].
            // In One-Shot mode pads play via playSample() and loopPlaying[i] is
            // never set to true, so the old guard silently skipped every pad and
            // the stop button appeared to do nothing. stopAll() sends a single
            // CMD_STOP_ALL that silences every active voice regardless of mode.
            if (this.audioEngine != null) {
                this.audioEngine.stopAll();
            }
            // Cancel any queued slider-debounce runnable so a pending update
            // can't re-start a voice immediately after we stopped everything.
            if (this.speedPitchRunnable != null) {
                this.speedPitchHandler.removeCallbacks(this.speedPitchRunnable);
                this.speedPitchRunnable = null;
            }
            for (int i = 0; i < 8; i++) {
                this.loopPlaying[i] = false;
                updatePadLabel(i);    // drum-mode pads keep their orange indicator
            }
            if (this.txtLoopStatus != null) {
                this.txtLoopStatus.setText("STOPPED");
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
        this.txtMasterVolVal  = (TextView) findViewById(R.id.txtMasterVolVal);
        this.txtMasterVolLabel = (android.widget.TextView) findViewById(R.id.txtMasterVolLabel);
        this.btnMasterVolMode  = (Button) findViewById(R.id.btnMasterVolMode);
        this.chkMultiMode = (CheckBox) findViewById(R.id.chkMultiMode);
        this.chkOneShotMode = (CheckBox) findViewById(R.id.chkOneShotMode);
        this.btnDrumOctapad = (Button) findViewById(R.id.btnDrumOctapad);
        this.btnResetSpeedPitch = (Button) findViewById(R.id.btnResetSpeedPitch);
        // Loop Mode / Drum Mode toggle buttons
        this.btnLoopMode = (Button) findViewById(R.id.btnLoopMode);
        this.btnDrumMode = (Button) findViewById(R.id.btnDrumMode);
        // Recording panel buttons
        this.btnRecordStart   = (Button)   findViewById(R.id.btnRecordStart);
        this.btnRecordStop    = (Button)   findViewById(R.id.btnRecordStop);
        this.btnPlayRecording = (Button)   findViewById(R.id.btnPlayRecording);
        this.btnStopPlayRec   = (Button)   findViewById(R.id.btnStopPlayRec);
        this.txtRecStatus     = (TextView) findViewById(R.id.txtRecStatus);
        // Restore global drum mode from prefs
        this.isGlobalDrumMode = this.prefs.getBoolean("global_drum_mode", false);
        String string = this.prefs.getString("loop_name_ch_" + this.loopChannelIndex, "LOOP " + this.loopChannelIndex);
        this.currentLoopName = string;
        this.txtLoopChannel.setText(string);
        this.masterVolume = this.prefs.getFloat("loop_master_volume", 1.0f);
        this.reverbLevel  = this.prefs.getInt("loop_reverb_level", 0);
        this.isMultiMode  = this.prefs.getBoolean("loop_multi_mode", false);
        this.isOneShotMode = this.prefs.getBoolean("loop_one_shot_mode", false);
        // Restore per-pad volumes and per-pad drum/loop mode
        for (int i = 0; i < 8; i++) {
            this.padVolume[i]   = this.prefs.getFloat("pad_volume_" + i, 1.0f);
            this.padDrumMode[i] = this.prefs.getBoolean("pad_drum_mode_" + i, false);
        }
        this.isMasterVolumeMode = this.prefs.getBoolean("master_vol_mode", true);
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
        setupAudioRouting();
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
        Button button7 = this.btnResetSpeedPitch;
        if (button7 != null) {
            button7.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    LoopsActivity.this.resetSpeedPitch();
                }
            });
        }
        Button button8 = this.btnDrumOctapad;
        if (button8 != null) {
            button8.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.toggleDrumOctapadMode();
                }
            });
        }
        // ── Loop Mode button ──────────────────────────────────────────────────
        if (this.btnLoopMode != null) {
            this.btnLoopMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.setGlobalDrumMode(false);
                }
            });
        }
        // ── Drum Mode button (Roland SPD-SX Pro style) ────────────────────────
        if (this.btnDrumMode != null) {
            this.btnDrumMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.setGlobalDrumMode(true);
                }
            });
        }
        // ── Recording buttons ─────────────────────────────────────────────────
        if (this.btnRecordStart != null) {
            this.btnRecordStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.startMicRecording();
                }
            });
        }
        if (this.btnRecordStop != null) {
            this.btnRecordStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.stopMicRecording();
                }
            });
        }
        if (this.btnPlayRecording != null) {
            this.btnPlayRecording.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.playRecording();
                }
            });
        }
        if (this.btnStopPlayRec != null) {
            this.btnStopPlayRec.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LoopsActivity.this.stopPlayback();
                }
            });
        }
        // Apply initial mode UI state
        updateModeButtonsUI();
    }

    /** Toggle Drum Octapad mode: one-shot + multi-play both on/off together.
    // ─────────────────────────────────────────────────────────────────────────
    //  Audio routing: handle earphone / BT plug-unplug without losing loops
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Register callbacks so the Oboe stream is restarted whenever the audio
     * output device changes (earphone plug/unplug, BT connect/disconnect).
     *
     * Two listeners are registered:
     *   1. AudioDeviceCallback — fires on the main thread when any audio
     *      device is added or removed. We filter to output devices and
     *      reinit the native stream with fresh AudioManager params so Oboe
     *      opens on the correct device at its native SR / burst size.
     *      Any loops that were playing are re-triggered after the stream
     *      comes back up.
     *
     *   2. ACTION_AUDIO_BECOMING_NOISY receiver — fires when the user
     *      unplugs earphones and sound would otherwise blast from the speaker
     *      unexpectedly. We stop all active loops cleanly in that case.
     */
    private void setupAudioRouting() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        // ── 1. Device-change callback (earphone plug-in / BT connect) ────────
        audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                for (AudioDeviceInfo d : addedDevices) {
                    if (d.isSink()) {          // output device appeared
                        reinitAudioForNewDevice(am);
                        return;
                    }
                }
            }
            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                for (AudioDeviceInfo d : removedDevices) {
                    if (d.isSink()) {          // output device removed
                        // ACTION_AUDIO_BECOMING_NOISY handles the "stop loops"
                        // case for earphone removal; here we just reinit the
                        // stream so it falls back to the next available device.
                        reinitAudioForNewDevice(am);
                        return;
                    }
                }
            }
        };
        am.registerAudioDeviceCallback(audioDeviceCallback, new Handler(Looper.getMainLooper()));

        // ── 2. Becoming-noisy receiver (earphone suddenly unplugged) ─────────
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    // Stop all active loops so they don't blast from speaker
                    final AudioEngine engine = LoopsActivity.this.audioEngine;
                    if (engine == null) return;
                    for (int i = 0; i < 8; i++) {
                        if (LoopsActivity.this.loopPlaying[i]) {
                            engine.stopPad(i);
                            LoopsActivity.this.loopPlaying[i] = false;
                            LoopsActivity.this.updatePadLabel(i); // keep drum-mode orange
                        }
                    }
                }
            }
        };
        registerReceiver(noisyReceiver,
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    /**
     * Reinit the Oboe stream with fresh AudioManager properties for the
     * currently active output device, then re-trigger any loops that were
     * playing before the device change.
     */
    private void reinitAudioForNewDevice(AudioManager am) {
        final AudioEngine engine = this.audioEngine;
        if (engine == null) return;

        // Re-query device-native parameters (they may differ for the new device)
        int nativeSR    = 48000;
        int nativeBurst = 256;
        try {
            String srStr    = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            String burstStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            if (srStr    != null && !srStr.isEmpty())    nativeSR    = Integer.parseInt(srStr);
            if (burstStr != null && !burstStr.isEmpty()) nativeBurst = Integer.parseInt(burstStr);
            if (nativeSR    < 8000  || nativeSR    > 192000) nativeSR    = 48000;
            if (nativeBurst < 32    || nativeBurst > 8192)   nativeBurst = 256;
        } catch (NumberFormatException ignored) {}

        // Snapshot which pads were playing before the stream reinit
        final boolean[] wasPlaying = new boolean[8];
        for (int i = 0; i < 8; i++) wasPlaying[i] = this.loopPlaying[i];

        // Restart the Oboe stream on the new device.
        // Native voices (sample data + active flags) are preserved inside
        // the C++ engine; only the stream itself is recreated.
        engine.reinitStream(nativeSR, nativeBurst);

        // Re-trigger loop voices so they audibly resume on the new device.
        // (The native engine may have kept voice state, but re-issuing
        // playLoopSP ensures the loop is definitely running post-reinit.)
        for (int i = 0; i < 8; i++) {
            if (wasPlaying[i] && this.loopSamples[i] != null && this.loopSamples[i].loaded) {
                engine.playLoopSP(i, effectiveVolume(i), this.currentSpeed, this.currentPitch);
            }
        }
    }

    /** Unregister audio routing callbacks — called from onDestroy(). */
    private void teardownAudioRouting() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && audioDeviceCallback != null) {
                am.unregisterAudioDeviceCallback(audioDeviceCallback);
            }
        } catch (Exception ignored) {}
        try {
            if (noisyReceiver != null) unregisterReceiver(noisyReceiver);
        } catch (Exception ignored) {}
        audioDeviceCallback = null;
        noisyReceiver       = null;
    }

    // ─────────────────────────────────────────────────────────────────────────

     /*  In Drum Octapad mode every pad tap plays the sample once (one-shot)
     *  and all 8 pads can play simultaneously — exactly like a real drum kit.
     *  Speed + pitch sliders apply to each new hit when it is triggered. */
    public void toggleDrumOctapadMode() {
        this.isDrumOctapadMode = !this.isDrumOctapadMode;
        // Drum mode = one-shot ON + multi-play ON
        this.isOneShotMode = this.isDrumOctapadMode;
        this.isMultiMode   = this.isDrumOctapadMode;
        // Sync checkboxes so the UI state matches
        if (this.chkOneShotMode != null) this.chkOneShotMode.setChecked(this.isOneShotMode);
        if (this.chkMultiMode   != null) this.chkMultiMode.setChecked(this.isMultiMode);
        // Visual feedback on the button: orange = active, dark = inactive
        if (this.btnDrumOctapad != null) {
            this.btnDrumOctapad.setBackgroundResource(
                this.isDrumOctapadMode ? R.drawable.btn_3d_orange : R.drawable.btn_3d_dark);
        }
        // Save both prefs
        this.prefs.edit()
            .putBoolean("loop_one_shot_mode", this.isOneShotMode)
            .putBoolean("loop_multi_mode",    this.isMultiMode)
            .apply();
        // Stop all active loops so the new mode takes effect cleanly
        if (this.isDrumOctapadMode && this.audioEngine != null) {
            this.audioEngine.stopAll();
            for (int i = 0; i < 8; i++) {
                this.loopPlaying[i] = false;
                updatePadLabel(i);   // respect per-pad drum/loop mode indicator
            }
        }
        if (this.txtLoopStatus != null) {
            this.txtLoopStatus.setText(this.isDrumOctapadMode ? "DRUM OCTAPAD MODE ON" : "DRUM OCTAPAD MODE OFF");
        }
    }

    public void resetSpeedPitch() {
        this.currentSpeed = 1.0f;
        this.currentPitch = 1.0f;
        this.currentScaleOffset = 0;
        SeekBar seekBar = this.seekTempo;
        if (seekBar != null) {
            seekBar.setProgress(100);
        }
        SeekBar seekBar2 = this.seekPitch;
        if (seekBar2 != null) {
            seekBar2.setProgress(100);
        }
        TextView textView = this.txtTempoVal;
        if (textView != null) {
            textView.setText("1.0x");
        }
        TextView textView2 = this.txtPitchVal;
        if (textView2 != null) {
            textView2.setText("1.0x");
        }
        updateScaleUI();
        updateAllActiveLoops();
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
                // Update UI labels immediately so the display feels instant
                if (seekBar.getId() == R.id.seekTempo) {
                    LoopsActivity.this.currentSpeed = value;
                    if (LoopsActivity.this.txtTempoVal != null) {
                        LoopsActivity.this.txtTempoVal.setText(String.format("%.1fx", Float.valueOf(LoopsActivity.this.currentSpeed)));
                    }
                } else if (seekBar.getId() == R.id.seekPitch) {
                    LoopsActivity.this.currentPitch = value;
                    if (LoopsActivity.this.txtPitchVal != null) {
                        LoopsActivity.this.txtPitchVal.setText(String.format("%.1fx", Float.valueOf(LoopsActivity.this.currentPitch)));
                    }
                } else if (seekBar.getId() == R.id.seekMasterVolume) {
                    float vol = progress / 100.0f;
                    if (LoopsActivity.this.isMasterVolumeMode) {
                        // ALL mode — move master volume, affects every pad
                        LoopsActivity.this.masterVolume = vol;
                        LoopsActivity.this.prefs.edit().putFloat("loop_master_volume", vol).apply();
                    } else {
                        // PAD mode — move only the selected pad's volume
                        int pad = LoopsActivity.this.selectedPad;
                        LoopsActivity.this.padVolume[pad] = vol;
                        LoopsActivity.this.prefs.edit().putFloat("pad_volume_" + pad, vol).apply();
                    }
                    if (LoopsActivity.this.txtMasterVolVal != null) {
                        LoopsActivity.this.txtMasterVolVal.setText(progress + "%");
                    }
                }
                // Debounce: cancel any pending audio update and reschedule.
                // Fires 40ms after the last slider movement — prevents flooding
                // the native command queue when dragging (60+ events/sec).
                if (LoopsActivity.this.speedPitchRunnable != null) {
                    LoopsActivity.this.speedPitchHandler.removeCallbacks(LoopsActivity.this.speedPitchRunnable);
                }
                LoopsActivity.this.speedPitchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        LoopsActivity.this.updateAllActiveLoops();
                        LoopsActivity.this.speedPitchRunnable = null;
                    }
                };
                LoopsActivity.this.speedPitchHandler.postDelayed(LoopsActivity.this.speedPitchRunnable, 40L);
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
                                LoopsActivity.this.updatePadLabel(i); // keep drum-mode orange
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

        // ── Master Volume Mode toggle button ──────────────────────────────────
        // ALL → slider controls every pad at once
        // PAD → slider controls only the last-tapped pad individually
        applyMasterVolModeUI();
        if (this.btnMasterVolMode != null) {
            this.btnMasterVolMode.setOnClickListener(v -> {
                isMasterVolumeMode = !isMasterVolumeMode;
                prefs.edit().putBoolean("master_vol_mode", isMasterVolumeMode).apply();
                applyMasterVolModeUI();
                // Show the selected pad's volume in the slider when switching to PAD mode
                if (!isMasterVolumeMode && seekMasterVolume != null) {
                    seekMasterVolume.setProgress((int)(padVolume[selectedPad] * 100f));
                } else if (seekMasterVolume != null) {
                    seekMasterVolume.setProgress((int)(masterVolume * 100f));
                }
            });
        }
    }

    /**
     * Sync the master-volume mode button and label text to {@link #isMasterVolumeMode}.
     * ALL (blue) = slider moves all pads | PAD (orange) = slider moves selected pad only.
     */
    private void applyMasterVolModeUI() {
        if (btnMasterVolMode != null) {
            btnMasterVolMode.setText(isMasterVolumeMode ? "ALL" : "PAD");
            btnMasterVolMode.setBackgroundResource(
                isMasterVolumeMode ? R.drawable.btn_3d_blue : R.drawable.btn_3d_orange);
        }
        if (txtMasterVolLabel != null) {
            txtMasterVolLabel.setText(isMasterVolumeMode ? "MASTER VOL" : "PAD VOL");
        }
    }

    public void updateAllActiveLoops() {
        // Live-update speed/pitch on already-playing loops WITHOUT stopping or
        // restarting them — stopPad()+playSample() here used to kill the voice
        // and restart it from position 0 on every single slider tick, causing
        // the audible "cut" every time speed/pitch was dragged.
        for (int i = 0; i < 8; i++) {
            if (this.loopPlaying[i] && this.loopSamples[i] != null && this.audioEngine != null) {
                this.audioEngine.updateLoopSpeedPitch(i, effectiveVolume(i), this.currentSpeed, this.currentPitch);
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

            // Touch listener: ACTION_DOWN = play, UP/CANCEL = release press visual
            this.loopPads[i].setOnTouchListener(new View.OnTouchListener(this) {
                final LoopsActivity this$0 = LoopsActivity.this;

                @Override
                public boolean onTouch(View v, MotionEvent event) throws IllegalStateException {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.setPressed(true);
                        this.this$0.handlePadClick(index);
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP
                            || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        v.setPressed(false);
                        return true;
                    }
                    return false;
                }
            });

            // Long-press: toggle this pad between LOOP mode and DRUM mode
            this.loopPads[i].setOnLongClickListener(v -> {
                padDrumMode[index] = !padDrumMode[index];
                // Stop the pad if it was looping — drum mode is one-shot only
                if (padDrumMode[index] && loopPlaying[index]) {
                    if (audioEngine != null) audioEngine.stopPad(index);
                    loopPlaying[index] = false;
                }
                updatePadLabel(index);
                prefs.edit().putBoolean("pad_drum_mode_" + index, padDrumMode[index]).apply();
                String modeStr = padDrumMode[index] ? "🥁 DRUM" : "🔁 LOOP";
                txtLoopStatus.setText("PAD " + (index + 1) + " → " + modeStr + " MODE (long-press to toggle)");
                return true;   // consume the long-press (don't fire the tap handler)
            });

            // Apply initial visual state (orange border for drum mode, dark for loop mode)
            updatePadLabel(index);
        }
    }

    public void handlePadClick(int index) throws IllegalStateException {
        this.selectedPad = index;
        if (this.editMode) {
            showEditOptions(index);
        } else {
            // In PAD volume mode: update the slider to reflect this pad's individual volume
            // so the user immediately sees and can adjust this pad's level on tap.
            if (!isMasterVolumeMode && seekMasterVolume != null) {
                seekMasterVolume.setProgress((int)(padVolume[index] * 100f));
                if (txtMasterVolVal != null)
                    txtMasterVolVal.setText((int)(padVolume[index] * 100f) + "%");
            }
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
        // Cleared pad has no sample — force dark background regardless of mode
        this.padDrumMode[index] = false;
        prefs.edit().putBoolean("pad_drum_mode_" + index, false).apply();
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
        // Try all common audio extensions so any format works (mp3, ogg, flac,
        // aac, m4a, wav, 3gp …). AudioEngine.decodeAudioToPcm handles them all
        // via MediaCodec for compressed formats and a pure-Java WAV decoder.
        final String[] AUDIO_EXTS = {"wav", "mp3", "ogg", "flac", "aac", "m4a", "3gp", "opus", "wma"};
        for (int i = 0; i < 8; i++) {
            this.loopUris[i] = null;
            String base = "loop_pad_" + (i + 1);
            for (String ext : AUDIO_EXTS) {
                DocumentFile audioFile = loopFolder.findFile(base + "." + ext);
                if (audioFile != null && audioFile.exists()) {
                    this.loopUris[i] = audioFile.getUri();
                    break;
                }
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
            // Kit load clears samples → reset drum mode and restore dark background
            this.padDrumMode[i] = false;
            prefs.edit().putBoolean("pad_drum_mode_" + i, false).apply();
            updatePadLabel(i);
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

    // ═════════════════════════════════════════════════════════════════════════
    //  Global Loop Mode / Drum Mode  (Roland SPD-SX Pro style)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Switch between global LOOP MODE and global DRUM MODE.
     *
     * LOOP MODE (isDrum=false):
     *   Each pad loops its sample continuously. Tap again to stop.
     *   This is the factory default — one pad plays at a time unless MULTI-PLAY is on.
     *
     * DRUM MODE (isDrum=true):
     *   Every pad behaves like a Roland SPD-SX Pro drum pad:
     *   - One-shot hit on every tap (no looping)
     *   - All 8 pads can fire simultaneously (independent voices)
     *   - Retapping the same pad cuts off its own previous ring (choke)
     *   - Long-pressing a pad still works to override its individual mode
     */
    public void setGlobalDrumMode(boolean isDrum) {
        if (isDrum == this.isGlobalDrumMode) return;  // already in this mode
        if (isDrum) {
            // Save current Loop Mode options before overwriting them
            this.savedMultiMode   = this.isMultiMode;
            this.savedOneShotMode = this.isOneShotMode;
            // Drum Mode: all pads independent one-shot, no looping
            this.isOneShotMode = false;   // choke handled per-hit, not globally
            this.isMultiMode   = true;    // all pads play simultaneously
        } else {
            // Restore the user's Loop Mode settings from before Drum Mode was enabled
            this.isOneShotMode = this.savedOneShotMode;
            this.isMultiMode   = this.savedMultiMode;
        }
        this.isGlobalDrumMode = isDrum;
        // Keep isDrumOctapadMode in sync so ADV panel reflects state correctly
        this.isDrumOctapadMode = isDrum;
        // Sync checkboxes
        if (this.chkMultiMode   != null) this.chkMultiMode.setChecked(this.isMultiMode);
        if (this.chkOneShotMode != null) this.chkOneShotMode.setChecked(this.isOneShotMode);
        // Stop all active pads so mode change takes effect cleanly
        if (this.audioEngine != null) {
            this.audioEngine.stopAll();
            for (int i = 0; i < 8; i++) {
                this.loopPlaying[i] = false;
                updatePadLabel(i);
            }
        }
        this.prefs.edit()
            .putBoolean("global_drum_mode",   this.isGlobalDrumMode)
            .putBoolean("loop_multi_mode",    this.isMultiMode)
            .putBoolean("loop_one_shot_mode", this.isOneShotMode)
            .apply();
        updateModeButtonsUI();
        if (this.txtLoopStatus != null) {
            this.txtLoopStatus.setText(isDrum
                ? "🥁 DRUM MODE — tap any pad to hit (Roland SPD-SX Pro style)"
                : "🔁 LOOP MODE — tap pad to start/stop loop");
        }
    }

    /** Update button backgrounds to reflect the current mode. */
    private void updateModeButtonsUI() {
        if (this.btnLoopMode != null) {
            this.btnLoopMode.setBackgroundResource(
                this.isGlobalDrumMode ? R.drawable.btn_3d_dark : R.drawable.btn_3d_blue);
        }
        if (this.btnDrumMode != null) {
            this.btnDrumMode.setBackgroundResource(
                this.isGlobalDrumMode ? R.drawable.btn_3d_orange : R.drawable.btn_3d_dark);
        }
        // Keep ADV panel's btnDrumOctapad in sync
        if (this.btnDrumOctapad != null) {
            this.btnDrumOctapad.setBackgroundResource(
                this.isGlobalDrumMode ? R.drawable.btn_3d_orange : R.drawable.btn_3d_dark);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Recording system  (MediaRecorder — mic input)
    // ═════════════════════════════════════════════════════════════════════════

    /** Start recording from the microphone. */
    public void startMicRecording() {
        if (this.isRecording) {
            Toast.makeText(this, "Already recording!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Stop any currently playing recording first
        stopPlayback();
        try {
            // Build output path in app's files dir (no external storage permission needed)
            File recFile = new File(getFilesDir(), "recording.aac");
            this.recFilePath = recFile.getAbsolutePath();

            if (this.mediaRecorder != null) {
                try { this.mediaRecorder.release(); } catch (Exception ignored) {}
            }
            this.mediaRecorder = new MediaRecorder();
            this.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            this.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            this.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            this.mediaRecorder.setAudioSamplingRate(44100);
            this.mediaRecorder.setAudioEncodingBitRate(192000);
            this.mediaRecorder.setOutputFile(this.recFilePath);
            this.mediaRecorder.prepare();
            this.mediaRecorder.start();
            this.isRecording = true;
            this.hasRecording = false;

            // Visual feedback
            if (this.btnRecordStart   != null) this.btnRecordStart.setBackgroundColor(0xFFFF0000);
            if (this.btnRecordStart   != null) this.btnRecordStart.setText("⏺ RECORDING...");
            if (this.txtRecStatus     != null) this.txtRecStatus.setText("🔴 RECORDING — tap ⏹ STOP when done");
            Log.i("LoopsActivity", "Recording started → " + this.recFilePath);
        } catch (Exception e) {
            Log.e("LoopsActivity", "startMicRecording failed", e);
            this.isRecording = false;
            if (this.mediaRecorder != null) {
                try { this.mediaRecorder.release(); } catch (Exception ignored) {}
                this.mediaRecorder = null;
            }
            Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (this.txtRecStatus != null) this.txtRecStatus.setText("❌ Recording failed — check mic permission");
        }
    }

    /** Stop an active recording. */
    public void stopMicRecording() {
        if (!this.isRecording || this.mediaRecorder == null) return;
        try {
            this.mediaRecorder.stop();
            this.mediaRecorder.release();
            this.mediaRecorder = null;
            this.isRecording   = false;
            this.hasRecording  = true;

            if (this.btnRecordStart != null) {
                this.btnRecordStart.setBackgroundColor(0xFFCC0000);
                this.btnRecordStart.setText("🔴 REC");
            }
            if (this.txtRecStatus != null)
                this.txtRecStatus.setText("✅ Recording saved — tap ▶ PLAY to listen");
            Log.i("LoopsActivity", "Recording stopped");
        } catch (Exception e) {
            Log.e("LoopsActivity", "stopMicRecording failed", e);
            this.isRecording = false;
            if (this.mediaRecorder != null) {
                try { this.mediaRecorder.release(); } catch (Exception ignored) {}
                this.mediaRecorder = null;
            }
            if (this.btnRecordStart != null) this.btnRecordStart.setText("🔴 REC");
        }
    }

    /** Play back the last recorded audio. */
    public void playRecording() {
        if (!this.hasRecording || this.recFilePath == null) {
            Toast.makeText(this, "No recording yet — tap 🔴 REC first!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (this.isRecording) {
            Toast.makeText(this, "Stop recording first!", Toast.LENGTH_SHORT).show();
            return;
        }
        stopPlayback();
        try {
            this.mediaPlayer = new MediaPlayer();
            this.mediaPlayer.setDataSource(this.recFilePath);
            this.mediaPlayer.setOnPreparedListener(mp -> mp.start());
            this.mediaPlayer.setOnCompletionListener(mp -> {
                if (this.txtRecStatus != null)
                    this.txtRecStatus.setText("✅ Playback finished — tap ▶ PLAY again or 🔴 REC for new recording");
                if (this.btnPlayRecording != null)
                    this.btnPlayRecording.setText("▶ PLAY");
            });
            this.mediaPlayer.prepareAsync();

            if (this.btnPlayRecording != null) this.btnPlayRecording.setText("▶ PLAYING...");
            if (this.txtRecStatus     != null) this.txtRecStatus.setText("▶ Playing recording...");
        } catch (Exception e) {
            Log.e("LoopsActivity", "playRecording failed", e);
            Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Stop any active playback of the recorded audio. */
    public void stopPlayback() {
        if (this.mediaPlayer != null) {
            try {
                if (this.mediaPlayer.isPlaying()) this.mediaPlayer.stop();
                this.mediaPlayer.release();
            } catch (Exception ignored) {}
            this.mediaPlayer = null;
        }
        if (this.btnPlayRecording != null) this.btnPlayRecording.setText("▶ PLAY");
        if (this.txtRecStatus != null && this.hasRecording)
            this.txtRecStatus.setText("✅ Recording ready — tap ▶ PLAY to listen");
    }
}
