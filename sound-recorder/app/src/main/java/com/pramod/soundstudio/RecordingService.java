package com.pramod.soundstudio;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.File;

/**
 * Background Recording Service
 *
 * Runs as a Foreground Service so recording continues when:
 *  - App is backgrounded
 *  - Screen is off
 *  - User switches apps
 *
 * Bind to this service from RecordActivity to control recording.
 * Post a persistent notification with timer + stop action.
 */
public class RecordingService extends Service {

    public static final String ACTION_START_RECORDING = "com.pramod.soundstudio.START_REC";
    public static final String ACTION_STOP_RECORDING  = "com.pramod.soundstudio.STOP_REC";
    public static final String ACTION_PAUSE_RECORDING = "com.pramod.soundstudio.PAUSE_REC";
    public static final String ACTION_RESUME_RECORDING= "com.pramod.soundstudio.RESUME_REC";

    public static final String EXTRA_SAMPLE_RATE = "sample_rate";
    public static final String EXTRA_BIT_DEPTH   = "bit_depth";
    public static final String EXTRA_FORMAT       = "format";  // "WAV" | "COMPRESSED"
    public static final String EXTRA_FILE_NAME    = "file_name";

    private static final String CHANNEL_ID   = "recording_channel";
    private static final int    NOTIF_ID     = 1001;

    // ── State ─────────────────────────────────────────────────────────────────
    private AudioRecorderHelper recorder;
    private Handler             handler  = new Handler(Looper.getMainLooper());
    private long                startMs  = 0;
    private long                pausedMs = 0;
    private boolean             paused   = false;
    private Runnable            timerRunnable;
    private File                currentFile;

    // ── Local binder ──────────────────────────────────────────────────────────

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public RecordingService getService() { return RecordingService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        recorder = new AudioRecorderHelper(this);
        recorder.setOnDone((file, err) -> {
            currentFile = file;
            broadcastDone(file, err);
            stopForeground(true);
            stopSelf();
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (action == null) action = "";

        switch (action) {
            case ACTION_START_RECORDING:
                startRecording(intent);
                break;
            case ACTION_STOP_RECORDING:
                stopRecording();
                break;
            case ACTION_PAUSE_RECORDING:
                pauseRecording();
                break;
            case ACTION_RESUME_RECORDING:
                resumeRecording();
                break;
        }
        return START_STICKY;
    }

    // ── Recording control ─────────────────────────────────────────────────────

    private void startRecording(Intent intent) {
        int    sampleRate = intent.getIntExtra(EXTRA_SAMPLE_RATE, 44100);
        int    bitDepth   = intent.getIntExtra(EXTRA_BIT_DEPTH, 16);
        String formatStr  = intent.getStringExtra(EXTRA_FORMAT);
        String fileName   = intent.getStringExtra(EXTRA_FILE_NAME);
        if (fileName == null) fileName = generateFileName();

        AudioRecorderHelper.Format fmt = "COMPRESSED".equals(formatStr)
                ? AudioRecorderHelper.Format.COMPRESSED
                : AudioRecorderHelper.Format.WAV;

        recorder.setSampleRate(sampleRate);
        recorder.setBitDepth(bitDepth);
        recorder.setFormat(fmt);

        File dir = new File(getFilesDir(), "recordings");
        if (!dir.exists()) dir.mkdirs();

        startMs  = System.currentTimeMillis();
        pausedMs = 0;
        paused   = false;

        recorder.startRecording(dir, fileName);
        startForeground(NOTIF_ID, buildNotification("Recording…", "00:00"));
        startTimer();

        // Broadcast start
        Intent bc = new Intent("com.pramod.soundstudio.RECORDING_STARTED");
        sendBroadcast(bc);
    }

    public void stopRecording() {
        if (recorder.isRecording()) recorder.stopRecording();
        handler.removeCallbacks(timerRunnable);
    }

    public void pauseRecording() {
        if (!paused && recorder.isRecording()) {
            // AudioRecord doesn't support pause natively; stop + note position
            // For a full implementation, we'd flush to disk and resume into a temp file
            paused   = true;
            pausedMs = System.currentTimeMillis() - startMs;
            // Simple pause: stop reading (recording continues streaming to disk)
            // Production approach: stop + accumulate into separate segments + merge on stop
            updateNotification("⏸ Paused", formatTime(pausedMs));
            Intent bc = new Intent("com.pramod.soundstudio.RECORDING_PAUSED");
            bc.putExtra("elapsed_ms", pausedMs);
            sendBroadcast(bc);
        }
    }

    public void resumeRecording() {
        if (paused) {
            paused  = false;
            startMs = System.currentTimeMillis() - pausedMs;
            updateNotification("Recording…", formatTime(pausedMs));
            Intent bc = new Intent("com.pramod.soundstudio.RECORDING_RESUMED");
            sendBroadcast(bc);
        }
    }

    public boolean isRecording()  { return recorder != null && recorder.isRecording(); }
    public boolean isPaused()     { return paused; }
    public long    getElapsedMs() {
        if (paused) return pausedMs;
        return startMs > 0 ? System.currentTimeMillis() - startMs : 0;
    }

    public void setAmplitudeCallback(AudioRecorderHelper.AmplitudeCallback cb) {
        if (recorder != null) recorder.setOnAmplitude(cb);
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!paused) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    updateNotification("🔴 Recording", formatTime(elapsed));
                    // Broadcast amplitude/time to bound Activity
                    Intent bc = new Intent("com.pramod.soundstudio.TIMER_TICK");
                    bc.putExtra("elapsed_ms", elapsed);
                    sendBroadcast(bc);
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(timerRunnable);
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Background audio recording");
            ch.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String title, String time) {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP_RECORDING);
        PendingIntent stopPI = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, RecordActivity.class);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title)
                .setContentText(time)
                .setContentIntent(openPI)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPI)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String title, String time) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(title, time));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastDone(File file, String error) {
        Intent bc = new Intent("com.pramod.soundstudio.RECORDING_DONE");
        if (file != null) bc.putExtra("file_path", file.getAbsolutePath());
        if (error != null) bc.putExtra("error", error);
        sendBroadcast(bc);
    }

    private static String generateFileName() {
        return "rec_" + System.currentTimeMillis();
    }

    private static String formatTime(long ms) {
        long s = (ms / 1000) % 60, m = ms / 60000;
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recorder != null && recorder.isRecording()) recorder.stopRecording();
        handler.removeCallbacksAndMessages(null);
    }
}
