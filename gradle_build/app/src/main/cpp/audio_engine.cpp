#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <atomic>
#include <algorithm>

#define TAG  "LoopmidiOboe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─── Constants ────────────────────────────────────────────────────────────────
static const int MAX_PADS        = 16;
static const int LOOP_VOICES     = 8;   // voices 0-7  : loop playback (long-running)
static const int DRUM_VOICES     = 16;  // voices 8-23 : drum/pad hits  (short, stackable)
static const int NUM_VOICES      = LOOP_VOICES + DRUM_VOICES;
static const int DELAY_BUF_SIZE  = 88200;  // 2 sec @ 44100
static const int CMD_QUEUE_SIZE  = 128;    // lock-free ring buffer capacity

// ─── Pad buffer (loaded samples) ─────────────────────────────────────────────
struct PadBuffer {
    std::vector<float> pcm;
    std::atomic<bool>  loaded{false};
    int                chokeGroup = 0;
};

// ─── Voice (audio-thread only after activation) ───────────────────────────────
struct Voice {
    std::atomic<bool> active{false};
    int     padIndex   = -1;
    size_t  position   = 0;
    float   pitchAcc   = 0.f;
    std::atomic<float> volume{1.f};
    std::atomic<float> pitch{1.f};
    int     chokeGroup = 0;
    bool    isLoop     = false;   // loop voices survive choke; drum voices don't
    // Envelope
    float   envGain    = 1.f;
    float   attackRate = 0.f;
    float   releaseRate= 0.f;
    bool    releasing  = false;
    // Delay
    bool    delayOn    = false;
    float   delayLevel = 0.f;
    int     delayOffset= 0;
};

// ─── Lock-free SPSC command queue ────────────────────────────────────────────
enum CmdType { CMD_PLAY, CMD_STOP_PAD, CMD_STOP_ALL, CMD_UPDATE_PITCH };

struct Cmd {
    CmdType type;
    int     padIdx;
    float   volume;
    float   pitch;
    bool    delayOn;
    float   delayMs;
    float   delayLevel;
    int     chokeGroup;
    float   attackMs;
    float   releaseMs;
    bool    isLoop;
};

struct CmdQueue {
    Cmd              buf[CMD_QUEUE_SIZE];
    std::atomic<int> head{0};
    std::atomic<int> tail{0};

    bool push(const Cmd& c) {
        int t    = tail.load(std::memory_order_relaxed);
        int next = (t + 1) % CMD_QUEUE_SIZE;
        if (next == head.load(std::memory_order_acquire)) return false;
        buf[t] = c;
        tail.store(next, std::memory_order_release);
        return true;
    }
    bool pop(Cmd& c) {
        int h = head.load(std::memory_order_relaxed);
        if (h == tail.load(std::memory_order_acquire)) return false;
        c = buf[h];
        head.store((h + 1) % CMD_QUEUE_SIZE, std::memory_order_release);
        return true;
    }
};

// ─── Main engine ──────────────────────────────────────────────────────────────
class AudioEngineImpl : public oboe::AudioStreamCallback {
public:
    PadBuffer pads[MAX_PADS];
    Voice     voices[NUM_VOICES];
    CmdQueue  cmdQ;
    int       nextDrumVoice = LOOP_VOICES;  // drums round-robin in 8..23
    std::shared_ptr<oboe::AudioStream> stream;

    float gDelayBuf[DELAY_BUF_SIZE];
    int   gDelayWrite = 0;

    // ── Audio callback (realtime thread — no malloc, no mutex, no blocking) ──
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream*, void* audioData, int32_t numFrames) override {

        float* out = static_cast<float*>(audioData);
        memset(out, 0, sizeof(float) * numFrames);

        // Process all pending commands (lock-free)
        Cmd c;
        while (cmdQ.pop(c)) processCmd(c);

        // Mix active voices
        for (auto& v : voices) {
            if (!v.active.load(std::memory_order_relaxed)) continue;
            int pi = v.padIndex;
            if (pi < 0 || pi >= MAX_PADS) continue;
            PadBuffer& pb = pads[pi];
            if (!pb.loaded.load(std::memory_order_acquire) || pb.pcm.empty()) continue;

            float vol   = v.volume.load(std::memory_order_relaxed);
            float pitch = v.pitch .load(std::memory_order_relaxed);

            for (int i = 0; i < numFrames; i++) {
                float fpos = (float)v.position + v.pitchAcc;
                int   ipos = (int)fpos;
                float frac = fpos - ipos;

                if ((size_t)ipos >= pb.pcm.size()) { v.active.store(false, std::memory_order_relaxed); break; }

                float s0   = pb.pcm[ipos];
                float s1   = ((size_t)(ipos+1) < pb.pcm.size()) ? pb.pcm[ipos+1] : 0.f;
                float samp = s0 + frac * (s1 - s0);

                // Envelope
                if (!v.releasing) {
                    v.envGain = std::min(1.f, v.envGain + v.attackRate);
                } else {
                    v.envGain -= v.releaseRate;
                    if (v.envGain <= 0.f) { v.active.store(false, std::memory_order_relaxed); break; }
                }
                samp *= vol * v.envGain;

                // Delay tap
                if (v.delayOn && v.delayOffset > 0) {
                    int ri = ((gDelayWrite + i - v.delayOffset) % DELAY_BUF_SIZE + DELAY_BUF_SIZE) % DELAY_BUF_SIZE;
                    samp += gDelayBuf[ri] * v.delayLevel;
                }

                out[i] += samp;

                // Advance position with pitch shift
                v.pitchAcc += pitch - 1.f;
                int extra = (int)v.pitchAcc;
                v.pitchAcc -= extra;
                v.position += 1 + extra;
                if (v.position >= pb.pcm.size()) { v.active.store(false, std::memory_order_relaxed); break; }
            }
        }

        // Write to delay buffer
        for (int i = 0; i < numFrames; i++)
            gDelayBuf[(gDelayWrite + i) % DELAY_BUF_SIZE] = out[i];
        gDelayWrite = (gDelayWrite + numFrames) % DELAY_BUF_SIZE;

        // Hard clip
        for (int i = 0; i < numFrames; i++) {
            if      (out[i] >  1.f) out[i] =  1.f;
            else if (out[i] < -1.f) out[i] = -1.f;
        }
        return oboe::DataCallbackResult::Continue;
    }

    // ── Process one command (called from audio thread) ────────────────────────
    void processCmd(const Cmd& c) {
        switch (c.type) {

        case CMD_PLAY: {
            // Choke: stop voices in same choke group (drum voices only)
            if (c.chokeGroup > 0) {
                for (auto& v : voices)
                    if (v.active.load() && v.chokeGroup == c.chokeGroup && !v.isLoop)
                        v.active.store(false, std::memory_order_relaxed);
            }

            int vi;
            if (c.isLoop) {
                // Loop: use fixed voice slot = padIdx (0-7), replace existing
                vi = c.padIdx % LOOP_VOICES;
            } else {
                // Drum: round-robin in drum pool (8-23), never steal loop voices
                vi = nextDrumVoice;
                nextDrumVoice = LOOP_VOICES + ((nextDrumVoice - LOOP_VOICES + 1) % DRUM_VOICES);
            }

            Voice& v      = voices[vi];
            v.active.store(false, std::memory_order_relaxed); // reset first
            v.padIndex    = c.padIdx;
            v.position    = 0;
            v.pitchAcc    = 0.f;
            v.volume.store(c.volume,  std::memory_order_relaxed);
            v.pitch .store(c.pitch,   std::memory_order_relaxed);
            v.chokeGroup  = c.chokeGroup;
            v.isLoop      = c.isLoop;
            v.delayOn     = c.delayOn;
            v.delayLevel  = c.delayLevel;
            v.delayOffset = c.delayOn ? (int)(c.delayMs * 44.1f) : 0;
            if (v.delayOffset >= DELAY_BUF_SIZE) v.delayOffset = DELAY_BUF_SIZE - 1;

            const float sr = 44100.f;
            v.attackRate   = (c.attackMs  > 0.f) ? (1.f / (c.attackMs  * sr / 1000.f)) : 1.f;
            v.releaseRate  = (c.releaseMs > 0.f) ? (1.f / (c.releaseMs * sr / 1000.f)) : 0.f;
            v.envGain      = (c.attackMs  > 0.f) ? 0.f : 1.f;
            v.releasing    = false;
            v.active.store(true, std::memory_order_release);
            break;
        }

        case CMD_STOP_PAD:
            for (auto& v : voices)
                if (v.active.load() && v.padIndex == c.padIdx)
                    v.active.store(false, std::memory_order_relaxed);
            break;

        case CMD_STOP_ALL:
            for (auto& v : voices)
                v.active.store(false, std::memory_order_relaxed);
            break;

        case CMD_UPDATE_PITCH:
            // Live pitch update for loop voice — no gap, no restart
            if (c.padIdx >= 0 && c.padIdx < LOOP_VOICES) {
                Voice& v = voices[c.padIdx];
                if (v.active.load()) {
                    v.pitch .store(c.pitch,  std::memory_order_relaxed);
                    v.volume.store(c.volume, std::memory_order_relaxed);
                }
            }
            break;
        }
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result r) override {
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
            ->setFramesPerCallback(128)          // ~3ms per callback — low latency
            ->setBufferCapacityInFrames(512)      // small buffer = low latency
            ->setDataCallback(this)
            ->openStream(stream);
        if (r != oboe::Result::OK) { LOGE("openStream: %s", oboe::convertToText(r)); return false; }
        stream->setBufferSizeInFrames(256);       // double-buffer: 256 frames ≈ 5.8ms
        r = stream->start();
        if (r != oboe::Result::OK) { LOGE("start: %s", oboe::convertToText(r)); return false; }
        LOGI("Oboe OK — rate=%d burst=%d bufSize=%d api=%s",
             stream->getSampleRate(), stream->getFramesPerBurst(),
             stream->getBufferSizeInFrames(),
             oboe::convertToText(stream->getAudioApi()));
        return true;
    }

    // Called from Java thread — lock-free push to queue
    void loadSample(int padIdx, const short* data, int len) {
        if (padIdx < 0 || padIdx >= MAX_PADS || !data || len <= 0) return;
        // Load into a temp vector first, then swap (minimize time with loaded=false)
        std::vector<float> buf(len);
        for (int i = 0; i < len; i++)
            buf[i] = data[i] / 32768.0f;
        pads[padIdx].loaded.store(false, std::memory_order_release);
        pads[padIdx].pcm = std::move(buf);
        pads[padIdx].loaded.store(true,  std::memory_order_release);
        LOGI("Loaded pad %d: %d samples", padIdx, len);
    }

    void playSample(int padIdx, float volume, float pitch,
                    bool delayOn, float delayMs, float delayLevel,
                    int chokeGroup, float attackMs, float releaseMs, bool isLoop) {
        if (padIdx < 0 || padIdx >= MAX_PADS) return;
        if (!pads[padIdx].loaded.load(std::memory_order_acquire)) {
            LOGI("Pad %d not loaded", padIdx); return;
        }
        Cmd c{};
        c.type       = CMD_PLAY;
        c.padIdx     = padIdx;
        c.volume     = std::max(0.f, std::min(1.f, volume));
        c.pitch      = std::max(0.1f, std::min(8.f, pitch));
        c.delayOn    = delayOn;
        c.delayMs    = delayMs;
        c.delayLevel = std::max(0.f, std::min(1.f, delayLevel));
        c.chokeGroup = chokeGroup;
        c.attackMs   = attackMs;
        c.releaseMs  = releaseMs;
        c.isLoop     = isLoop;
        cmdQ.push(c);
    }

    void updateLoopPitch(int padIdx, float volume, float pitch) {
        if (padIdx < 0 || padIdx >= LOOP_VOICES) return;
        Cmd c{};
        c.type   = CMD_UPDATE_PITCH;
        c.padIdx = padIdx;
        c.volume = std::max(0.f, std::min(1.f, volume));
        c.pitch  = std::max(0.1f, std::min(8.f, pitch));
        cmdQ.push(c);
    }

    void stopPad(int padIdx) {
        Cmd c{};
        c.type   = CMD_STOP_PAD;
        c.padIdx = padIdx;
        cmdQ.push(c);
    }

    void stopAll() {
        Cmd c{};
        c.type = CMD_STOP_ALL;
        cmdQ.push(c);
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
        jint padIdx, jfloat volume, jfloat pitch,
        jboolean delayOn, jfloat delayMs, jfloat delayLevel,
        jfloat /*eqLow*/, jfloat /*eqMid*/, jfloat /*eqHigh*/,
        jint chokeGroup, jfloat attackMs, jfloat releaseMs) {
    AudioEngineImpl* e = getEngine(env, obj);
    // Default: treat as drum hit (not loop) — loops use nativePlayLoop
    if (e) e->playSample((int)padIdx, (float)volume, (float)pitch,
                         (bool)delayOn, (float)delayMs, (float)delayLevel,
                         (int)chokeGroup, (float)attackMs, (float)releaseMs, false);
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativePlayLoop(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat pitch) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playSample((int)padIdx, (float)volume, (float)pitch,
                         false, 0.f, 0.f, 0, 0.f, 0.f, true);
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeUpdateLoopPitch(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat pitch) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->updateLoopPitch((int)padIdx, (float)volume, (float)pitch);
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
