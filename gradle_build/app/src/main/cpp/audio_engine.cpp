#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <mutex>
#include <algorithm>

#define TAG  "LoopmidiOboe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const int MAX_PADS   = 16;
static const int NUM_VOICES = 16;  // 16 simultaneous voices

struct PadBuffer {
    std::vector<float> pcm;   // float mono @ 44100 Hz
    bool loaded    = false;
    int  chokeGroup = 0;
};

struct Voice {
    int    padIndex   = -1;
    size_t position   = 0;
    float  pitchAcc   = 0.f;  // fractional position accumulator for pitch shift
    float  volume     = 1.f;
    float  pitch      = 1.f;  // playback rate
    bool   active     = false;
    int    chokeGroup = 0;
    // Envelope
    float  envGain    = 1.f;
    float  attackRate = 0.f;
    float  releaseRate= 0.f;
    bool   releasing  = false;
    // Delay
    bool   delayOn    = false;
    float  delayLevel = 0.f;
    int    delayOffset= 0;
};

static const int DELAY_BUF_SIZE = 88200;  // 2 seconds @ 44100
static float gDelayBuf[DELAY_BUF_SIZE];
static int   gDelayWrite = 0;

class AudioEngineImpl : public oboe::AudioStreamCallback {
public:
    PadBuffer pads[MAX_PADS];
    Voice     voices[NUM_VOICES];
    int       nextVoice = 0;
    std::mutex mtx;
    std::shared_ptr<oboe::AudioStream> stream;

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* /*s*/,
            void*   audioData,
            int32_t numFrames) override {

        float* out = static_cast<float*>(audioData);
        memset(out, 0, sizeof(float) * numFrames);

        if (!mtx.try_lock()) return oboe::DataCallbackResult::Continue;

        for (auto& v : voices) {
            if (!v.active || v.padIndex < 0 || v.padIndex >= MAX_PADS) continue;
            PadBuffer& pb = pads[v.padIndex];
            if (!pb.loaded || pb.pcm.empty()) continue;

            for (int i = 0; i < numFrames; i++) {
                // Fractional pitch interpolation
                float fpos = (float)v.position + v.pitchAcc;
                int   ipos = (int)fpos;
                float frac = fpos - ipos;

                if ((size_t)ipos >= pb.pcm.size()) { v.active = false; break; }

                float s0   = pb.pcm[ipos];
                float s1   = ((size_t)(ipos + 1) < pb.pcm.size()) ? pb.pcm[ipos + 1] : 0.f;
                float samp = s0 + frac * (s1 - s0);

                // Envelope
                if (!v.releasing) {
                    v.envGain = std::min(1.f, v.envGain + v.attackRate);
                } else {
                    v.envGain -= v.releaseRate;
                    if (v.envGain <= 0.f) { v.active = false; break; }
                }
                samp *= v.volume * v.envGain;

                // Delay tap
                if (v.delayOn && v.delayOffset > 0) {
                    int ri = ((gDelayWrite + i - v.delayOffset) % DELAY_BUF_SIZE + DELAY_BUF_SIZE) % DELAY_BUF_SIZE;
                    samp += gDelayBuf[ri] * v.delayLevel;
                }

                out[i] += samp;

                // Advance position with pitch
                v.pitchAcc += v.pitch - 1.f;
                int extra = (int)v.pitchAcc;
                v.pitchAcc -= extra;
                v.position += 1 + extra;
                if (v.position >= pb.pcm.size()) { v.active = false; break; }
            }
        }

        // Write to delay buffer
        for (int i = 0; i < numFrames; i++)
            gDelayBuf[(gDelayWrite + i) % DELAY_BUF_SIZE] = out[i];
        gDelayWrite = (gDelayWrite + numFrames) % DELAY_BUF_SIZE;

        mtx.unlock();

        // Hard clip
        for (int i = 0; i < numFrames; i++) {
            if      (out[i] >  1.f) out[i] =  1.f;
            else if (out[i] < -1.f) out[i] = -1.f;
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream* /*s*/, oboe::Result r) override {
        LOGE("Oboe error: %s — restarting", oboe::convertToText(r));
        init();
    }

    bool init() {
        if (stream) { stream->stop(); stream->close(); stream.reset(); }
        memset(gDelayBuf, 0, sizeof(gDelayBuf));

        oboe::AudioStreamBuilder b;
        oboe::Result r = b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setSampleRate(44100)
            ->setDataCallback(this)
            ->openStream(stream);
        if (r != oboe::Result::OK) { LOGE("openStream: %s", oboe::convertToText(r)); return false; }
        r = stream->start();
        if (r != oboe::Result::OK) { LOGE("start: %s", oboe::convertToText(r)); return false; }
        LOGI("Oboe OK — rate=%d burst=%d api=%s",
             stream->getSampleRate(), stream->getFramesPerBurst(),
             oboe::convertToText(stream->getAudioApi()));
        return true;
    }

    void loadSample(int padIdx, const short* data, int len) {
        if (padIdx < 0 || padIdx >= MAX_PADS || !data || len <= 0) return;
        std::vector<float> buf(len);
        for (int i = 0; i < len; i++)
            buf[i] = data[i] / 32768.0f;
        std::lock_guard<std::mutex> lk(mtx);
        pads[padIdx].pcm    = std::move(buf);
        pads[padIdx].loaded = true;
        LOGI("Loaded pad %d: %d samples", padIdx, len);
    }

    void playSample(int padIdx, float volume, float pitch,
                    bool delayOn, float delayMs, float delayLevel,
                    float /*eqLow*/, float /*eqMid*/, float /*eqHigh*/,
                    int chokeGroup, float attackMs, float releaseMs) {
        if (padIdx < 0 || padIdx >= MAX_PADS) return;
        std::lock_guard<std::mutex> lk(mtx);
        if (!pads[padIdx].loaded) { LOGI("Pad %d not loaded", padIdx); return; }

        // Choke same group
        if (chokeGroup > 0)
            for (auto& ov : voices)
                if (ov.active && ov.chokeGroup == chokeGroup) ov.active = false;

        Voice& v      = voices[nextVoice % NUM_VOICES];
        nextVoice     = (nextVoice + 1) % NUM_VOICES;
        v.padIndex    = padIdx;
        v.position    = 0;
        v.pitchAcc    = 0.f;
        v.volume      = std::max(0.f, std::min(1.f, volume));
        v.pitch       = std::max(0.25f, std::min(4.f, pitch));
        v.chokeGroup  = chokeGroup;
        v.delayOn     = delayOn;
        v.delayLevel  = std::max(0.f, std::min(1.f, delayLevel));
        v.delayOffset = delayOn ? (int)(delayMs * 44.1f) : 0;
        if (v.delayOffset >= DELAY_BUF_SIZE) v.delayOffset = DELAY_BUF_SIZE - 1;

        const float sr = 44100.f;
        v.attackRate   = (attackMs  > 0.f) ? (1.f / (attackMs  * sr / 1000.f)) : 1.f;
        v.releaseRate  = (releaseMs > 0.f) ? (1.f / (releaseMs * sr / 1000.f)) : 0.f;
        v.envGain      = (attackMs  > 0.f) ? 0.f : 1.f;
        v.releasing    = false;
        v.active       = true;
        LOGI("Play pad=%d vol=%.2f pitch=%.2f delay=%s choke=%d",
             padIdx, volume, pitch, delayOn ? "on" : "off", chokeGroup);
    }

    void stopPad(int padIdx) {
        std::lock_guard<std::mutex> lk(mtx);
        for (auto& v : voices)
            if (v.active && v.padIndex == padIdx) v.active = false;
    }

    void stopAll() {
        std::lock_guard<std::mutex> lk(mtx);
        for (auto& v : voices) v.active = false;
    }

    ~AudioEngineImpl() {
        if (stream) { stream->stop(); stream->close(); }
    }
};

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
        jint padIdx, jfloat volume, jfloat pitch,
        jboolean delayOn, jfloat delayMs, jfloat delayLevel,
        jfloat eqLow, jfloat eqMid, jfloat eqHigh,
        jint chokeGroup, jfloat attackMs, jfloat releaseMs) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playSample((int)padIdx, (float)volume, (float)pitch,
                         (bool)delayOn, (float)delayMs, (float)delayLevel,
                         (float)eqLow, (float)eqMid, (float)eqHigh,
                         (int)chokeGroup, (float)attackMs, (float)releaseMs);
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
