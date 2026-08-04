package com.pramod.audioeditor.engine;

import com.pramod.audioeditor.data.EqSettings;
import com.pramod.audioeditor.data.PcmSource;

import java.io.IOException;

/**
 * Offline render engine: processes audio through EQ + limiter in chunks.
 */
public class OfflineRenderEngine {

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public static interface Sink {
        void write(float[] buf, int frames, int channels) throws IOException;
    }

    private static final int CHUNK = 4096;

    /**
     * Render a region through EQ + limiter into a sink.
     */
    public static void render(PcmSource src, long fromFrame, long toFrame,
                              EqSettings eq, LimiterDsp limiter,
                              Sink sink, ProgressListener progress) throws IOException {
        RealtimeEq realtimeEq = new RealtimeEq();
        realtimeEq.setSettings(eq);

        float[] buf = new float[CHUNK * src.channels()];
        long total = toFrame - fromFrame;

        for (long pos = fromFrame; pos < toFrame; ) {
            int count = (int) Math.min(CHUNK, toFrame - pos);
            src.readFrames(pos, count, buf, 0, false);

            realtimeEq.process(buf, count, src.channels());
            if (limiter != null) limiter.process(buf, count, src.channels());

            sink.write(buf, count, src.channels());
            pos += count;

            if (progress != null) {
                int pct = (int)((pos - fromFrame) * 100 / total);
                progress.onProgress(Math.min(99, pct));
            }
        }
        if (progress != null) progress.onProgress(100);
    }
}
