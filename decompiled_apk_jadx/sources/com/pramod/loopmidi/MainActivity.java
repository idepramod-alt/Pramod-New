package com.pramod.loopmidi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private static final int MAX_KITS = 50;
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

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.isVisible = true;
    }

    public void onStopLoopClick(View view) {
        try {
            LoopsActivity loopsActivity = LoopsActivity.globalInstance;
            if (loopsActivity != null) {
                for (int i = 0; i < 8; i++) {
                    if (loopsActivity.loopPlaying[i]) {
                        loopsActivity.audioEngine.stopPad(i);
                        loopsActivity.loopPlaying[i] = false;
                        loopsActivity.loopPads[i].setBackgroundResource(R.drawable.pad_black_selector);
                    }
                }
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
            this.midiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() { // from class: com.pramod.loopmidi.MainActivity.2
                @Override // android.media.midi.MidiManager.OnDeviceOpenedListener
                public void onDeviceOpened(MidiDevice device) {
                    MainActivity.this.openedMidiDevice = device;
                    int portCount = device.getInfo().getOutputPortCount();
                    for (int portIndex = 0; portIndex < portCount; portIndex++) {
                        MidiOutputPort port = device.openOutputPort(portIndex);
                        if (port != null) {
                            if (MainActivity.this.midiOutputPort == null) {
                                MainActivity.this.midiOutputPort = port;
                            }
                            MainActivity.this.midiOutputPorts.add(port);
                            MainActivity.this.txtMidiStatus.setText("MIDI connected");
                            port.connect(new MidiReceiver() { // from class: com.pramod.loopmidi.MainActivity.2.1
                                @Override // android.media.midi.MidiReceiver
                                public void onSend(byte[] msg, int offset, int count, long timestamp) {
                                    int end = offset + count;
                                    int status = 0;
                                    int i = offset;
                                    while (i < end) {
                                        int value = msg[i] & UByte.MAX_VALUE;
                                        if (value >= 128) {
                                            status = value;
                                        } else if ((status & 240) != 144) {
                                            if ((status & 240) != 128) {
                                                continue;
                                            } else if (i + 1 < end) {
                                                int noteOff = msg[i] & UByte.MAX_VALUE;
                                                MainActivity.this.handleMidiNoteOff((byte) noteOff);
                                                i++;
                                            } else {
                                                return;
                                            }
                                        } else if (i + 1 < end) {
                                            int velocity = msg[i + 1] & UByte.MAX_VALUE;
                                            if (velocity > 0) {
                                                MainActivity.this.handleMidiNoteOn((byte) value, (byte) velocity);
                                            } else {
                                                MainActivity.this.handleMidiNoteOff((byte) value);
                                            }
                                            i++;
                                        } else {
                                            return;
                                        }
                                        i++;
                                    }
                                }
                            });
                        }
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
            playPadSoundImmediate(finalPadIndex);
            runOnUiThread(new Runnable(this) { // from class: com.pramod.loopmidi.MainActivity.3
                final /* synthetic */ MainActivity this$0;

                {
                    this.this$0 = this;
                }

                @Override // java.lang.Runnable
                public void run() {
                    try {
                        this.this$0.pads[finalPadIndex].setPressed(true);
                    } catch (Exception e) {
                    }
                    Handler handler = new Handler(Looper.getMainLooper());
                    final int i = finalPadIndex;
                    handler.postDelayed(new Runnable(this) { // from class: com.pramod.loopmidi.MainActivity.3.1
                        final /* synthetic */ AnonymousClass3 this$1;

                        {
                            this.this$1 = this;
                        }

                        @Override // java.lang.Runnable
                        public void run() {
                            try {
                                this.this$1.this$0.pads[i].setPressed(false);
                            } catch (Exception e2) {
                            }
                        }
                    }, 100L);
                }
            });
        }
    }

    public void handleMidiNoteOff(byte note) {
        int padIndex;
        if (this.isVisible && (padIndex = note % 8) >= 0 && padIndex < 8) {
            try {
                if (this.audioEngine != null) {
                    this.audioEngine.stopPad(padIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling MIDI note off", e);
            }
        }
    }

    private void playPadSoundImmediate(int index) {
        try {
            AudioEngine.SampleData sampleData = this.samples[index];
            if (sampleData != null && sampleData.loaded) {
                this.audioEngine.playSample(index, sampleData, this.padVolume[index], this.padPitch[index], 0, this.padDelayOn[index], this.padDelayTime[index], this.padDelayLevel[index], this.padEqLow[index], this.padEqMid[index], this.padEqHigh[index], this.padChokeGroup[index], 0.0f, 0.0f);
            }
        } catch (Exception e) {
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
        Button button = (Button) findViewById(R.id.btnLoops);
        this.btnLoops = button;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.4
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, LoopsActivity.class);
                    MainActivity.this.startActivity(intent);
                }
            });
        }
        this.seekVolume = (SeekBar) findViewById(R.id.seekVolume);
        this.seekPitch = (SeekBar) findViewById(R.id.seekPitch);
        this.fxControlBar = findViewById(R.id.fxControlBar);
        this.advControlBar = findViewById(R.id.advControlBar);
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
        initPads();
        initSeekBars();
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
        this.btnPrevKit.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.7
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                if (MainActivity.this.kitIndex > 1) {
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                    MainActivity.access$1210(MainActivity.this);
                    MainActivity.this.prefs.edit().putInt(MainActivity.KEY_KIT_INDEX, MainActivity.this.kitIndex).apply();
                    MainActivity mainActivity2 = MainActivity.this;
                    mainActivity2.loadKitFromMemory(mainActivity2.kitIndex);
                    return;
                }
                Toast.makeText(MainActivity.this, "Already First Kit!", 0).show();
            }
        });
        this.btnNextKit.setOnClickListener(new View.OnClickListener() { // from class: com.pramod.loopmidi.MainActivity.8
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                if (MainActivity.this.kitIndex < 50) {
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.saveKitToMemory(mainActivity.kitIndex);
                    MainActivity.access$1208(MainActivity.this);
                    MainActivity.this.prefs.edit().putInt(MainActivity.KEY_KIT_INDEX, MainActivity.this.kitIndex).apply();
                    MainActivity mainActivity2 = MainActivity.this;
                    mainActivity2.loadKitFromMemory(mainActivity2.kitIndex);
                    return;
                }
                Toast.makeText(MainActivity.this, "Max Kit Limit Reached!", 0).show();
            }
        });
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
                v.setPressed(true);
                if (!MainActivity.this.editMode) {
                    MainActivity.this.playPadSound(this.index);
                }
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
        new AlertDialog.Builder(this).setTitle("PAD " + (padIndex + 1) + " - EDIT OPTIONS").setItems(options, new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.MainActivity.21
            final /* synthetic */ MainActivity this$0;

            {
                this.this$0 = this;
            }

            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                if (which != 0) {
                    if (which == 1) {
                        this.this$0.copySourcePad = padIndex;
                        this.this$0.swapSourcePad = -1;
                        Toast.makeText(this.this$0, "Copy Mode ON: Now tap target PAD to paste", 0).show();
                        return;
                    } else if (which == 2) {
                        this.this$0.swapSourcePad = padIndex;
                        this.this$0.copySourcePad = -1;
                        Toast.makeText(this.this$0, "Exchange Mode ON: Now tap second PAD to swap", 0).show();
                        return;
                    } else if (which == 3) {
                        this.this$0.selectedWavUris[padIndex] = null;
                        this.this$0.selectedRawResIds[padIndex] = 0;
                        this.this$0.samples[padIndex] = null;
                        this.this$0.padVolume[padIndex] = 0.8f;
                        this.this$0.padPitch[padIndex] = 1.0f;
                        this.this$0.padDelayOn[padIndex] = false;
                        this.this$0.padDelayTime[padIndex] = 150.0f;
                        this.this$0.padDelayLevel[padIndex] = 0.5f;
                        this.this$0.padEqHigh[padIndex] = 0.0f;
                        this.this$0.padEqMid[padIndex] = 0.0f;
                        this.this$0.padEqLow[padIndex] = 0.0f;
                        this.this$0.padChokeGroup[padIndex] = 0;
                        MainActivity mainActivity = this.this$0;
                        mainActivity.saveKitToMemory(mainActivity.kitIndex);
                        Toast.makeText(this.this$0, "PAD " + (padIndex + 1) + " Cleared!", 0).show();
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
                this.this$0.startActivityForResult(intent, MainActivity.REQ_PICK_SINGLE_WAV);
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
                e = e;
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
                    e = e2;
                    Toast.makeText(this, "Error copying sound: " + e.getMessage(), 0).show();
                    this.samples[toPad] = null;
                    saveKitToMemory(this.kitIndex);
                    Toast.makeText(this, "Copied PAD " + (fromPad + 1) + " -> PAD " + (toPad + 1), 0).show();
                }
            } catch (IOException e3) {
                e = e3;
            }
        }
        saveKitToMemory(this.kitIndex);
        Toast.makeText(this, "Copied PAD " + (fromPad + 1) + " -> PAD " + (toPad + 1), 0).show();
    }

    public void swapPadSound(int padA, int padB) {
        Uri uri;
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
        } catch (IOException e) {
            e = e;
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
            e = e2;
            Toast.makeText(this, "Error swapping sounds: " + e.getMessage(), 0).show();
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
        new AlertDialog.Builder(this).setTitle("Save Kit As").setView(edt).setPositiveButton("NEXT", new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.MainActivity.22
            final /* synthetic */ MainActivity this$0;

            {
                this.this$0 = this;
            }

            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = edt.getText().toString().trim();
                if (name.length() != 0) {
                    this.this$0.pendingSaveKitName = this.this$0.sanitizeFileName(name);
                    this.this$0.startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), MainActivity.REQ_SAVE_FOLDER);
                    return;
                }
                Toast.makeText(this.this$0, "Kit name required!", 0).show();
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    public void renameKitDialog() {
        final EditText edt = new EditText(this);
        edt.setText(this.currentKitName);
        new AlertDialog.Builder(this).setTitle("Enter Kit Name").setView(edt).setPositiveButton("OK", new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.MainActivity.23
            final /* synthetic */ MainActivity this$0;

            {
                this.this$0 = this;
            }

            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface d, int w) {
                this.this$0.currentKitName = edt.getText().toString().trim();
                if (this.this$0.currentKitName.length() == 0) {
                    this.this$0.currentKitName = "KIT " + this.this$0.kitIndex;
                }
                this.this$0.txtKitName.setText(this.this$0.currentKitName);
                MainActivity mainActivity = this.this$0;
                Log.i(MainActivity.TAG, "renameKitDialog: saving kit name='" + this.this$0.currentKitName + "' for kitNo=" + mainActivity.kitIndex);
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
                this.samples[i] = this.audioEngine.loadRawSound(i, rawResId);
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
                this.samples[i] = this.audioEngine.loadRawSound(i, this.selectedRawResIds[i]);
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
        DocumentFile dataFile;
        DocumentFile kitFolder;
        JSONArray dlyLArray;
        JSONArray eqHArray;
        DocumentFile kitFolder2;
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
        DocumentFile root2;
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
                                        e = e;
                                        chokeArray = chokeArray2;
                                    }
                                } catch (Exception e2) {
                                    e = e2;
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
                                    e = e3;
                                    try {
                                        e.printStackTrace();
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
                                        e2 = e4;
                                        e2.printStackTrace();
                                        Toast.makeText(this, "Kit Saved: " + this.currentKitName, 0).show();
                                    }
                                }
                            } catch (Exception e5) {
                                e = e5;
                                eqMArray = eqMArray2;
                                chokeArray = chokeArray2;
                                i2 = i22;
                                eqLArray = eqLArray2;
                            }
                        } catch (Exception e6) {
                            e = e6;
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
                    e2 = e7;
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
            new AlertDialog.Builder(this).setTitle("Select Kit").setItems(items, new DialogInterface.OnClickListener(this) { // from class: com.pramod.loopmidi.MainActivity.25
                final /* synthetic */ MainActivity this$0;

                {
                    this.this$0 = this;
                }

                @Override // android.content.DialogInterface.OnClickListener
                public void onClick(DialogInterface dialog, int which) {
                    DocumentFile selectedKitFolder = (DocumentFile) kitFolders.get(which);
                    try {
                        this.this$0.loadKitFromFolder(selectedKitFolder.getUri());
                    } catch (IOException e) {
                        Toast.makeText(this.this$0, "Error loading kit: " + e.getMessage(), 0).show();
                        e.printStackTrace();
                    }
                    MainActivity mainActivity = this.this$0;
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

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        this.isVisible = false;
        saveKitToMemory(this.kitIndex);
    }

    @Override // android.app.Activity
    protected void onStop() {
        super.onStop();
        saveKitToMemory(this.kitIndex);
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
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
}
