package com.pramod.audioeditor.engine;

import com.pramod.audioeditor.data.EditModel;
import com.pramod.audioeditor.data.ExportSettings;
import com.pramod.audioeditor.data.PcmSource;
import com.pramod.audioeditor.data.WavFileWriter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Export orchestrator: renders EQ-processed audio to WAV or MP3.
 */
public class ExportEngine {

    public interface ProgressListener {
        void onProgress(int percent);
        void onComplete(File outputFile);
        void onError(String message);
    }

    public static File export(EditModel model, PcmSource src, ExportSettings settings,
                              File cacheDir, com.pramod.audioeditor.data.EqSettings eq,
                              ProgressListener listener) throws IOException {
        long from = settings.exportSelection ? model.activeStart() : 0;
        long to = settings.exportSelection ? model.activeEnd() : src.totalFrames();

        File outputFile;
        if (settings.format == ExportSettings.Format.MP3) {
            outputFile = new File(cacheDir, "export_" + System.currentTimeMillis() + ".mp3");
            exportMp3(src, from, to, settings, eq, outputFile, listener);
        } else {
            outputFile = new File(cacheDir, "export_" + System.currentTimeMillis() + ".wav");
            exportWav(src, from, to, settings, eq, outputFile, listener);
        }

        if (listener != null) listener.onComplete(outputFile);
        return outputFile;
    }

    private static void exportWav(PcmSource src, long from, long to, ExportSettings settings,
                                  com.pramod.audioeditor.data.EqSettings eq,
                                  File output, ProgressListener listener) throws IOException {
        WavFileWriter writer = new WavFileWriter();
        writer.open(output, settings.sampleRate, 2, settings.bitDepth);

        // For now, render at native sample rate then resample
        LimiterDsp limiter = new LimiterDsp();
        limiter.configure(-0.5f, 1f, 50f, src.sampleRate());

        OfflineRenderEngine.Sink sink = (buf, frames, channels) -> {
            // Simple resample if needed (linear interpolation)
            if (settings.sampleRate != src.sampleRate()) {
                float[] resampled = resample(buf, frames, channels, src.sampleRate(), settings.sampleRate);
                writer.write(resampled, 0, resampled.length);
            } else {
                writer.write(buf, 0, frames * channels);
            }
        };

        OfflineRenderEngine.render(src, from, to, eq, limiter, sink, listener);
        writer.close();
    }

    private static void exportMp3(PcmSource src, long from, long to, ExportSettings settings,
                                  com.pramod.audioeditor.data.EqSettings eq,
                                  File output, ProgressListener listener) throws IOException {
        // MP3 export — try to use jump3r via reflection if available,
        // otherwise fall back to WAV export
        try {
            Class.forName("de.sciss.jump3r.LameEncoder");

            File tempWav = new File(output.getParentFile(), "temp_encode.wav");
            exportWav(src, from, to, settings, eq, tempWav, null);

            // Use reflection to avoid compile-time dependency on jump3r
            Class<?> qualClass = Class.forName("de.sciss.jump3r.Quality");
            Object quality = qualClass.getField("QUALITY_HIGH").get(null);

            Class<?> encClass = Class.forName("de.sciss.jump3r.LameEncoder");
            Object encoder = encClass.getConstructor(
                    java.io.InputStream.class, java.io.OutputStream.class,
                    int.class, int.class, int.class, qualClass)
                    .newInstance(
                            new java.io.FileInputStream(tempWav),
                            new java.io.FileOutputStream(output),
                            settings.sampleRate, 2, settings.mp3Bitrate, quality);

            java.lang.reflect.Method getBufSize = encClass.getMethod("getBufferSize");
            java.lang.reflect.Method encode = encClass.getMethod("encodeBuffer", byte[].class);
            java.lang.reflect.Method closeEnc = encClass.getMethod("close");

            byte[] buffer = new byte[(int) getBufSize.invoke(encoder)];
            int totalBytes = (int) tempWav.length();
            int written = 0;

            while (true) {
                int bytesRead = (int) encode.invoke(encoder, buffer);
                if (bytesRead <= 0) break;
                written += bytesRead;
                if (listener != null) {
                    int pct = (int)((long)written * 90 / totalBytes);
                    listener.onProgress(Math.min(90, pct));
                }
            }
            closeEnc.invoke(encoder);
            tempWav.delete();
            if (listener != null) listener.onProgress(100);
        } catch (ClassNotFoundException e) {
            // jump3r not available — fall back to WAV with .wav extension
            File wavOutput = new File(output.getParentFile(),
                    output.getName().replace(".mp3", ".wav"));
            exportWav(src, from, to, settings, eq, wavOutput, listener);
            if (listener != null) {
                listener.onProgress(100);
                listener.onComplete(wavOutput);
            }
        } catch (Exception e) {
            throw new IOException("MP3 encoding failed: " + e.getMessage());
        }
    }

    /** Simple linear interpolation resample. */
    private static float[] resample(float[] input, int frames, int channels, int fromRate, int toRate) {
        double ratio = (double) fromRate / toRate;
        int outFrames = (int)(frames / ratio);
        float[] output = new float[outFrames * channels];
        for (int i = 0; i < outFrames; i++) {
            double srcPos = i * ratio;
            int idx = (int) srcPos;
            double frac = srcPos - idx;
            for (int ch = 0; ch < channels; ch++) {
                float a = input[idx * channels + ch];
                float b = (idx + 1 < frames) ? input[(idx + 1) * channels + ch] : a;
                output[i * channels + ch] = (float)(a + frac * (b - a));
            }
        }
        return output;
    }
}
