package com.pramod.loopmidi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import kotlin.UByte;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/* loaded from: classes3.dex */
public class MainActivity extends Activity {
    private static final long HIT_BLOCK_MS = 5;
    private static final String KEY_EDIT_MODE = "edit_mode";
    private static final String KEY_KIT_INDEX = "kit_index";
    private static final String KEY_LAST_LIST_FOLDER_URI = "last_list_folder_uri";
    private static final int MAX_KITS = 100;
    private static final int PAD_COUNT = 8;
    private static final String PREF_NAME = "OctapadSettings";
    private static final int REQ_LIST_FOLDER = 2003;
    private static final int REQ_LOAD_FOLDER = 2002;
    private static final int REQ_PICK_SINGLE_WAV = 5001;
    private static final int REQ_SAVE_FOLDER = 2001;
    private static final String TAG = "MainActivity";
    private View advControlBar;
    private AudioEngine.SampleData assistSoundId;
    private Uri assistSoundUri;
    private AudioEngine audioEngine;
    private Button btnEditMode;
    private Button btnSignOut;
    private TextView txtSignedInAs;
    // ── Admin deactivation real-time listener ──
    private com.google.firebase.database.ValueEventListener deactivateListener;
    private com.google.firebase.database.DatabaseReference  deactivateRef;
    private boolean isForceLogoutInProgress = false;
    private Button btnEq;
    private Button btnLoadKit;
    private Button btnLoops;
    private Button btnNextKit;
    private Button btnPrevKit;
    private Button btnRenameKit;
    private Button btnSaveKit;
    private CheckBox chkDelay;
    private View fxControlBar;
    private boolean isVisible;
    // Velocity Sensitivity: when ON, MIDI velocity (0-127) scales the hit volume
    private boolean velocitySensitiveMode = false;
    private Button btnVelocity = null;
    // ── MIDI Key Mapping System ───────────────────────────────────────────────
    private static final int[] MIDI_NOTE_MAP_DEFAULT = {49, 45, 37, 39, 36, 38, 46, 42};
    private boolean         midiKeyMappingEnabled = false;
    private int[]           midiNoteMap           = MIDI_NOTE_MAP_DEFAULT.clone();
    private volatile boolean midiLearnMode        = false;
    private volatile int     midiLearnTargetPad   = -1;
    private Button           btnMidiMap           = null;
    private MidiManager midiManager;
    private MidiOutputPort midiOutputPort;
    private MidiDevice openedMidiDevice;
    private SharedPreferences prefs;
    private SeekBar seekChokeGroup;
    private SeekBar seekDelayLevel;
    private SeekBar seekDelayTime;
    private SeekBar seekEqHigh;
    private SeekBar seekEqLow;
    private SeekBar seekEqMid;
    private SeekBar seekPitch;
    private SeekBar seekVolume;
    private TextView txtKitName;
    private TextView txtMidiStatus;
    private TextView txtSelectedPad;
    private ArrayList<MidiOutputPort> midiOutputPorts = new ArrayList<>();
    private Button[] pads = new Button[8];
    private Uri[] selectedWavUris = new Uri[8];
    private int[] selectedRawResIds = new int[8];
    private float[] padVolume = new float[8];
    private float[] padPitch = new float[8];
    private boolean[] padDelayOn = new boolean[8];
    private float[] padDelayTime = new float[8];
    private float[] padDelayLevel = new float[8];
    private float[] padEqHigh = new float[8];
    private float[] padEqMid = new float[8];
    private float[] padEqLow = new float[8];
    private int[] padChokeGroup = new int[8];
    private int selectedPad = 0;
    private boolean editMode = false;
    private int kitIndex = 1;
    private String currentKitName = "KIT 1";
    private String pendingSaveKitName = null;
    private int copySourcePad = -1;
    private int swapSourcePad = -1;
    private AudioEngine.SampleData[] samples = new AudioEngine.SampleData[8];
    private int[] activePointerId = new int[8];
    private int currentPresetKit = 0;
    private final String[] presetKitNames = new String[25];
    private final int[][] presetKits = (int[][]) Array.newInstance(Integer.TYPE, 25, 8);
    private long[] lastHitTime = new long[8];

    // ── Kit hold-repeat (Roland SPD style) ────────────────────────────────────
    private final Handler kitRepeatHandler = new Handler(Looper.getMainLooper());
    private Runnable kitRepeatRunnable;

    // ── Audio-routing callbacks (earphone / BT plug-unplug) ──────────────────
    private AudioDeviceCallback audioDeviceCallback = null;
    private BroadcastReceiver   noisyReceiver       = null;

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.isVisible = true;

        if (this.audioEngine == null) {
            // Engine was stopped in onStop() while LoopsActivity was on screen.
            // Recreate the engine and reload the current kit so drum pads work.
            AudioEngine eng = new AudioEngine(this);
            this.audioEngine = eng;
            eng.start();
            loadKitFromMemory(this.kitIndex);
        } else {
            // Normal resume (screen lock, permission dialog, etc.).
            // Reinit Oboe stream to clear any buffer drift/underrun from background.
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int nativeSR = 48000, nativeBurst = 256;
                try {
                    String srStr    = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                    String burstStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                    if (srStr    != null && !srStr.isEmpty())    nativeSR    = Integer.parseInt(srStr);
                    if (burstStr != null && !burstStr.isEmpty()) nativeBurst = Integer.parseInt(burstStr);
                    if (nativeSR < 8000 || nativeSR > 192000)   nativeSR    = 48000;
                    if (nativeBurst < 32 || nativeBurst > 8192) nativeBurst = 256;
                } catch (NumberFormatException ignored) {}
                this.audioEngine.reinitStream(nativeSR, nativeBurst);
            }
        }
    }

    public void onStopLoopClick(View view) {
        try {
            LoopsActivity loopsActivity = LoopsActivity.globalInstance;
            if (loopsActivity != null && loopsActivity.audioEngine != null) {
                // Use stopAll() — same as LoopsActivity's own Stop button.
                // This silences every voice regardless of loopPlaying state.
                loopsActivity.audioEngine.stopAll();
                for (int i = 0; i < 8; i++) {
                    loopsActivity.loopPlaying[i] = false;
                }
                // UI (pad colours) will refresh when LoopsActivity next resumes.
            }
        } catch (Throwable th) {
        }
    }

    static int access$1208(MainActivity x0) {
        int i = x0.kitIndex;
        x0.kitIndex = i + 1;
        return i;
    }

    static int access$1210(MainActivity x0) {
        int i = x0.kitIndex;
        x0.kitIndex = i - 1;
        return i;
    }

    private void initPresets() {
        String[] strArr = this.presetKitNames;
        char c = 0;
        strArr[0] = "Intro Patch";
        char c2 = 1;
        strArr[1] = "Dadra Kaharwa";
        strArr[2] = "Duff Patch";
        strArr[3] = "Kaharwa Dadra Manjira";
        strArr[4] = "Deepchandi Patch";
        strArr[5] = "Bhanda Huk Patch";
        strArr[6] = "Disco Patch";
        strArr[7] = "Dholak Manjira Patch";
        int i = 8;
        strArr[8] = "Dhumal Patch";
        strArr[9] = "Gaura Gauri Patch";
        strArr[10] = "Tiger Dhumal Patch";
        strArr[11] = "Groomer Patch";
        strArr[12] = "Dandiya Patch";
        strArr[13] = "CG Patch";
        strArr[14] = "Jasgeet Manjira Patch";
        strArr[15] = "Jasgeet Jhanj Patch";
        strArr[16] = "CG Sambalpuri";
        strArr[17] = "Panthi Patch";
        strArr[18] = "Nagpuri Patch";
        strArr[19] = "Percussion Patch";
        strArr[20] = "Aana N Gori Ab";
        strArr[21] = "Chham Chham Baje Patch";
        strArr[22] = "CG Slow Karma Patch";
        strArr[23] = "CG Karma Patch";
        strArr[24] = "Drum Set Western Patch";
        int i2 = 0;
        while (i2 < 25) {
            String suffix = i2 == 0 ? "" : String.valueOf(i2 + 1);
            int[][] iArr = this.presetKits;
            int[] iArr2 = new int[i];
            iArr2[c] = getResources().getIdentifier("crash" + suffix, "raw", getPackageName());
            iArr2[c2] = getResources().getIdentifier("tom" + suffix, "raw", getPackageName());
            iArr2[2] = getResources().getIdentifier("rim" + suffix, "raw", getPackageName());
            iArr2[3] = getResources().getIdentifier("clap" + suffix, "raw", getPackageName());
            iArr2[4] = getResources().getIdentifier("kick" + suffix, "raw", getPackageName());
            iArr2[5] = getResources().getIdentifier("snare" + suffix, "raw", getPackageName());
            iArr2[6] = getResources().getIdentifier("ohat" + suffix, "raw", getPackageName());
            iArr2[7] = getResources().getIdentifier("chat" + suffix, "raw", getPackageName());
            iArr[i2] = iArr2;
            i2++;
            i = 8;
            c = 0;
            c2 = 1;
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // Android 11+ (API 30): new WindowInsetsController API
            // setDecorFitsSystemWindows(false) → layout draws behind nav/status bars
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE:
                // swipe se temporarily dikhega, phir auto-hide ho jayega
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Android 6–10 (API 23–29): legacy flags
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
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
        this.midiManager.registerDeviceCallback(new MidiManager.DeviceCallback() { // from class: com.pramod.loopmidi.MainActivity.1
            @Override // android.media.midi.MidiManager.DeviceCallback
            public void onDeviceAdded(MidiDeviceInfo device) {
                MainActivity.this.openMidiDevice(device);
            }

            @Override // android.media.midi.MidiManager.DeviceCallback
            public void onDeviceRemoved(MidiDeviceInfo device) {
                if (MainActivity.this.openedMidiDevice != null && MainActivity.this.openedMidiDevice.getInfo().getId() == device.getId()) {
                    try {
                        MainActivity.this.closeMidiDevice();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ((TextView) MainActivity.this.findViewById(R.id.txtMidiStatus)).setText("MIDI disconnected");
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }

    public void openMidiDevice(MidiDeviceInfo info) {
        if (info.getOutputPortCount() > 0) {
            this.midiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() {
                @Override
                public void onDeviceOpened(MidiDevice device) {
                    if (device == null) return;
                    MainActivity.this.openedMidiDevice = device;
                    int portCount = device.getInfo().getOutputPortCount();
                    // Open ALL output ports — supports multi-port MIDI devices
                    for (int portIndex = 0; portIndex < portCount; portIndex++) {
                        MidiOutputPort port = device.openOutputPort(portIndex);
                        if (port == null) continue;
                        if (MainActivity.this.midiOutputPort == null) {
                            MainActivity.this.midiOutputPort = port;
                        }
                        MainActivity.this.midiOutputPorts.add(port);
                        if (MainActivity.this.txtMidiStatus != null) {
                            MainActivity.this.txtMidiStatus.setText("MIDI connected");
                        }
                        port.connect(new MidiReceiver() {
                            @Override
                            public void onSend(byte[] msg, int offset, int count, long timestamp) {
                                // ── Zero-latency MIDI parser ─────────────────────────────
                                // Runs on dedicated MIDI thread. Audio fires immediately via
                                // playPadSoundImmediate(); UI updates posted to UI thread after.
                                int end    = offset + count;
                                int status = 0;
                                int i      = offset;
                                while (i < end) {
                                    int val = msg[i] & 0xFF;
                                    if (val >= 0x80) {
                                        // Status byte — update running status and advance
                                        status = val;
                                        i++;
                                        continue;
                                    }
                                    int type = status & 0xF0;
                                    if (type == 0x90) {
                                        // Note-On (0x9n channel message)
                                        if (i + 1 >= end) return;
                                        byte note     = (byte) val;
                                        int  velocity = msg[i + 1] & 0xFF;
                                        if (velocity > 0) {
                                            MainActivity.this.handleMidiNoteOn(note, (byte) velocity);
                                        } else {
                                            // velocity == 0 is a Note-Off in disguise
                                            MainActivity.this.handleMidiNoteOff(note);
                                        }
                                        i += 2;
                                    } else if (type == 0x80) {
                                        // Note-Off (0x8n channel message)
                                        if (i + 1 >= end) return;
                                        byte note = (byte) val;
                                        MainActivity.this.handleMidiNoteOff(note);
                                        i += 2;
                                    } else {
                                        // Program Change, Pitch Bend, CC, etc. — skip data byte
                                        // (old code had `continue` here which skipped i++ → infinite loop!)
                                        i++;
                                    }
                                }
                            }
                        });
                    }
                }
            }, new Handler(Looper.getMainLooper()));
        }
    }

    public void closeMidiDevice() throws IOException {
        try {
            Iterator<MidiOutputPort> it = this.midiOutputPorts.iterator();
            while (it.hasNext()) {
                MidiOutputPort port = it.next();
                if (port != null) {
                    port.close();
                }
            }
            this.midiOutputPorts.clear();
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
        // ── MIDI Learn: capture incoming note for the pad being learned ────────
        if (midiLearnMode && midiLearnTargetPad >= 0) {
            final int learnPad  = midiLearnTargetPad;
            final int learnNote = note & 0xFF;
            midiLearnMode      = false;
            midiLearnTargetPad = -1;
            midiNoteMap[learnPad] = learnNote;
            saveMidiNoteMap();
            runOnUiThread(() -> {
                Toast.makeText(this,
                    "PAD " + (learnPad + 1) + " → Note " + learnNote + " mapped! ✅",
                    Toast.LENGTH_SHORT).show();
                updateMidiMapButton();
            });
        }

        if (this.isVisible) {
            int padIndex = -1;

            if (midiKeyMappingEnabled) {
                // ── Custom mapping: scan midiNoteMap[] for a match ────────────
                int noteVal = note & 0xFF;
                for (int i = 0; i < 8; i++) {
                    if (midiNoteMap[i] == noteVal) { padIndex = i; break; }
                }
                if (padIndex == -1) padIndex = noteVal % 8;
            } else {
                // ── Original hardcoded mapping (unchanged / always safe) ──────
                switch (note) {
                    case 36: padIndex = 4; break;
                    case 37: padIndex = 2; break;
                    case 38: case 40: padIndex = 5; break;
                    case 39: padIndex = 3; break;
                    case 42: case 44: padIndex = 7; break;
                    case 45:
                    case 47:
                    case 48:
                    case 50: padIndex = 1; break;
                    case 46: padIndex = 6; break;
                    case 49: padIndex = 0; break;
                }
                if (padIndex == -1) padIndex = (note & 0xFF) % 8;
            }
            final int finalPadIndex = padIndex;
            // ── Velocity scale: 30% min (soft) → 100% (hard) musical curve ──
            final float velScale = velocitySensitiveMode
                    ? Math.min(1.4f, 0.2f + 1.2f * ((velocity & 0xFF) / 127.0f))
                    : 1.0f;
            playPadSoundImmediate(finalPadIndex, velScale);
            runOnUiThread(new Runnable() { // from class: com.pramod.loopmidi.MainActivity.3


                @Override // java.lang.Runnable
                public void run() {
                    try {
                        MainActivity.this.pads[finalPadIndex].setPressed(true);
                    } catch (Exception e) {
                    }
                    Handler handler = new Handler(Looper.getMainLooper());
                    final int i = finalPadIndex;
                    handler.postDelayed(new Runnable() { // from class: com.pramod.loopmidi.MainActivity.3.1


                        @Override // java.lang.Runnable
                        public void run() {
                            try {
                                MainActivity.this.pads[i].setPressed(false);
                            } catch (Exception e2) {
                            }
                        }
                    }, 100L);
                }
            });
        }
    }

    /**
     * Handle MIDI Note-Off.
     *
     * Intentionally a no-op: pads here are one-shot hits that must ring out
     * fully once triggered, exactly like a finger tap. A MIDI pad controller
     * sends Note-Off almost immediately after Note-On (as soon as the
     * physical pad is released) — that is normal and NOT a signal to cut the
     * sample, but this previously called audioEngine.stopPad() unconditionally
     * on every Note-Off, which chopped off drum-roll hits and any pad with a
     * longer sample as soon as the controller released. Only happened over
     * MIDI since touch input has no separate "release" event — same root
     * cause and fix as LoopsActivity.handleMidiNoteOff().
     */
    public void handleMidiNoteOff(byte note) {
        // No-op — see javadoc above.
    }

    private void playPadSoundImmediate(int index) {
        playPadSoundImmediate(index, 1.0f);
    }

    /**
     * Fires pad audio immediately on calling thread (MIDI or UI).
     * @param velocityScale 0.3–1.0 when velocity-sensitive, always 1.0 when OFF.
     */
    private void playPadSoundImmediate(int index, float velocityScale) {
        try {
            AudioEngine.SampleData sampleData = this.samples[index];
            if (sampleData != null && sampleData.loaded) {
                float vol = this.padVolume[index] * velocityScale;
                this.audioEngine.playSample(index, sampleData, vol, this.padPitch[index], 0, this.padDelayOn[index], this.padDelayTime[index], this.padDelayLevel[index], this.padEqLow[index], this.padEqMid[index], this.padEqHigh[index], this.padChokeGroup[index], 0.0f, 0.0f);
            }
        } catch (Exception e) {
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MIDI Key Mapping helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void loadMidiNoteMap() {
        midiKeyMappingEnabled = prefs.getBoolean("midi_key_mapping_on", false);
        for (int i = 0; i < 8; i++) {
            midiNoteMap[i] = prefs.getInt("midi_note_map_" + i, MIDI_NOTE_MAP_DEFAULT[i]);
        }
    }

    private void saveMidiNoteMap() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean("midi_key_mapping_on", midiKeyMappingEnabled);
        for (int i = 0; i < 8; i++) ed.putInt("midi_note_map_" + i, midiNoteMap[i]);
        ed.apply();
    }

    private void updateMidiMapButton() {
        if (btnMidiMap == null) return;
        midiLearnMode = false;
        if (midiKeyMappingEnabled) {
            btnMidiMap.setText("🎹MAP\nON");
            btnMidiMap.setBackgroundResource(R.drawable.btn_3d_orange);
        } else {
            btnMidiMap.setText("🎹MAP\nOFF");
            btnMidiMap.setBackgroundResource(R.drawable.btn_3d_dark);
        }
    }

    private void showMidiKeyMappingDialog() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 8);
        root.setBackgroundColor(0xFF1a1a2e);

        // ON/OFF toggle
        final Button btnToggle = new Button(this);
        btnToggle.setText(midiKeyMappingEnabled ? "✅ CUSTOM MAPPING: ON  (tap to turn OFF)" : "❌ CUSTOM MAPPING: OFF  (tap to turn ON)");
        btnToggle.setBackgroundColor(midiKeyMappingEnabled ? 0xFF006600 : 0xFF333333);
        btnToggle.setTextColor(0xFFFFFFFF);
        btnToggle.setTextSize(12f);
        android.widget.LinearLayout.LayoutParams toggleLP = new android.widget.LinearLayout.LayoutParams(-1, -2);
        toggleLP.setMargins(0, 0, 0, 16);
        btnToggle.setLayoutParams(toggleLP);
        root.addView(btnToggle);

        android.widget.TextView tvInfo = new android.widget.TextView(this);
        tvInfo.setTextColor(0xFF888888);
        tvInfo.setTextSize(11f);
        tvInfo.setText("Har pad ke liye MIDI note number set karo (0–127).\nLEARN: MIDI controller se koi button dabao — auto-assign hoga.");
        android.widget.LinearLayout.LayoutParams infoLP = new android.widget.LinearLayout.LayoutParams(-1, -2);
        infoLP.setMargins(0, 0, 0, 12);
        tvInfo.setLayoutParams(infoLP);
        root.addView(tvInfo);

        final android.widget.EditText[] noteEdits = new android.widget.EditText[8];
        final Button[] learnBtns = new Button[8];
        for (int i = 0; i < 8; i++) {
            final int padIdx = i;
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            android.widget.LinearLayout.LayoutParams rowLP = new android.widget.LinearLayout.LayoutParams(-1, -2);
            rowLP.setMargins(0, 4, 0, 4);
            row.setLayoutParams(rowLP);

            android.widget.TextView lbl = new android.widget.TextView(this);
            lbl.setText("PAD " + (i + 1) + " →");
            lbl.setTextColor(0xFFCCCCCC);
            lbl.setTextSize(12f);
            lbl.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-2, -2));

            android.widget.EditText et = new android.widget.EditText(this);
            et.setText(String.valueOf(midiNoteMap[i]));
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setTextColor(0xFFFFFFFF);
            et.setBackgroundColor(0xFF222244);
            et.setTextSize(13f);
            et.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams etLP = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            etLP.setMargins(8, 0, 8, 0);
            et.setLayoutParams(etLP);
            noteEdits[i] = et;

            Button btnLearn = new Button(this);
            btnLearn.setText("🎹 LEARN");
            btnLearn.setBackgroundColor(0xFF003399);
            btnLearn.setTextColor(0xFFFFFFFF);
            btnLearn.setTextSize(10f);
            btnLearn.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-2, -2));
            learnBtns[i] = btnLearn;
            btnLearn.setOnClickListener(vv -> {
                for (Button b : learnBtns) if (b != null) b.setBackgroundColor(0xFF003399);
                midiLearnTargetPad = padIdx;
                midiLearnMode      = true;
                btnLearn.setBackgroundColor(0xFFCC8800);
                btnLearn.setText("⏳ WAIT...");
                Toast.makeText(this, "PAD " + (padIdx + 1) + ": MIDI controller se koi note dabao...", Toast.LENGTH_SHORT).show();
            });

            row.addView(lbl);
            row.addView(et);
            row.addView(btnLearn);
            root.addView(row);
        }

        android.widget.LinearLayout bottomRow = new android.widget.LinearLayout(this);
        bottomRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams bottomLP = new android.widget.LinearLayout.LayoutParams(-1, -2);
        bottomLP.setMargins(0, 16, 0, 0);
        bottomRow.setLayoutParams(bottomLP);

        Button btnApply = new Button(this);
        btnApply.setText("💾 APPLY");
        btnApply.setBackgroundColor(0xFF006600);
        btnApply.setTextColor(0xFFFFFFFF);
        btnApply.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, -2, 1f));

        Button btnReset = new Button(this);
        btnReset.setText("↩ RESET");
        btnReset.setBackgroundColor(0xFF550000);
        btnReset.setTextColor(0xFFFFFFFF);
        android.widget.LinearLayout.LayoutParams resetLP = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        resetLP.setMargins(8, 0, 0, 0);
        btnReset.setLayoutParams(resetLP);

        bottomRow.addView(btnApply);
        bottomRow.addView(btnReset);
        root.addView(bottomRow);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
            .setTitle("🎹 MIDI Key Mapping")
            .setView(sv)
            .setNegativeButton("CLOSE", null)
            .create();

        btnToggle.setOnClickListener(vv -> {
            midiKeyMappingEnabled = !midiKeyMappingEnabled;
            btnToggle.setText(midiKeyMappingEnabled ? "✅ CUSTOM MAPPING: ON  (tap to turn OFF)" : "❌ CUSTOM MAPPING: OFF  (tap to turn ON)");
            btnToggle.setBackgroundColor(midiKeyMappingEnabled ? 0xFF006600 : 0xFF333333);
            saveMidiNoteMap();
            updateMidiMapButton();
        });

        btnApply.setOnClickListener(vv -> {
            for (int i = 0; i < 8; i++) {
                try {
                    int val = Integer.parseInt(noteEdits[i].getText().toString().trim());
                    midiNoteMap[i] = Math.max(0, Math.min(127, val));
                    noteEdits[i].setText(String.valueOf(midiNoteMap[i]));
                } catch (NumberFormatException ignored) {}
            }
            saveMidiNoteMap();
            Toast.makeText(this, "✅ Mapping save ho gaya!", Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(vv -> new android.app.AlertDialog.Builder(this)
            .setTitle("Reset to Default?")
            .setMessage("Sab pads ke notes wapas default par aa jayenge.")
            .setPositiveButton("RESET", (d, w) -> {
                System.arraycopy(MIDI_NOTE_MAP_DEFAULT, 0, midiNoteMap, 0, 8);
                for (int i = 0; i < 8; i++) noteEdits[i].setText(String.valueOf(midiNoteMap[i]));
                saveMidiNoteMap();
                Toast.makeText(this, "↩ Default mapping restore ho gaya!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show());

        dlg.show();
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int)(screenW * 0.95f), android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Updates the Velocity button label + color to match velocitySensitiveMode. */
    private void updateVelocityButton() {
        if (btnVelocity == null) return;
        if (velocitySensitiveMode) {
            btnVelocity.setText("🎚️VEL\nON");
            btnVelocity.setBackgroundResource(R.drawable.btn_3d_orange);
        } else {
            btnVelocity.setText("🎚️VEL\nOFF");
            btnVelocity.setBackgroundResource(R.drawable.btn_3d_dark);
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        hideSystemUI();
        Toast.makeText(this, "Mobile Octapad Pramod Sahu", 0).show();
        initPresets();
        setupMidi();
        getWindow().getDecorView().setSoundEffectsEnabled(false);
        this.prefs = getSharedPreferences(PREF_NAME, 0);
        this.txtKitName = (TextView) findViewById(R.id.txtKitName);
        this.txtSelectedPad = (TextView) findViewById(R.id.txtSelectedPad);
        this.txtMidiStatus = (TextView) findViewById(R.id.txtMidiStatus);
        this.txtMidiStatus.setText("MIDI status: disconnected");
        this.btnEditMode = (Button) findViewById(R.id.btnEditMode);
        this.btnSaveKit = (Button) findViewById(R.id.btnSaveKit);
        this.btnLoadKit = (Button) findViewById(R.id.btnLoadKit);
        this.btnRenameKit = (Button) findViewById(R.id.btnRenameKit);
        this.btnPrevKit = (Button) findViewById(R.id.btnPrevKit);
        this.btnNextKit = (Button) findViewById(R.id.btnNextKit);
        this.btnEq = (Button) findViewById(R.id.btnEq);
        // Velocity Sensitivity toggle button
        this.btnVelocity = (Button) findViewById(R.id.btnVelocity);
        // MIDI Key Mapping button
        this.btnMidiMap = (Button) findViewById(R.id.btnMidiMap);
        Button button = (Button) findViewById(R.id.btnLoops);
        this.btnLoops = button;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.4
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, LoopsActivity.class);
                    // REORDER_TO_FRONT: if a LoopsActivity is already alive in the
                    // back stack (background playback mode), bring it to front instead
                    // of creating a second instance on top of it.
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    MainActivity.this.startActivity(intent);
                }
            });
        }
        // Cloud account row (Google Sign-In / Firebase) — same login system as LoopsActivity
        this.btnSignOut    = (Button)   findViewById(R.id.btnSignOut);
        this.txtSignedInAs = (TextView) findViewById(R.id.txtSignedInAs);
        com.google.firebase.auth.FirebaseUser _signedInUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (this.txtSignedInAs != null) {
            this.txtSignedInAs.setText(
                    _signedInUser != null && _signedInUser.getEmail() != null
                            ? "Signed in: " + _signedInUser.getEmail() : "");
        }
        if (this.btnSignOut != null) {
            this.btnSignOut.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CloudSync.pushCurrentUserSettings(MainActivity.this);
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                            new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail().build();
                    com.google.android.gms.auth.api.signin.GoogleSignIn
                            .getClient(MainActivity.this, gso).signOut();
                    Intent logoutIntent = new Intent(MainActivity.this, LoginActivity.class);
                    logoutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(logoutIntent);
                    finish();
                }
            });
        }
        // ── Real-time deactivation listener ──────────────────────────────────
        // Agar admin "Deactivate" kare to user turant logout ho jaye — koi wait nahi
        com.google.firebase.auth.FirebaseUser _sessionUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (_sessionUser != null) {
            final String _uid    = _sessionUser.getUid();
            final String _DB_URL = "https://pramod-octapad-loop-default-rtdb.asia-southeast1.firebasedatabase.app";
            deactivateRef = com.google.firebase.database.FirebaseDatabase
                    .getInstance(_DB_URL)
                    .getReference("authorizedUsers")
                    .child(_uid);
            deactivateListener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (!snapshot.exists() && !isForceLogoutInProgress) {
                        isForceLogoutInProgress = true;
                        // Admin ne deactivate kar diya — immediately force-logout
                        runOnUiThread(() -> {
                            getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                                    .edit().putBoolean("licensed_ok", false).apply();
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions _gso =
                                    new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail().build();
                            com.google.android.gms.auth.api.signin.GoogleSignIn
                                    .getClient(MainActivity.this, _gso).signOut();
                            Intent _logoutIntent = new Intent(MainActivity.this, LoginActivity.class);
                            _logoutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(_logoutIntent);
                            finish();
                        });
                    }
                }
                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    // Network issue — ignore, will retry on next event
                }
            };
            deactivateRef.addValueEventListener(deactivateListener);
        }
        // ─────────────────────────────────────────────────────────────────────

        this.seekVolume = (SeekBar) findViewById(R.id.seekVolume);
        this.seekPitch = (SeekBar) findViewById(R.id.seekPitch);
        this.fxControlBar = findViewById(R.id.fxControlBar);
        this.advControlBar = findViewById(R.id.advControlBar);

        // ── Drums APK: LOOPS/STOP hide, Sign Out FX/ADV me ──────────────────
        if (BuildConfig.FLAVOR.equals("drums")) {
            // 1. LOOPS aur STOP buttons hata do (LoopsActivity se koi matlab nahi)
            if (this.btnLoops != null) this.btnLoops.setVisibility(View.GONE);
            View btnStopLoop = findViewById(R.id.btnStopLoop);
            if (btnStopLoop != null) btnStopLoop.setVisibility(View.GONE);
            // 3. FX/ADV panel ke andar Sign Out button dikhao + wire karo
            Button btnDrumsSignOut = findViewById(R.id.btnDrumsSignOut);
            if (btnDrumsSignOut != null) {
                btnDrumsSignOut.setVisibility(View.VISIBLE);
                btnDrumsSignOut.setOnClickListener(v -> {
                    CloudSync.pushCurrentUserSettings(MainActivity.this);
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions _gso3 =
                            new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail().build();
                    com.google.android.gms.auth.api.signin.GoogleSignIn
                            .getClient(MainActivity.this, _gso3).signOut();
                    Intent _li = new Intent(MainActivity.this, LoginActivity.class);
                    _li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(_li);
                    finish();
                });
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        this.chkDelay = (CheckBox) findViewById(R.id.chkDelay);
        this.seekDelayTime = (SeekBar) findViewById(R.id.seekDelayTime);
        this.seekDelayLevel = (SeekBar) findViewById(R.id.seekDelayLevel);
        this.seekEqHigh = (SeekBar) findViewById(R.id.seekEqHigh);
        this.seekEqMid = (SeekBar) findViewById(R.id.seekEqMid);
        this.seekEqLow = (SeekBar) findViewById(R.id.seekEqLow);
        this.seekChokeGroup = (SeekBar) findViewById(R.id.seekChokeGroup);
        AudioEngine audioEngine = new AudioEngine(this);
        this.audioEngine = audioEngine;
        audioEngine.start();
        setupAudioRouting();   // earphone / BT plug-unplug handling
        // Audio focus pehle se lo — pehli pad hit pe OS ko audio path switch
        // nahi karna padta, isliye pehli hit ka delay khatam hota hai.
        try {
            AudioManager _am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (_am != null) _am.requestAudioFocus(null,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) {}
        initPads();
        initSeekBars();
        // Restore velocity sensitivity mode from prefs
        this.velocitySensitiveMode = this.prefs.getBoolean("velocity_sensitive_mode", false);
        updateVelocityButton();
        // Restore MIDI key mapping
        loadMidiNoteMap();
        updateMidiMapButton();
        if (this.btnVelocity != null) {
            this.btnVelocity.setOnClickListener(v -> {
                velocitySensitiveMode = !velocitySensitiveMode;
                prefs.edit().putBoolean("velocity_sensitive_mode", velocitySensitiveMode).apply();
                updateVelocityButton();
            });
        }
        // ── MIDI Key Mapping button ────────────────────────────────────────────
        if (this.btnMidiMap != null) {
            this.btnMidiMap.setOnClickListener(v -> showMidiKeyMappingDialog());
        }
        this.editMode = this.prefs.getBoolean(KEY_EDIT_MODE, false);
        int i = this.prefs.getInt(KEY_KIT_INDEX, 1);
        this.kitIndex = i;
        if (i < 1) {
            this.kitIndex = 1;
        }
        loadKitFromMemory(this.kitIndex);
        updateEditButtonUI();
        this.btnEditMode.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.editMode = !MainActivity.this.editMode;
                if (!MainActivity.this.editMode) {
                    MainActivity.this.copySourcePad = -1;
                    MainActivity.this.swapSourcePad = -1;
                }
                MainActivity.this.updateEditButtonUI();
                MainActivity.this.prefs.edit().putBoolean(MainActivity.KEY_EDIT_MODE, MainActivity.this.editMode).apply();
                MainActivity mainActivity = MainActivity.this;
                mainActivity.saveKitToMemory(mainActivity.kitIndex);
            }
        });
        this.btnRenameKit.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.renameKitDialog();
            }
        });
        // ── Hold-repeat touch listeners (Roland SPD style) ────────────────────
        setupKitHoldButton(this.btnPrevKit, -1);
        setupKitHoldButton(this.btnNextKit, +1);
        // ── Kit Jump: tap kit name → number keyboard → jump instantly ─────────
        this.txtKitName.setOnClickListener(v -> showKitJumpDialog());
        this.btnLoadKit.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.9
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
                intent.addFlags(1);
                intent.addFlags(2);
                intent.addFlags(64);
                MainActivity.this.startActivityForResult(intent, MainActivity.REQ_LOAD_FOLDER);
            }
        });
        this.btnSaveKit.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.10
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showSaveKitNameDialog();
            }
        });
        Button button2 = this.btnEq;
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.11
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    if (MainActivity.this.fxControlBar != null && MainActivity.this.advControlBar != null) {
                        if (MainActivity.this.fxControlBar.getVisibility() == 0) {
                            MainActivity.this.fxControlBar.setVisibility(8);
                            MainActivity.this.advControlBar.setVisibility(8);
                            MainActivity.this.btnEq.setBackgroundResource(R.drawable.btn_3d_dark);
                            return;
                        }
                        MainActivity.this.fxControlBar.setVisibility(0);
                        MainActivity.this.advControlBar.setVisibility(0);
                        MainActivity.this.btnEq.setBackgroundResource(R.drawable.btn_3d_orange);
                    }
                }
            });
        }
    }

    private void initPads() {
        int[] padIds = {R.id.pad1, R.id.pad2, R.id.pad3, R.id.pad4, R.id.pad5, R.id.pad6, R.id.pad7, R.id.pad8};
        for (int i = 0; i < 8; i++) {
            this.pads[i] = (Button) findViewById(padIds[i]);
            this.padVolume[i] = 0.8f;
            this.padPitch[i] = 1.0f;
            this.padDelayOn[i] = false;
            this.padDelayTime[i] = 150.0f;
            this.padDelayLevel[i] = 0.5f;
            this.padEqHigh[i] = 0.0f;
            this.padEqMid[i] = 0.0f;
            this.padEqLow[i] = 0.0f;
            this.activePointerId[i] = -1;
            this.lastHitTime[i] = 0;
            this.pads[i].setSoundEffectsEnabled(false);
            this.pads[i].setHapticFeedbackEnabled(false);
            this.pads[i].setClickable(true);
            this.pads[i].setLongClickable(false);
            this.pads[i].setFocusable(false);
            this.pads[i].setFocusableInTouchMode(false);
            this.pads[i].setOnClickListener(null);
            this.pads[i].setOnTouchListener(new PadTouch(i));
        }
    }

    private void initSeekBars() {
        this.seekVolume.setMax(100);
        this.seekPitch.setMax(100);
        this.seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.12
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.padVolume[MainActivity.this.selectedPad] = progress / 100.0f;
                MainActivity mainActivity = MainActivity.this;
                mainActivity.saveKitToMemory(mainActivity.kitIndex);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.13
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MainActivity.this.padPitch[MainActivity.this.selectedPad] = (progress / 100.0f) + 0.5f;
                MainActivity mainActivity = MainActivity.this;
                mainActivity.saveKitToMemory(mainActivity.kitIndex);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.chkDelay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.pramod.loopmidi.MainActivity.14
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.this.padDelayOn[MainActivity.this.selectedPad] = isChecked;
                MainActivity mainActivity = MainActivity.this;
                mainActivity.saveKitToMemory(mainActivity.kitIndex);
            }
        });
        this.seekDelayTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.15
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MainActivity.this.padDelayTime[MainActivity.this.selectedPad] = progress;
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.seekDelayLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.16
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MainActivity.this.padDelayLevel[MainActivity.this.selectedPad] = progress / 100.0f;
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.seekEqHigh.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.17
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MainActivity.this.padEqHigh[MainActivity.this.selectedPad] = (progress - 100) * 0.15f;
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.seekEqMid.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.18
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MainActivity.this.padEqMid[MainActivity.this.selectedPad] = (progress - 100) * 0.15f;
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.seekEqLow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.19
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MainActivity.this.padEqLow[MainActivity.this.selectedPad] = (progress - 100) * 0.15f;
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        this.seekChokeGroup.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.pramod.loopmidi.MainActivity.20
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MainActivity.this.padChokeGroup[MainActivity.this.selectedPad] = progress;
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
    }

    public void updateEditButtonUI() {
        this.btnEditMode.setText(this.editMode ? "EDIT ON" : "EDIT OFF");
        this.btnEditMode.setBackgroundResource(this.editMode ? R.drawable.btn_3d_red : R.drawable.btn_3d_dark);
    }

    public void playPadSound(int index) {
        AudioEngine.SampleData sampleData = this.samples[index];
        if (sampleData == null) {
            Toast.makeText(this, "No WAV Selected!", 0).show();
        } else {
            this.audioEngine.playSample(index, sampleData, this.padVolume[index], this.padPitch[index], 0, this.padDelayOn[index], this.padDelayTime[index], this.padDelayLevel[index], this.padEqLow[index], this.padEqMid[index], this.padEqHigh[index], this.padChokeGroup[index], 0.0f, 0.0f);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PadTouch implements View.OnTouchListener {
        int index;

        PadTouch(int i) {
            this.index = i;
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);
            if (action != 0 && action != 5) {
                if (action != 1 && action != 6 && action != 3) {
                    return false;
                }
                if (MainActivity.this.activePointerId[this.index] == pointerId) {
                    MainActivity.this.activePointerId[this.index] = -1;
                    v.setPressed(false);
                }
                return true;
            } else if (MainActivity.this.activePointerId[this.index] != -1) {
                return true;
            } else {
                long now = System.currentTimeMillis();
                if (now - MainActivity.this.lastHitTime[this.index] < MainActivity.HIT_BLOCK_MS) {
                    return true;
                }
                MainActivity.this.lastHitTime[this.index] = now;
                MainActivity.this.activePointerId[this.index] = pointerId;
                // ── Audio BEFORE visual — fire sound with zero setPressed overhead ──
                if (!MainActivity.this.editMode) {
                    MainActivity.this.playPadSoundImmediate(this.index);
                }
                v.setPressed(true);
                MainActivity.this.selectedPad = this.index;
                if (!MainActivity.this.editMode || MainActivity.this.copySourcePad == -1 || MainActivity.this.copySourcePad == this.index) {
                    if (!MainActivity.this.editMode || MainActivity.this.swapSourcePad == -1 || MainActivity.this.swapSourcePad == this.index) {
                        if (MainActivity.this.editMode) {
                            MainActivity.this.showEditPadOptions(this.index);
                        }
                        MainActivity.this.txtSelectedPad.setText("Selected: PAD " + (this.index + 1));
                        MainActivity.this.seekVolume.setProgress((int) (MainActivity.this.padVolume[this.index] * 100.0f));
                        MainActivity.this.seekPitch.setProgress((int) ((MainActivity.this.padPitch[this.index] - 0.5f) * 100.0f));
                        MainActivity.this.chkDelay.setChecked(MainActivity.this.padDelayOn[this.index]);
                        MainActivity.this.seekDelayTime.setProgress((int) MainActivity.this.padDelayTime[this.index]);
                        MainActivity.this.seekDelayLevel.setProgress((int) (MainActivity.this.padDelayLevel[this.index] * 100.0f));
                        MainActivity.this.seekEqHigh.setProgress(((int) (MainActivity.this.padEqHigh[this.index] / 0.15f)) + 100);
                        MainActivity.this.seekEqMid.setProgress(((int) (MainActivity.this.padEqMid[this.index] / 0.15f)) + 100);
                        MainActivity.this.seekEqLow.setProgress(((int) (MainActivity.this.padEqLow[this.index] / 0.15f)) + 100);
                        MainActivity.this.seekChokeGroup.setProgress(MainActivity.this.padChokeGroup[this.index]);
                        return true;
                    }
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.swapPadSound(mainActivity.swapSourcePad, this.index);
                    MainActivity.this.swapSourcePad = -1;
                    MainActivity mainActivity2 = MainActivity.this;
                    mainActivity2.saveKitToMemory(mainActivity2.kitIndex);
                    return true;
                }
                MainActivity mainActivity3 = MainActivity.this;
                mainActivity3.copyPadSound(mainActivity3.copySourcePad, this.index);
                MainActivity.this.copySourcePad = -1;
                MainActivity mainActivity4 = MainActivity.this;
                mainActivity4.saveKitToMemory(mainActivity4.kitIndex);
                return true;
            }
        }
    }

    public void showEditPadOptions(final int padIndex) {
        String copyText = this.copySourcePad == -1 ? "Pad Sound Copy (Select Source)" : "Pad Sound Copy (Paste Mode ON)";
        String swapText = this.swapSourcePad == -1 ? "Pad Sound Exchange (Select First Pad)" : "Pad Sound Exchange (Swap Mode ON)";
        String[] options = {"Pad Select Sound", copyText, swapText, "Clear Pad Sound"};
        new AlertDialog.Builder(this).setTitle("PAD " + (padIndex + 1) + " - EDIT OPTIONS").setItems(options, new DialogInterface.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.21


            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                if (which != 0) {
                    if (which == 1) {
                        MainActivity.this.copySourcePad = padIndex;
                        MainActivity.this.swapSourcePad = -1;
                        Toast.makeText(MainActivity.this, "Copy Mode ON: Now tap target PAD to paste", 0).show();
                        return;
                    } else if (which == 2) {
                        MainActivity.this.swapSourcePad = padIndex;
                        MainActivity.this.copySourcePad = -1;
                        Toast.makeText(MainActivity.this, "Exchange Mode ON: Now tap second PAD to swap", 0).show();
                        return;
                    } else if (which == 3) {
                        MainActivity.this.selectedWavUris[padIndex] = null;
                        MainActivity.this.selectedRawResIds[padIndex] = 0;
                        MainActivity.this.samples[padIndex] = null;
                        MainActivity.this.padVolume[padIndex] = 0.8f;
                        MainActivity.this.padPitch[padIndex] = 1.0f;
                        MainActivity.this.padDelayOn[padIndex] = false;
                        MainActivity.this.padDelayTime[padIndex] = 150.0f;
                        MainActivity.this.padDelayLevel[padIndex] = 0.5f;
                        MainActivity.this.padEqHigh[padIndex] = 0.0f;
                        MainActivity.this.padEqMid[padIndex] = 0.0f;
                        MainActivity.this.padEqLow[padIndex] = 0.0f;
                        MainActivity.this.padChokeGroup[padIndex] = 0;
                        MainActivity mainActivity = MainActivity.this;
                        mainActivity.saveKitToMemory(mainActivity.kitIndex);
                        Toast.makeText(MainActivity.this, "PAD " + (padIndex + 1) + " Cleared!", 0).show();
                        return;
                    } else {
                        return;
                    }
                }
                Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
                intent.addCategory("android.intent.category.OPENABLE");
                intent.setType("audio/*");
                intent.addFlags(1);
                intent.addFlags(64);
                MainActivity.this.startActivityForResult(intent, MainActivity.REQ_PICK_SINGLE_WAV);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public void copyPadSound(int fromPad, int toPad) {
        if (fromPad == toPad) {
            return;
        }
        Uri[] uriArr = this.selectedWavUris;
        Uri uri = uriArr[fromPad];
        uriArr[toPad] = uri;
        int[] iArr = this.selectedRawResIds;
        iArr[toPad] = iArr[fromPad];
        float[] fArr = this.padVolume;
        fArr[toPad] = fArr[fromPad];
        float[] fArr2 = this.padPitch;
        fArr2[toPad] = fArr2[fromPad];
        boolean[] zArr = this.padDelayOn;
        zArr[toPad] = zArr[fromPad];
        float[] fArr3 = this.padDelayTime;
        fArr3[toPad] = fArr3[fromPad];
        float[] fArr4 = this.padDelayLevel;
        fArr4[toPad] = fArr4[fromPad];
        float[] fArr5 = this.padEqHigh;
        fArr5[toPad] = fArr5[fromPad];
        float[] fArr6 = this.padEqMid;
        fArr6[toPad] = fArr6[fromPad];
        float[] fArr7 = this.padEqLow;
        fArr7[toPad] = fArr7[fromPad];
        int[] iArr2 = this.padChokeGroup;
        iArr2[toPad] = iArr2[fromPad];
        if (uri != null) {
            try {
                this.samples[toPad] = this.audioEngine.loadWavFromUri(toPad, uri);
            } catch (IOException e) {
                Toast.makeText(this, "Error copying sound: " + e.getMessage(), 0).show();
                this.samples[toPad] = null;
                saveKitToMemory(this.kitIndex);
                Toast.makeText(this, "Copied PAD " + (fromPad + 1) + " -> PAD " + (toPad + 1), 0).show();
            }
        } else {
            try {
                int i = iArr[toPad];
                try {
                    if (i == 0) {
                        this.samples[toPad] = null;
                    } else {
                        this.samples[toPad] = this.audioEngine.loadRawSound(toPad, i);
                    }
                } catch (IOException e2) {
                    Toast.makeText(this, "Error copying sound: " + e2.getMessage(), 0).show();
                    this.samples[toPad] = null;
                    saveKitToMemory(this.kitIndex);
                    Toast.makeText(this, "Copied PAD " + (fromPad + 1) + " -> PAD " + (toPad + 1), 0).show();
                }
            } catch (Exception e3) {

            }
        }
        saveKitToMemory(this.kitIndex);
        Toast.makeText(this, "Copied PAD " + (fromPad + 1) + " -> PAD " + (toPad + 1), 0).show();
    }

    public void swapPadSound(int padA, int padB) {
        Uri uri = null;
        if (padA == padB) {
            return;
        }
        Uri[] uriArr = this.selectedWavUris;
        Uri tempUri = uriArr[padA];
        uriArr[padA] = uriArr[padB];
        uriArr[padB] = tempUri;
        int[] iArr = this.selectedRawResIds;
        int tempRaw = iArr[padA];
        iArr[padA] = iArr[padB];
        iArr[padB] = tempRaw;
        float[] fArr = this.padVolume;
        float tempVol = fArr[padA];
        fArr[padA] = fArr[padB];
        fArr[padB] = tempVol;
        float[] fArr2 = this.padPitch;
        float tempPitch = fArr2[padA];
        fArr2[padA] = fArr2[padB];
        fArr2[padB] = tempPitch;
        boolean[] zArr = this.padDelayOn;
        boolean tempDlyOn = zArr[padA];
        zArr[padA] = zArr[padB];
        zArr[padB] = tempDlyOn;
        float[] fArr3 = this.padDelayTime;
        float tempDlyT = fArr3[padA];
        fArr3[padA] = fArr3[padB];
        fArr3[padB] = tempDlyT;
        float[] fArr4 = this.padDelayLevel;
        float tempDlyL = fArr4[padA];
        fArr4[padA] = fArr4[padB];
        fArr4[padB] = tempDlyL;
        float[] fArr42 = this.padEqHigh;
        float tempEqH = fArr42[padA];
        fArr42[padA] = fArr42[padB];
        fArr42[padB] = tempEqH;
        float[] fArr5 = this.padEqMid;
        float tempEqM = fArr5[padA];
        fArr5[padA] = fArr5[padB];
        fArr5[padB] = tempEqM;
        float[] fArr6 = this.padEqLow;
        float tempEqL = fArr6[padA];
        fArr6[padA] = fArr6[padB];
        fArr6[padB] = tempEqL;
        int[] fArr7 = this.padChokeGroup;
        int tempChoke = fArr7[padA];
        fArr7[padA] = fArr7[padB];
        fArr7[padB] = tempChoke;
        try {
            uri = uriArr[padA];
        } catch (Exception e) {
        }
        try {
            if (uri != null) {
                this.samples[padA] = this.audioEngine.loadWavFromUri(padA, uri);
            } else {
                int i = iArr[padA];
                if (i != 0) {
                    this.samples[padA] = this.audioEngine.loadRawSound(padA, i);
                } else {
                    this.samples[padA] = null;
                }
            }
            Uri uri2 = this.selectedWavUris[padB];
            if (uri2 != null) {
                this.samples[padB] = this.audioEngine.loadWavFromUri(padB, uri2);
            } else {
                int i2 = this.selectedRawResIds[padB];
                if (i2 != 0) {
                    this.samples[padB] = this.audioEngine.loadRawSound(padB, i2);
                } else {
                    this.samples[padB] = null;
                }
            }
        } catch (IOException e2) {

            Toast.makeText(this, "Error swapping sounds: " + e2.getMessage(), 0).show();
            this.samples[padA] = null;
            this.samples[padB] = null;
            saveKitToMemory(this.kitIndex);
            Toast.makeText(this, "Swapped PAD " + (padA + 1) + " <-> PAD " + (padB + 1), 0).show();
        }
        saveKitToMemory(this.kitIndex);
        Toast.makeText(this, "Swapped PAD " + (padA + 1) + " <-> PAD " + (padB + 1), 0).show();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri uri;
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != -1 || data == null || (uri = data.getData()) == null) {
            return;
        }
        // onActivityResult is called BEFORE onResume(). If the file-picker caused
        // onStop() to fire (which sets audioEngine = null), we must recreate the
        // engine here before any audio operation. When onResume() then runs it will
        // see audioEngine != null and call reinitStream() only — the samples loaded
        // below (loadKitFromFolder / loadWavFromUri) remain intact in the engine.
        if (this.audioEngine == null) {
            AudioEngine eng = new AudioEngine(this);
            this.audioEngine = eng;
            eng.start();
        }
        try {
            if (requestCode == REQ_PICK_SINGLE_WAV) {
                int takeFlags = data.getFlags() & 3;
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
                Uri[] uriArr = this.selectedWavUris;
                int i = this.selectedPad;
                uriArr[i] = uri;
                this.samples[i] = this.audioEngine.loadWavFromUri(i, uri);
                AudioEngine.SampleData sampleData = this.samples[this.selectedPad];
                if (sampleData != null) {
                    this.audioEngine.preloadSample(sampleData);
                }
                saveKitToMemory(this.kitIndex);
                Toast.makeText(this, "Sound Loaded & Saved!", 0).show();
            } else if (requestCode == REQ_LOAD_FOLDER) {
                getContentResolver().takePersistableUriPermission(uri, 1);
                loadKitFromFolder(uri);
                saveKitToMemory(this.kitIndex);
            } else if (requestCode == REQ_SAVE_FOLDER) {
                getContentResolver().takePersistableUriPermission(uri, 3);
                String str = this.pendingSaveKitName;
                if (str != null && str.length() > 0) {
                    String str2 = this.pendingSaveKitName;
                    this.currentKitName = str2;
                    this.txtKitName.setText(str2);
                }
                saveKitToFolder(uri);
                this.pendingSaveKitName = null;
            } else if (requestCode == REQ_LIST_FOLDER) {
                getContentResolver().takePersistableUriPermission(uri, 3);
                this.prefs.edit().putString(KEY_LAST_LIST_FOLDER_URI, uri.toString()).apply();
                showKitListDialog(uri);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Permission Error: " + e.getMessage(), 0).show();
        }
    }

    public void showSaveKitNameDialog() {
        final EditText edt = new EditText(this);
        edt.setHint("Enter Kit Name");
        edt.setText(this.currentKitName);
        new AlertDialog.Builder(this).setTitle("Save Kit As").setView(edt).setPositiveButton("NEXT", new DialogInterface.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.22


            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = edt.getText().toString().trim();
                if (name.length() != 0) {
                    MainActivity.this.pendingSaveKitName = MainActivity.this.sanitizeFileName(name);
                    MainActivity.this.startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), MainActivity.REQ_SAVE_FOLDER);
                    return;
                }
                Toast.makeText(MainActivity.this, "Kit name required!", 0).show();
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public void renameKitDialog() {
        final EditText edt = new EditText(this);
        edt.setText(this.currentKitName);
        new AlertDialog.Builder(this).setTitle("Enter Kit Name").setView(edt).setPositiveButton("OK", new DialogInterface.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.23


            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface d, int w) {
                MainActivity.this.currentKitName = edt.getText().toString().trim();
                if (MainActivity.this.currentKitName.length() == 0) {
                    MainActivity.this.currentKitName = "KIT " + MainActivity.this.kitIndex;
                }
                MainActivity.this.txtKitName.setText(MainActivity.this.currentKitName);
                MainActivity mainActivity = MainActivity.this;
                Log.i(MainActivity.TAG, "renameKitDialog: saving kit name='" + MainActivity.this.currentKitName + "' for kitNo=" + mainActivity.kitIndex);
                mainActivity.saveKitToMemory(mainActivity.kitIndex);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public String sanitizeFileName(String name) {
        return name.replace("/", "_").replace("\\", "_").replace(":", "_").replace("*", "_").replace("?", "_").replace("\"", "_").replace("<", "_").replace(">", "_").replace("|", "_");
    }

    public void saveKitToMemory(int kitNo) {
        Log.i(TAG, "saveKitToMemory: saving kitNo=" + kitNo + " name='" + this.currentKitName + "'");
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString("kit_name_" + kitNo, this.currentKitName);
        for (int i = 0; i < 8; i++) {
            editor.putFloat("kit_" + kitNo + "_vol_" + i, this.padVolume[i]);
            editor.putFloat("kit_" + kitNo + "_pitch_" + i, this.padPitch[i]);
            editor.putBoolean("kit_" + kitNo + "_dlyon_" + i, this.padDelayOn[i]);
            editor.putFloat("kit_" + kitNo + "_dlyt_" + i, this.padDelayTime[i]);
            editor.putFloat("kit_" + kitNo + "_dlyl_" + i, this.padDelayLevel[i]);
            editor.putFloat("kit_" + kitNo + "_eqh_" + i, this.padEqHigh[i]);
            editor.putFloat("kit_" + kitNo + "_eqm_" + i, this.padEqMid[i]);
            editor.putFloat("kit_" + kitNo + "_eql_" + i, this.padEqLow[i]);
            editor.putInt("kit_" + kitNo + "_choke_" + i, this.padChokeGroup[i]);
            if (this.selectedWavUris[i] != null) {
                editor.putString("kit_" + kitNo + "_uri_" + i, this.selectedWavUris[i].toString());
                editor.remove("kit_" + kitNo + "_raw_" + i);
            } else if (this.selectedRawResIds[i] != 0) {
                editor.remove("kit_" + kitNo + "_uri_" + i);
                editor.putInt("kit_" + kitNo + "_raw_" + i, this.selectedRawResIds[i]);
            } else {
                editor.remove("kit_" + kitNo + "_uri_" + i);
                editor.remove("kit_" + kitNo + "_raw_" + i);
            }
        }
        if (this.assistSoundUri != null) {
            editor.putString("kit_" + kitNo + "_assist_uri", this.assistSoundUri.toString());
        } else {
            editor.remove("kit_" + kitNo + "_assist_uri");
        }
        editor.apply();
    }

    public void loadKitFromMemory(int kitNo) {
        // Guard: engine can be null if called while Activity is stopped (e.g. from
        // a kitRepeatRunnable that fired after onStop() set audioEngine = null).
        if (this.audioEngine == null) {
            Log.w(TAG, "loadKitFromMemory: audioEngine is null, skipping load for kitNo=" + kitNo);
            return;
        }
        Log.i(TAG, "loadKitFromMemory: loading kitNo=" + kitNo);
        if (kitNo <= this.presetKitNames.length) {
            this.currentPresetKit = kitNo - 1;
            this.currentKitName = this.prefs.getString("kit_name_" + kitNo, this.presetKitNames[this.currentPresetKit]);
        } else {
            this.currentKitName = this.prefs.getString("kit_name_" + kitNo, "KIT " + kitNo);
        }
        this.txtKitName.setText(this.currentKitName);
        Log.i(TAG, "loadKitFromMemory: loaded kitName='" + this.currentKitName + "'");
        for (int i = 0; i < 8; i++) {
            this.padVolume[i] = this.prefs.getFloat("kit_" + kitNo + "_vol_" + i, 0.8f);
            this.padPitch[i] = this.prefs.getFloat("kit_" + kitNo + "_pitch_" + i, 1.0f);
            this.padDelayOn[i] = this.prefs.getBoolean("kit_" + kitNo + "_dlyon_" + i, false);
            this.padDelayTime[i] = this.prefs.getFloat("kit_" + kitNo + "_dlyt_" + i, 150.0f);
            this.padDelayLevel[i] = this.prefs.getFloat("kit_" + kitNo + "_dlyl_" + i, 0.5f);
            this.padEqHigh[i] = this.prefs.getFloat("kit_" + kitNo + "_eqh_" + i, 0.0f);
            this.padEqMid[i] = this.prefs.getFloat("kit_" + kitNo + "_eqm_" + i, 0.0f);
            this.padEqLow[i] = this.prefs.getFloat("kit_" + kitNo + "_eql_" + i, 0.0f);
            this.padChokeGroup[i] = this.prefs.getInt("kit_" + kitNo + "_choke_" + i, 0);
            String uriStr = this.prefs.getString("kit_" + kitNo + "_uri_" + i, null);
            int rawResId = this.prefs.getInt("kit_" + kitNo + "_raw_" + i, 0);
            if (uriStr != null) {
                try {
                    this.selectedWavUris[i] = Uri.parse(uriStr);
                    this.selectedRawResIds[i] = 0;
                    this.samples[i] = this.audioEngine.loadWavFromUri(i, this.selectedWavUris[i]);
                    if (this.samples[i] != null) {
                        this.audioEngine.preloadSample(this.samples[i]);
                    } else {
                        this.selectedWavUris[i] = null;
                        if (kitNo <= this.presetKitNames.length) {
                            this.selectedRawResIds[i] = this.presetKits[this.currentPresetKit][i];
                        } else {
                            this.selectedRawResIds[i] = this.presetKits[0][i];
                        }
                        this.samples[i] = this.audioEngine.loadRawSound(i, this.selectedRawResIds[i]);
                        if (this.samples[i] != null) {
                            this.audioEngine.preloadSample(this.samples[i]);
                        }
                    }
                } catch (IOException e) {
                    this.samples[i] = null;
                    e.printStackTrace();
                }
            } else if (rawResId != 0) {
                this.selectedWavUris[i] = null;
                this.selectedRawResIds[i] = rawResId;
                try {
                    this.samples[i] = this.audioEngine.loadRawSound(i, rawResId);
                } catch (IOException e) {
                    this.samples[i] = null;
                    e.printStackTrace();
                }
                AudioEngine.SampleData sampleData2 = this.samples[i];
                if (sampleData2 != null) {
                    this.audioEngine.preloadSample(sampleData2);
                }
            } else {
                this.selectedWavUris[i] = null;
                if (kitNo <= this.presetKitNames.length) {
                    this.selectedRawResIds[i] = this.presetKits[this.currentPresetKit][i];
                } else {
                    this.selectedRawResIds[i] = this.presetKits[0][i];
                }
                try {
                    this.samples[i] = this.audioEngine.loadRawSound(i, this.selectedRawResIds[i]);
                } catch (IOException e) {
                    this.samples[i] = null;
                    e.printStackTrace();
                }
                AudioEngine.SampleData sampleData3 = this.samples[i];
                if (sampleData3 != null) {
                    this.audioEngine.preloadSample(sampleData3);
                }
            }
        }
        String assistUriStr = this.prefs.getString("kit_" + kitNo + "_assist_uri", null);
        if (assistUriStr != null) {
            this.assistSoundUri = Uri.parse(assistUriStr);
        } else {
            this.assistSoundUri = null;
        }
        this.seekVolume.setProgress((int) (this.padVolume[this.selectedPad] * 100.0f));
        this.seekPitch.setProgress((int) ((this.padPitch[this.selectedPad] - 0.5f) * 100.0f));
    }

    public void loadKitFromFolder(Uri folderUri) throws IOException {
        int i;
        String folderName;
        DocumentFile dataFile = null;
        DocumentFile kitFolder = null;
        JSONArray dlyLArray = null;
        JSONArray eqHArray = null;
        DocumentFile kitFolder2 = null;
        try {
            DocumentFile kitFolder3 = DocumentFile.fromTreeUri(this, folderUri);
            if (kitFolder3 == null) {
                Toast.makeText(this, "Folder not found!", 0).show();
                return;
            }
            int i2 = 0;
            while (true) {
                i = 8;
                if (i2 >= 8) {
                    break;
                }
                DocumentFile wav = kitFolder3.findFile(KitManager.DEFAULT_WAV_NAMES[i2]);
                if (wav != null) {
                    this.selectedWavUris[i2] = wav.getUri();
                    this.selectedRawResIds[i2] = 0;
                    this.samples[i2] = this.audioEngine.loadWavFromUri(i2, wav.getUri());
                    AudioEngine.SampleData sampleData = this.samples[i2];
                    if (sampleData != null) {
                        this.audioEngine.preloadSample(sampleData);
                    }
                } else {
                    this.selectedWavUris[i2] = null;
                    int[] iArr = this.selectedRawResIds;
                    int i22 = this.presetKits[this.currentPresetKit][i2];
                    iArr[i2] = i22;
                    this.samples[i2] = this.audioEngine.loadRawSound(i2, i22);
                    AudioEngine.SampleData sampleData2 = this.samples[i2];
                    if (sampleData2 != null) {
                        this.audioEngine.preloadSample(sampleData2);
                    }
                }
                i2++;
            }
            String folderName2 = kitFolder3.getName();
            if (folderName2 != null) {
                String strReplace = folderName2.replace(".mcn", "");
                this.currentKitName = strReplace;
                this.txtKitName.setText(strReplace);
            }
            DocumentFile dataFile2 = kitFolder3.findFile("kit_data.json");
            if (dataFile2 != null) {
                Exception lastException = null;
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(dataFile2.getUri());
                } catch (Exception e) {
                    lastException = e;
                }
                if (is != null) {
                    BufferedReader reader2 = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb2 = new StringBuilder();
                    while (true) {
                        String line = reader2.readLine();
                        if (line == null) {
                            break;
                        }
                        DocumentFile kitFolder4 = kitFolder3;
                        String folderName3 = folderName2;
                        DocumentFile dataFile3 = dataFile2;
                        try {
                            sb2.append(line);
                        } catch (Exception e2) {
                            lastException = e2;
                        }
                        if (lastException != null) {
                            lastException.printStackTrace();
                        }
                        dataFile2 = dataFile3;
                        kitFolder3 = kitFolder4;
                        folderName2 = folderName3;
                        i = 8;
                    }
                    is.close();
                    JSONObject jsonData = new JSONObject(sb2.toString());
                    JSONArray volArray = jsonData.optJSONArray("volume");
                    JSONArray pitchArray = jsonData.optJSONArray("pitch");
                    JSONArray dlyOnArray = jsonData.optJSONArray("delayOn");
                    JSONArray dlyTArray = jsonData.optJSONArray("delayTime");
                    JSONArray dlyLArray2 = jsonData.optJSONArray("delayLevel");
                    JSONArray dlyLArray3 = jsonData.optJSONArray("eqHigh");
                    JSONArray eqHArray2 = jsonData.optJSONArray("eqMid");
                    JSONArray eqLArray = jsonData.optJSONArray("eqLow");
                    try {
                        JSONArray chokeArray = jsonData.optJSONArray("chokeGroup");
                        int i3 = 0;
                        while (i3 < i) {
                            if (volArray != null) {
                                try {
                                    folderName = folderName2;
                                    try {
                                        this.padVolume[i3] = (float) volArray.getDouble(i3);
                                    } catch (Exception e3) {
                                    }
                                } catch (Exception e4) {
                                    folderName = folderName2;
                                }
                            } else {
                                folderName = folderName2;
                            }
                            if (pitchArray != null) {
                                try {
                                    this.padPitch[i3] = (float) pitchArray.getDouble(i3);
                                } catch (Exception e5) {
                                }
                            }
                            if (dlyOnArray != null) {
                                this.padDelayOn[i3] = dlyOnArray.getBoolean(i3);
                            }
                            if (dlyTArray != null) {
                                this.padDelayTime[i3] = (float) dlyTArray.getDouble(i3);
                            }
                            JSONArray dlyLArray4 = dlyLArray2;
                            if (dlyLArray4 != null) {
                                try {
                                    dataFile = dataFile2;
                                    try {
                                        this.padDelayLevel[i3] = (float) dlyLArray4.getDouble(i3);
                                    } catch (Exception e6) {
                                    }
                                } catch (Exception e7) {
                                }
                            } else {
                                dataFile = dataFile2;
                            }
                            JSONArray eqHArray3 = dlyLArray3;
                            if (eqHArray3 != null) {
                                try {
                                    kitFolder = kitFolder3;
                                    dlyLArray = dlyLArray4;
                                    try {
                                        this.padEqHigh[i3] = (float) eqHArray3.getDouble(i3);
                                    } catch (Exception e8) {
                                    }
                                } catch (Exception e9) {
                                }
                            } else {
                                kitFolder = kitFolder3;
                                dlyLArray = dlyLArray4;
                            }
                            JSONArray eqMArray = eqHArray2;
                            if (eqMArray != null) {
                                try {
                                    eqHArray = eqHArray3;
                                    try {
                                        this.padEqMid[i3] = (float) eqMArray.getDouble(i3);
                                    } catch (Exception e10) {
                                    }
                                } catch (Exception e11) {
                                }
                            } else {
                                eqHArray = eqHArray3;
                            }
                            JSONArray eqLArray2 = eqLArray;
                            if (eqLArray2 != null) {
                                try {
                                    kitFolder2 = kitFolder;
                                    try {
                                        this.padEqLow[i3] = (float) eqLArray2.getDouble(i3);
                                    } catch (Exception e12) {
                                    }
                                } catch (Exception e13) {
                                }
                            } else {
                                kitFolder2 = kitFolder;
                            }
                            JSONArray chokeArray2 = chokeArray;
                            if (chokeArray2 != null) {
                                this.padChokeGroup[i3] = chokeArray2.getInt(i3);
                            }
                            i3++;
                            chokeArray = chokeArray2;
                            dataFile2 = dataFile;
                            dlyLArray2 = dlyLArray;
                            dlyLArray3 = eqHArray;
                            folderName2 = folderName;
                            eqHArray2 = eqMArray;
                            kitFolder3 = kitFolder2;
                            eqLArray = eqLArray2;
                            i = 8;
                        }
                    } catch (Exception e14) {
                    }
                }
            }
            this.seekVolume.setProgress((int) (this.padVolume[this.selectedPad] * 100.0f));
            this.seekPitch.setProgress((int) ((this.padPitch[this.selectedPad] - 0.5f) * 100.0f));
            saveKitToMemory(this.kitIndex);
            Toast.makeText(this, "Kit Loaded Successfully!", 0).show();
        } catch (Exception ignored) {
            ignored.printStackTrace();
            Toast.makeText(this, "Load Error: " + ignored.getMessage(), 0).show();
        }
    }

    private void saveKitToFolder(Uri folderUri) throws JSONException, IOException {
        DocumentFile root;
        DocumentFile dataFile;
        JSONArray eqMArray;
        JSONArray chokeArray;
        int i2;
        JSONArray eqLArray;
        DocumentFile root2 = null;
        try {
            DocumentFile root3 = DocumentFile.fromTreeUri(this, folderUri);
            if (root3 == null) {
                Toast.makeText(this, "Folder access error!", 0).show();
                return;
            }
            DocumentFile kitFolder = root3.findFile(this.currentKitName + ".mcn");
            if (kitFolder == null) {
                kitFolder = root3.createDirectory(this.currentKitName + ".mcn");
            }
            if (kitFolder == null) {
                Toast.makeText(this, "Cannot create kit folder!", 0).show();
                return;
            }
            int i22 = 0;
            while (i22 < 8) {
                int i23 = i22;
                if (this.selectedWavUris[i23] != null || this.selectedRawResIds[i23] != 0) {
                    DocumentFile old = kitFolder.findFile(KitManager.DEFAULT_WAV_NAMES[i23]);
                    if (old != null) {
                        old.delete();
                    }
                    DocumentFile dest = kitFolder.createFile("audio/wav", KitManager.DEFAULT_WAV_NAMES[i23]);
                    if (dest != null) {
                        Uri uri = this.selectedWavUris[i23];
                        if (uri != null) {
                            FileUtil.copyUriToUri(this, uri, dest.getUri());
                        } else {
                            int i3 = this.selectedRawResIds[i23];
                            if (i3 != 0) {
                                FileUtil.copyRawToUri(this, i3, dest.getUri());
                            }
                        }
                    }
                }
                i22 = i23 + 1;
            }
            DocumentFile dataFile2 = kitFolder.findFile("kit_data.json");
            if (dataFile2 != null) {
                dataFile2.delete();
            }
            DocumentFile dataFile22 = kitFolder.createFile("application/json", "kit_data.json");
            if (dataFile22 != null) {
                try {
                    JSONObject jsonData = new JSONObject();
                    JSONArray volArray = new JSONArray();
                    JSONArray pitchArray = new JSONArray();
                    JSONArray dlyOnArray = new JSONArray();
                    JSONArray dlyTArray = new JSONArray();
                    JSONArray dlyLArray = new JSONArray();
                    JSONArray eqHArray = new JSONArray();
                    JSONArray eqMArray2 = new JSONArray();
                    JSONArray eqLArray2 = new JSONArray();
                    JSONArray chokeArray2 = new JSONArray();
                    int i = 8;
                    DocumentFile kitFolder2 = kitFolder;
                    DocumentFile kitFolder3 = root3;
                    int i4 = 0;
                    while (i4 < i) {
                        DocumentFile root22 = kitFolder3;
                        DocumentFile kitFolder22 = kitFolder2;
                        try {
                            root = kitFolder3;
                            dataFile = dataFile2;
                            try {
                                volArray.put(this.padVolume[i4]);
                                pitchArray.put(this.padPitch[i4]);
                                dlyOnArray.put(this.padDelayOn[i4]);
                                dlyTArray.put(this.padDelayTime[i4]);
                                dlyLArray.put(this.padDelayLevel[i4]);
                                eqHArray.put(this.padEqHigh[i4]);
                                eqMArray = eqMArray2;
                                try {
                                    eqMArray.put(this.padEqMid[i4]);
                                    i2 = i22;
                                    eqLArray = eqLArray2;
                                    try {
                                        eqLArray.put(this.padEqLow[i4]);
                                        chokeArray = chokeArray2;
                                    } catch (Exception e) {
                                        chokeArray = chokeArray2;
                                    }
                                } catch (Exception e2) {

                                    i2 = i22;
                                    eqLArray = eqLArray2;
                                    chokeArray = chokeArray2;
                                }
                                try {
                                    chokeArray.put(this.padChokeGroup[i4]);
                                    i4++;
                                    root2 = root22;
                                    kitFolder2 = kitFolder22;
                                } catch (Exception e3) {

                                    try {
                                        e3.printStackTrace();
                                        Toast.makeText(this, "Kit Saved: " + this.currentKitName, 0).show();
                                        root2 = root;
                                        eqLArray2 = eqLArray;
                                        chokeArray2 = chokeArray;
                                        i22 = i2;
                                        kitFolder3 = root2;
                                        eqMArray2 = eqMArray;
                                        i = 8;
                                        dataFile2 = dataFile;
                                    } catch (Exception e4) {

                                        e4.printStackTrace();
                                        Toast.makeText(this, "Kit Saved: " + this.currentKitName, 0).show();
                                    }
                                }
                            } catch (Exception e5) {

                                eqMArray = eqMArray2;
                                chokeArray = chokeArray2;
                                i2 = i22;
                                eqLArray = eqLArray2;
                            }
                        } catch (Exception e6) {

                            root = kitFolder3;
                            dataFile = dataFile2;
                            eqMArray = eqMArray2;
                            chokeArray = chokeArray2;
                            i2 = i22;
                            eqLArray = eqLArray2;
                        }
                        eqLArray2 = eqLArray;
                        chokeArray2 = chokeArray;
                        i22 = i2;
                        kitFolder3 = root2;
                        eqMArray2 = eqMArray;
                        i = 8;
                        dataFile2 = dataFile;
                    }
                    root = kitFolder3;
                    jsonData.put("volume", volArray);
                    jsonData.put("pitch", pitchArray);
                    jsonData.put("delayOn", dlyOnArray);
                    jsonData.put("delayTime", dlyTArray);
                    jsonData.put("delayLevel", dlyLArray);
                    jsonData.put("eqHigh", eqHArray);
                    jsonData.put("eqMid", eqMArray2);
                    jsonData.put("eqLow", eqLArray2);
                    jsonData.put("chokeGroup", chokeArray2);
                    OutputStream out = getContentResolver().openOutputStream(dataFile22.getUri());
                    if (out != null) {
                        out.write(jsonData.toString().getBytes());
                        out.close();
                    }
                } catch (Exception e7) {

                }
            }
            Toast.makeText(this, "Kit Saved: " + this.currentKitName, 0).show();
        } catch (Exception e32) {
            e32.printStackTrace();
            Toast.makeText(this, "Save Error: " + e32.getMessage(), 0).show();
        }
    }

    public void openListFolderPicker() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
        intent.addFlags(1);
        intent.addFlags(2);
        intent.addFlags(64);
        startActivityForResult(intent, REQ_LIST_FOLDER);
    }

    private void scanForMcnFolders(DocumentFile folder, ArrayList<DocumentFile> kitFolders, ArrayList<String> kitNames) {
        DocumentFile[] listFiles;
        String name;
        for (DocumentFile file : folder.listFiles()) {
            if (file != null && (name = file.getName()) != null && file.isDirectory()) {
                if (name.toLowerCase().endsWith(".mcn")) {
                    kitFolders.add(file);
                    kitNames.add(name.substring(0, name.length() - 4));
                } else {
                    scanForMcnFolders(file, kitFolders, kitNames);
                }
            }
        }
    }

    private void showKitListDialog(Uri folderUri) {
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, folderUri);
            if (root == null || !root.exists() || !root.isDirectory()) {
                Toast.makeText(this, "Invalid folder! Choose again.", 0).show();
                openListFolderPicker();
                return;
            }
            final ArrayList<DocumentFile> kitFolders = new ArrayList<>();
            ArrayList<String> kitNames = new ArrayList<>();
            scanForMcnFolders(root, kitFolders, kitNames);
            if (kitNames.size() == 0) {
                Toast.makeText(this, "No .mcn kit folders found in this folder!", 0).show();
                return;
            }
            String[] items = (String[]) kitNames.toArray(new String[0]);
            DialogInterface.OnClickListener onClickListener = null;
            new AlertDialog.Builder(this).setTitle("Select Kit").setItems(items, new DialogInterface.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.25


                @Override // android.content.DialogInterface.OnClickListener
                public void onClick(DialogInterface dialog, int which) {
                    DocumentFile selectedKitFolder = (DocumentFile) kitFolders.get(which);
                    try {
                        MainActivity.this.loadKitFromFolder(selectedKitFolder.getUri());
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, "Error loading kit: " + e.getMessage(), 0).show();
                        e.printStackTrace();
                    }
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                }
            }).setNeutralButton("Change Folder", new DialogInterface.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.24
                @Override // android.content.DialogInterface.OnClickListener
                public void onClick(DialogInterface dialog, int which) {
                    MainActivity.this.openListFolderPicker();
                }
            }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "List Error: " + e.getMessage(), 0).show();
        }
    }

    // ── Kit navigation helpers ─────────────────────────────────────────────────

    /** Single kit step; safe to call from any thread that owns the UI. */
    private void changeKitBy(int direction) {
        if (direction < 0) {
            if (kitIndex > 1) {
                saveKitToMemory(kitIndex);
                kitIndex--;
                prefs.edit().putInt(KEY_KIT_INDEX, kitIndex).apply();
                loadKitFromMemory(kitIndex);
            }
        } else {
            if (kitIndex < MAX_KITS) {
                saveKitToMemory(kitIndex);
                kitIndex++;
                prefs.edit().putInt(KEY_KIT_INDEX, kitIndex).apply();
                loadKitFromMemory(kitIndex);
            }
        }
    }

    /**
     * Attaches a hold-to-repeat touch listener to a kit nav button.
     * Behaviour: tap = 1 step; hold 500 ms → starts repeating at 300 ms,
     * accelerates to 120 ms → 60 ms → 30 ms (Roland SPD-20 Pro feel).
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupKitHoldButton(Button btn, final int direction) {
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    changeKitBy(direction);
                    kitRepeatRunnable = new Runnable() {
                        private int step = 0;
                        @Override public void run() {
                            step++;
                            changeKitBy(direction);
                            long delay = step < 5 ? 300L : step < 15 ? 120L : step < 30 ? 60L : 30L;
                            kitRepeatHandler.postDelayed(this, delay);
                        }
                    };
                    kitRepeatHandler.postDelayed(kitRepeatRunnable, 500);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    kitRepeatHandler.removeCallbacks(kitRepeatRunnable);
                    kitRepeatRunnable = null;
                    return true;
            }
            return false;
        });
    }

    /** Tap kit name → number keyboard → jump directly to any kit 1-100. */
    private void showKitJumpDialog() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("1 – " + MAX_KITS);
        et.setTextColor(0xffffffff);
        et.setHintTextColor(0xff888888);
        et.setGravity(Gravity.CENTER);
        et.setTextSize(26);
        new AlertDialog.Builder(this)
            .setTitle("Kit number daalo (1–" + MAX_KITS + ")")
            .setView(et)
            .setPositiveButton("GO ▶", (d, w) -> {
                String s = et.getText().toString().trim();
                if (!s.isEmpty()) {
                    try {
                        int target = Integer.parseInt(s);
                        if (target >= 1 && target <= MAX_KITS) {
                            saveKitToMemory(kitIndex);
                            kitIndex = target;
                            prefs.edit().putInt(KEY_KIT_INDEX, kitIndex).apply();
                            loadKitFromMemory(kitIndex);
                        } else {
                            Toast.makeText(this, "1 se " + MAX_KITS + " ke beech daalo!", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException ignored) {}
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
        et.post(() -> {
            et.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    // ──────────────────────────────────────────────────────────────────────────

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        this.isVisible = false;
        saveKitToMemory(this.kitIndex);
    }

    @Override // android.app.Activity
    protected void onStop() {
        super.onStop();
        // Cancel hold-repeat BEFORE destroying the engine.
        // kitRepeatRunnable fires on the main thread; if it fires after
        // audioEngine = null below, changeKitBy() → loadKitFromMemory() crashes
        // with NullPointerException. Cancelling here prevents that entirely.
        if (kitRepeatRunnable != null) {
            kitRepeatHandler.removeCallbacks(kitRepeatRunnable);
            kitRepeatRunnable = null;
        }
        saveKitToMemory(this.kitIndex);
        // Full APK: when LoopsActivity comes to the foreground, MainActivity
        // goes to onStop(). If we leave our Oboe stream running, two streams
        // compete for the audio hardware simultaneously → underruns, crackling,
        // and distortion in LoopsActivity ("kharab sound").
        // Destroy the engine here; onResume() will recreate it + reload samples.
        if (this.audioEngine != null) {
            try { this.audioEngine.stop(); } catch (Exception ignored) {}
            this.audioEngine = null;
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        if (deactivateListener != null && deactivateRef != null) {
            deactivateRef.removeEventListener(deactivateListener);
            deactivateListener = null;
        }
        teardownAudioRouting();   // unregister earphone / BT callbacks
        saveKitToMemory(this.kitIndex);
        try {
            closeMidiDevice();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            AudioEngine audioEngine = this.audioEngine;
            if (audioEngine != null) {
                audioEngine.stop();
                this.audioEngine = null;
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    // ── Audio-routing helpers ─────────────────────────────────────────────────

    /**
     * Register two listeners so drum pads keep working when the user plugs
     * or unplugs earphones / connects Bluetooth audio:
     *
     *   1. AudioDeviceCallback — fires on the main thread whenever an output
     *      device is added or removed.  We reinit the Oboe stream so it opens
     *      on the correct device at that device's native SR / burst size.
     *
     *   2. ACTION_AUDIO_BECOMING_NOISY receiver — fires when earphones are
     *      suddenly unplugged and audio would otherwise blast from the speaker.
     *      For drum pads (one-shot), we just reinit the stream; no loops to stop.
     */
    private void setupAudioRouting() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        // 1. Device-change callback (earphone plug / BT connect & disconnect)
        audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                for (AudioDeviceInfo d : addedDevices) {
                    if (d.isSink()) {
                        reinitAudioForNewDevice(am);
                        return;
                    }
                }
            }
            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                for (AudioDeviceInfo d : removedDevices) {
                    if (d.isSink()) {
                        reinitAudioForNewDevice(am);
                        return;
                    }
                }
            }
        };
        am.registerAudioDeviceCallback(audioDeviceCallback,
                new Handler(Looper.getMainLooper()));

        // 2. Becoming-noisy receiver (earphone suddenly unplugged)
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    // Drum pads are one-shot — just reinit the stream so the
                    // next hit routes to the speaker cleanly.
                    reinitAudioForNewDevice(am);
                }
            }
        };
        registerReceiver(noisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    /** Unregister audio-routing listeners — called from onDestroy(). */
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

    /**
     * Reinit the Oboe stream with fresh AudioManager properties for the
     * currently active output device (earphone / BT / speaker).
     * Sample data stays loaded in the C++ engine — only the stream restarts.
     */
    private void reinitAudioForNewDevice(AudioManager am) {
        final AudioEngine engine = this.audioEngine;
        if (engine == null) return;
        int nativeSR = 48000, nativeBurst = 256;
        try {
            String srStr    = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            String burstStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            if (srStr    != null && !srStr.isEmpty())    nativeSR    = Integer.parseInt(srStr);
            if (burstStr != null && !burstStr.isEmpty()) nativeBurst = Integer.parseInt(burstStr);
            if (nativeSR    < 8000  || nativeSR    > 192000) nativeSR    = 48000;
            if (nativeBurst < 32    || nativeBurst > 8192)   nativeBurst = 256;
        } catch (NumberFormatException ignored) {}
        engine.reinitStream(nativeSR, nativeBurst);
    }
}
