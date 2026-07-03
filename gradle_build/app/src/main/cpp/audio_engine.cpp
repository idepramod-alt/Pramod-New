#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <mutex>

#define TAG "LoopmidiOboe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const int MAX_PADS   = 16;
static const int NUM_VOICES = 8;

struct PadBuffer {
    std::vector<float> pcm;  // 32-bit float mono @ 44100 Hz
    bool loaded = false;
};

struct Voice {
    int    padIndex = -1;
    size_t position = 0;
    float  volume   = 1.f;
    bool   active   = false;
};

// ─── Main engine — inherits Oboe callback ────────────────────────────────────
class AudioEngineImpl : public oboe::AudioStreamCallback {
public:
    PadBuffer pads[MAX_PADS];
    Voice     voices[NUM_VOICES];
    int       nextVoice = 0;
    std::mutex mtx;
    std::shared_ptr<oboe::AudioStream> stream;

    // Called by audio thread every buffer period (~2-5 ms in LowLatency mode)
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* /*s*/,
            void*    audioData,
            int32_t  numFrames) override {

        float* out = static_cast<float*>(audioData);
        memset(out, 0, sizeof(float) * numFrames);

        // try_lock: never block the realtime audio thread
        if (!mtx.try_lock()) return oboe::DataCallbackResult::Continue;

        for (auto& v : voices) {
            if (!v.active || v.padIndex < 0 || v.padIndex >= MAX_PADS) continue;
            PadBuffer& pb = pads[v.padIndex];
            if (!pb.loaded || pb.pcm.empty()) continue;

            for (int i = 0; i < numFrames; i++) {
                if (v.position >= pb.pcm.size()) { v.active = false; break; }
                out[i] += pb.pcm[v.position++] * v.volume;
            }
        }
        mtx.unlock();

        // Soft clip to prevent distortion when voices overlap
        for (int i = 0; i < numFrames; i++) {
            if      (out[i] >  1.f) out[i] =  1.f;
            else if (out[i] < -1.f) out[i] = -1.f;
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream* /*s*/, oboe::Result r) override {
        LOGE("Oboe stream error: %s — attempting restart", oboe::convertToText(r));
        init();  // auto-restart on audio focus loss / device change
    }

    bool init() {
        if (stream) { stream->stop(); stream->close(); stream.reset(); }

        // Oboe 1.6+: builder methods return AudioStreamBuilder* — use -> chaining
        oboe::AudioStreamBuilder b;
        oboe::Result r = b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setSampleRate(44100)
            ->setDataCallback(this)
            ->openStream(stream);
        if (r != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(r));
            return false;
        }
        r = stream->start();
        if (r != oboe::Result::OK) {
            LOGE("start failed: %s", oboe::convertToText(r));
            return false;
        }
        LOGI("Oboe stream OK — rate=%d  bufSize=%d  api=%s",
             stream->getSampleRate(),
             stream->getFramesPerBurst(),
             oboe::convertToText(stream->getAudioApi()));   // AAudio or OpenSLES
        return true;
    }

    void loadSample(int padIdx, const short* data, int len) {
        if (padIdx < 0 || padIdx >= MAX_PADS || !data || len <= 0) return;
        std::vector<float> buf(len);
        for (int i = 0; i < len; i++)
            buf[i] = data[i] / 32768.0f;   // int16 → float32

        std::lock_guard<std::mutex> lk(mtx);
        pads[padIdx].pcm    = std::move(buf);
        pads[padIdx].loaded = true;
        LOGI("Loaded pad %d: %d samples", padIdx, len);
    }

    void playSample(int padIdx, float volume) {
        if (padIdx < 0 || padIdx >= MAX_PADS) return;
        std::lock_guard<std::mutex> lk(mtx);
        if (!pads[padIdx].loaded) { LOGI("Pad %d not loaded", padIdx); return; }

        Voice& v   = voices[nextVoice % NUM_VOICES];
        nextVoice++;
        v.padIndex = padIdx;
        v.position = 0;
        v.volume   = (volume < 0.f ? 0.f : volume > 1.f ? 1.f : volume);
        v.active   = true;
        LOGI("Play pad %d vol=%.2f", padIdx, volume);
    }

    void stopPad(int padIdx) {
        std::lock_guard<std::mutex> lk(mtx);
        for (auto& v : voices)
            if (v.padIndex == padIdx) v.active = false;
    }

    void stopAll() {
        std::lock_guard<std::mutex> lk(mtx);
        for (auto& v : voices) v.active = false;
    }

    ~AudioEngineImpl() {
        if (stream) { stream->stop(); stream->close(); }
    }
};

// ─── JNI helpers ─────────────────────────────────────────────────────────────
static AudioEngineImpl* getEngine(JNIEnv* env, jobject obj) {
    jclass   cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, "nativeHandle", "J");
    if (!fid) return nullptr;
    return reinterpret_cast<AudioEngineImpl*>(env->GetLongField(obj, fid));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeCreateAudioEngine(JNIEnv*, jobject) {
    auto* e = new AudioEngineImpl();
    if (!e->init()) { delete e; return 0L; }
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeDestroyAudioEngine(JNIEnv* env, jobject obj) {
    delete getEngine(env, obj);
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeLoadSample(
        JNIEnv* env, jobject obj, jint padIdx, jshortArray arr, jint len) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (!e) return;
    jshort* data = env->GetShortArrayElements(arr, nullptr);
    e->loadSample((int)padIdx, (const short*)data, (int)len);
    env->ReleaseShortArrayElements(arr, data, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativePlaySample(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat /*pitch*/,
        jboolean /*delay*/, jfloat /*delayMs*/, jfloat /*delayLevel*/,
        jfloat /*eqLow*/, jfloat /*eqMid*/, jfloat /*eqHigh*/,
        jint /*chokeGroup*/, jfloat /*attackMs*/, jfloat /*releaseMs*/) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playSample((int)padIdx, (float)volume);
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeStopAll(JNIEnv* env, jobject obj) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->stopAll();
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeStopPad(JNIEnv* env, jobject obj, jint padIdx) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->stopPad((int)padIdx);
}

} // extern "C"
