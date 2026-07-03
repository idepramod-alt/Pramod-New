#include <jni.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <mutex>

#define TAG "LoopmidiAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const int MAX_PADS   = 16;
static const int NUM_VOICES = 8;

struct PadBuffer {
    std::vector<short> pcm;
    bool loaded = false;
};

struct Voice {
    SLObjectItf playerObj = nullptr;
    SLPlayItf   play      = nullptr;
    SLAndroidSimpleBufferQueueItf bq  = nullptr;
    SLVolumeItf vol       = nullptr;
    int  padIndex = -1;
};

struct AudioEngineImpl {
    SLObjectItf engineObj    = nullptr;
    SLEngineItf engine       = nullptr;
    SLObjectItf outputMixObj = nullptr;
    PadBuffer   pads[MAX_PADS];
    Voice       voices[NUM_VOICES];
    int         nextVoice = 0;
    std::mutex  mtx;

    bool createVoice(Voice& v) {
        SLDataLocator_AndroidSimpleBufferQueue bqLoc = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
        };
        SLDataFormat_PCM fmt = {
            SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_44_1,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN
        };
        SLDataSource src  = { &bqLoc, &fmt };
        SLDataLocator_OutputMix omLoc = { SL_DATALOCATOR_OUTPUTMIX, outputMixObj };
        SLDataSink   sink = { &omLoc, nullptr };

        const SLInterfaceID iids[] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_VOLUME };
        const SLboolean     reqs[] = { SL_BOOLEAN_TRUE, SL_BOOLEAN_FALSE };

        SLresult res = (*engine)->CreateAudioPlayer(engine, &v.playerObj, &src, &sink, 2, iids, reqs);
        if (res != SL_RESULT_SUCCESS) { LOGE("CreateAudioPlayer failed: %d", (int)res); return false; }
        if ((*v.playerObj)->Realize(v.playerObj, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) {
            LOGE("Player Realize failed"); return false;
        }
        (*v.playerObj)->GetInterface(v.playerObj, SL_IID_PLAY,                    &v.play);
        (*v.playerObj)->GetInterface(v.playerObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &v.bq);
        (*v.playerObj)->GetInterface(v.playerObj, SL_IID_VOLUME,                  &v.vol);
        return (v.play != nullptr && v.bq != nullptr);
    }

    bool init() {
        SLresult res = slCreateEngine(&engineObj, 0, nullptr, 0, nullptr, nullptr);
        if (res != SL_RESULT_SUCCESS) { LOGE("slCreateEngine failed %d", (int)res); return false; }
        (*engineObj)->Realize(engineObj, SL_BOOLEAN_FALSE);
        (*engineObj)->GetInterface(engineObj, SL_IID_ENGINE, &engine);

        (*engine)->CreateOutputMix(engine, &outputMixObj, 0, nullptr, nullptr);
        (*outputMixObj)->Realize(outputMixObj, SL_BOOLEAN_FALSE);

        int created = 0;
        for (auto& v : voices) { if (createVoice(v)) created++; }
        LOGI("AudioEngine init: %d/%d voices ready", created, NUM_VOICES);
        return created > 0;
    }

    void loadSample(int padIdx, const short* data, int len) {
        if (padIdx < 0 || padIdx >= MAX_PADS || !data || len <= 0) return;
        std::lock_guard<std::mutex> lk(mtx);
        pads[padIdx].pcm.assign(data, data + len);
        pads[padIdx].loaded = true;
        LOGI("Loaded pad %d: %d samples", padIdx, len);
    }

    void playSample(int padIdx, float volume) {
        if (padIdx < 0 || padIdx >= MAX_PADS) return;
        std::lock_guard<std::mutex> lk(mtx);
        PadBuffer& pb = pads[padIdx];
        if (!pb.loaded || pb.pcm.empty()) { LOGI("Pad %d not loaded", padIdx); return; }

        Voice& v = voices[nextVoice % NUM_VOICES];
        nextVoice++;
        if (!v.play || !v.bq) return;

        (*v.play)->SetPlayState(v.play, SL_PLAYSTATE_STOPPED);
        (*v.bq)->Clear(v.bq);

        if (v.vol) {
            float c = (volume < 0.001f) ? 0.001f : (volume > 1.f ? 1.f : volume);
            SLmillibel mb = (SLmillibel)(2000.f * log10f(c));
            (*v.vol)->SetVolumeLevel(v.vol, mb);
        }
        (*v.bq)->Enqueue(v.bq, pb.pcm.data(), (SLuint32)(pb.pcm.size() * sizeof(short)));
        (*v.play)->SetPlayState(v.play, SL_PLAYSTATE_PLAYING);
        v.padIndex = padIdx;
        LOGI("Play pad %d vol=%.2f", padIdx, volume);
    }

    void stopPad(int padIdx) {
        std::lock_guard<std::mutex> lk(mtx);
        for (auto& v : voices) {
            if (v.padIndex == padIdx && v.play)
                (*v.play)->SetPlayState(v.play, SL_PLAYSTATE_STOPPED);
        }
    }

    void stopAll() {
        std::lock_guard<std::mutex> lk(mtx);
        for (auto& v : voices) {
            if (v.play) (*v.play)->SetPlayState(v.play, SL_PLAYSTATE_STOPPED);
        }
    }

    ~AudioEngineImpl() {
        for (auto& v : voices)
            if (v.playerObj) (*v.playerObj)->Destroy(v.playerObj);
        if (outputMixObj) (*outputMixObj)->Destroy(outputMixObj);
        if (engineObj)    (*engineObj)->Destroy(engineObj);
    }
};

// Helper: read nativeHandle field from the Java AudioEngine object
static AudioEngineImpl* getEngine(JNIEnv* env, jobject obj) {
    jclass  cls = env->GetObjectClass(obj);
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
    AudioEngineImpl* e = getEngine(env, obj);
    delete e;
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
