package com.pramod.audioeditor.engine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import com.pramod.audioeditor.data.PcmSource;

/**
 * Streaming audio playback engine with EQ processing.
 * Reads from PcmSource, applies RealtimeEq + LimiterDsp, outputs to AudioTrack.
 */
public class PlaybackEngine {

    private PcmSource source;
    private AudioTrack audioTrack;
    private RealtimeEq eq;
    private LimiterDsp limiter;
    private Thread playThread;
    private volatile boolean playing = false;
    private volatile boolean stopped = false;
    private long playStartFrame;
    private long playEndFrame;
    private long currentFrame;
    private Listener listener;

    private static final int CHUNK_FRAMES = 2048;

    public interface Listener {
        void onPositionUpdate(long frame);
        void onPlaybackComplete();
    }

    public void setListener(Listener l) { this.listener = l; }
    public void setEq(RealtimeEq eq) { this.eq = eq; }
    public void setLimiter(LimiterDsp limiter) { this.limiter = limiter; }

    public void configure(PcmSource src) {
        this.source = src;
        int channelConfig = src.channels() == 2
                ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int bufSize = AudioTrack.getMinBufferSize(
                src.sampleRate(), channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        bufSize = Math.max(bufSize, CHUNK_FRAMES * src.channels() * 2);

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(src.sampleRate())
                        .setChannelMask(channelConfig)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
    }

    public void play(long fromFrame, long toFrame) {
        if (audioTrack == null || source == null) return;
        stop();
        this.playStartFrame = fromFrame;
        this.playEndFrame = toFrame;
        this.currentFrame = fromFrame;
        this.stopped = false;
        this.playing = true;

        audioTrack.play();
        playThread = new Thread(this::playLoop, "PlaybackEngine");
        playThread.start();
    }

    public void pause() {
        playing = false;
        if (audioTrack != null) try { audioTrack.pause(); } catch (Exception ignored) {}
    }

    public void resume() {
        if (audioTrack != null && !stopped) {
            playing = true;
            audioTrack.play();
            if (playThread == null || !playThread.isAlive()) {
                playThread = new Thread(this::playLoop, "PlaybackEngine");
                playThread.start();
            }
        }
    }

    public void stop() {
        stopped = true;
        playing = false;
        if (audioTrack != null) {
            try { audioTrack.stop(); } catch (Exception ignored) {}
        }
        if (playThread != null) {
            playThread.interrupt();
            try { playThread.join(100); } catch (InterruptedException ignored) {}
            playThread = null;
        }
    }

    public long getCurrentFrame() { return currentFrame; }
    public boolean isPlaying() { return playing; }

    public void seek(long frame) {
        this.currentFrame = Math.max(0, Math.min(frame, source != null ? source.totalFrames() : 0));
    }

    private void playLoop() {
        float[] buf = new float[CHUNK_FRAMES * (source.channels() == 2 ? 2 : 1)];
        short[] out = new short[CHUNK_FRAMES * (source.channels() == 2 ? 2 : 1)];

        while (!stopped && playing && currentFrame < playEndFrame) {
            int framesToRead = (int) Math.min(CHUNK_FRAMES, playEndFrame - currentFrame);
            source.readFrames(currentFrame, framesToRead, buf, 0, false);

            // Apply EQ
            if (eq != null) {
                eq.process(buf, framesToRead, source.channels());
            }

            // Apply limiter
            if (limiter != null) {
                limiter.process(buf, framesToRead, source.channels());
            }

            // Convert float to short
            int totalSamples = framesToRead * source.channels();
            for (int i = 0; i < totalSamples; i++) {
                float s = Math.max(-1f, Math.min(1f, buf[i]));
                out[i] = (short)(s * 32767f);
            }

            audioTrack.write(out, 0, totalSamples);
            currentFrame += framesToRead;

            if (listener != null) {
                final long pos = currentFrame;
                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> listener.onPositionUpdate(pos));
            }
        }

        playing = false;
        if (listener != null) {
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(listener::onPlaybackComplete);
        }
    }

    public void release() {
        stop();
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
    }
}
