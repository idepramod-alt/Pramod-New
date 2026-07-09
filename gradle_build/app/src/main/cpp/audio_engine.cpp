#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
extern "C" {
#include "sonic.h"
}
#include <cstring>
#include <cmath>
#include <vector>
#include <atomic>
#include <algorithm>
#include <mutex>
#include <thread>
#include <chrono>
#include <condition_variable>

#define TAG  "LoopmidiOboe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─── Constants ────────────────────────────────────────────────────────────────
static const int MAX_PADS        = 16;
static const int LOOP_VOICES     = 8;   // voices 0-7  : loop playback (long-running)
static const int DRUM_VOICES     = 16;  // voices 8-23 : drum/pad hits  (short, stackable)
static const int NUM_VOICES      = LOOP_VOICES + DRUM_VOICES;
// 4 seconds @ 96 kHz — safe for both 44100 and 48000 native rates
static const int DELAY_BUF_SIZE  = 192000;
static const int CMD_QUEUE_SIZE  = 128;    // lock-free ring buffer capacity
static const int SYN_HOP        = 256;   // OLA synthesis hop size
// Roland SPD-20 Pro-style delay: multiple decaying repeats instead of a single
// flat echo. delayLevel is now used as the per-repeat feedback amount — each
// successive repeat is quieter by that factor, same as a real delay pedal.
static const int   MAX_DELAY_REPEATS = 8;
static const float MAX_DELAY_FEEDBACK = 0.82f; // keeps the tail musical, never runs away
static const float MIN_DELAY_REPEAT_AMP = 0.01f; // stop once a repeat is inaudible

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
    float   pitchAcc   = 0.f;          // used only by drum voices
    std::atomic<float> volume{1.f};
    std::atomic<float> speed{1.f};     // playback speed — no pitch change (loops only)
    std::atomic<float> pitch{1.f};     // pitch shift — no speed change (loops only)
    int     chokeGroup = 0;
    bool    isLoop     = false;
    // Envelope
    float   envGain    = 1.f;
    float   attackRate = 0.f;
    float   releaseRate= 0.f;
    bool    releasing  = false;
    // Delay
    bool    delayOn    = false;
    float   delayLevel = 0.f;
    int     delayOffset= 0;
    // OLA granular synthesis state
    int   grainStartA = 0;
    int   grainStartB = 0;
    float synPhase    = 0.f;
};

// ─── Lock-free SPSC command queue ────────────────────────────────────────────
enum CmdType { CMD_PLAY, CMD_STOP_PAD, CMD_STOP_ALL, CMD_UPDATE_SPEED_PITCH };

struct Cmd {
    CmdType type;
    int     padIdx;
    float   volume;
    float   speed;    // time-stretch factor (1.0 = normal, 2.0 = 2x faster, no pitch change)
    float   pitch;    // pitch shift factor  (1.0 = normal, 2.0 = one octave up, no speed change)
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
    // This ring buffer is designed as lock-free SPSC (single-producer/single-consumer):
    // pop() runs only on the audio callback thread, which stays lock-free/wait-free as
    // required for real-time audio. push(), however, is now called from more than one
    // producer thread — the UI thread AND, since the MIDI-latency fix, the MIDI callback
    // thread firing playSample() directly for low-latency pad hits. Two producers racing
    // on the old lock-free push() (read tail, then later write tail) could interleave and
    // corrupt the queue (lost/garbled commands) under concurrent MIDI + UI activity. A
    // mutex here only serializes the rare, short push() calls (one per note-on/pad-tap) —
    // it never touches the real-time pop() path, so it doesn't reintroduce audio glitching.
    std::mutex       pushMutex;

    bool push(const Cmd& c) {
        std::lock_guard<std::mutex> lock(pushMutex);
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
    int       nextDrumVoice = LOOP_VOICES;
    std::shared_ptr<oboe::AudioStream> stream;
    // Bumped every time init() successfully (re)opens the stream. Used by the
    // ErrorDisconnected fallback-restart watchdog (see onErrorAfterClose) to
    // detect whether someone else (Java's AudioDeviceCallback path) already
    // healed the stream before the watchdog's delay elapses.
    std::atomic<uint64_t> streamGeneration{0};

    // Device-native audio parameters (set from Java via AudioManager queries)
    // Using native SR avoids Android's internal resampler and cuts ~20-40 ms latency
    int sampleRate    = 48000;
    int framesPerBurst = 256;

    float gDelayBuf[DELAY_BUF_SIZE];
    int   gDelayWrite = 0;

    // One Sonic stream per loop voice — handles pitch-preserving speed and vice-versa
    sonicStream loopSonic[LOOP_VOICES];
    float loopSonicLastSpeed[LOOP_VOICES];
    float loopSonicLastPitch[LOOP_VOICES];

    // scratch buffers (audio thread only — no malloc in callback)
    static const int SCRATCH_SIZE = 4096;
    float feedBuf[SCRATCH_SIZE];
    float readBuf[SCRATCH_SIZE];

    // ── Internal/system-audio recording (post-mix tap) ─────────────────────
    // Captures the engine's own mixed output (everything played through the
    // pads/loops) so it can be saved as a track without MediaProjection or
    // mic permission. The buffer is fully preallocated by startRecording()
    // (on the main/binder thread) so the realtime audio callback never
    // allocates memory — it only writes into pre-reserved slots via an
    // atomic write index.
    static const int RECORD_MAX_SECONDS = 300; // 5 minutes cap per take
    std::vector<float>   recordBuffer;
    std::atomic<size_t>  recordWritePos{0};
    std::atomic<bool>    recordActive{false};
    size_t               recordCapacity = 0;

    AudioEngineImpl() {
        memset(loopSonic, 0, sizeof(loopSonic));
        for (int i = 0; i < LOOP_VOICES; i++) {
            loopSonicLastSpeed[i] = 1.f;
            loopSonicLastPitch[i] = 1.f;
        }
    }

    // ── Audio callback (realtime thread — no malloc, no mutex, no blocking) ──
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream*, void* audioData, int32_t numFrames) override {

        float* out = static_cast<float*>(audioData);
        memset(out, 0, sizeof(float) * numFrames);

        // Process all pending commands (lock-free)
        Cmd c;
        while (cmdQ.pop(c)) processCmd(c);

        // Mix active voices
        for (int vi = 0; vi < NUM_VOICES; vi++) {
            Voice& v = voices[vi];
            if (!v.active.load(std::memory_order_relaxed)) continue;
            int pi = v.padIndex;
            if (pi < 0 || pi >= MAX_PADS) continue;
            PadBuffer& pb = pads[pi];
            if (!pb.loaded.load(std::memory_order_acquire) || pb.pcm.empty()) continue;

            float vol = v.volume.load(std::memory_order_relaxed);

            if (v.isLoop && vi < LOOP_VOICES && loopSonic[vi] != nullptr) {
                // ── Loop voice: use Sonic for independent speed + pitch ──────────
                sonicStream sonic = loopSonic[vi];

                // ── Smooth speed/pitch ramping ───────────────────────────────────
                // WHY: sonicSetSpeed/sonicSetPitch with a hard jump to the target
                // value causes Sonic's WSOLA internals to re-sync abruptly — the
                // first ~10ms of output after the jump contains audible artifacts
                // (the "crack" when dragging the seekbar).
                //
                // FIX: Each audio callback, move only 15% toward the target stored
                // by the Java side (v.speed / v.pitch atomics updated by CMD_UPDATE).
                // This spreads the transition over ~6 callbacks (~30ms), making each
                // individual sonicSet call a tiny step that Sonic's WSOLA handles
                // smoothly — completely inaudible instead of a sharp crack.
                //
                // CMD_UPDATE_SPEED_PITCH only writes the TARGET atomics; it does NOT
                // call sonicSetSpeed/Pitch directly. All Sonic param changes happen
                // here, gradually, on the audio thread, at a pace Sonic can handle.
                {
                    float targetSpd  = v.speed.load(std::memory_order_relaxed);
                    float targetPtch = v.pitch.load(std::memory_order_relaxed);
                    const float SMOOTH = 0.15f;
                    float newSpd  = loopSonicLastSpeed[vi] + SMOOTH * (targetSpd  - loopSonicLastSpeed[vi]);
                    float newPtch = loopSonicLastPitch[vi] + SMOOTH * (targetPtch - loopSonicLastPitch[vi]);
                    // Snap to target when very close — stops infinite micro-updates
                    if (fabsf(newSpd  - targetSpd)  < 0.002f) newSpd  = targetSpd;
                    if (fabsf(newPtch - targetPtch) < 0.002f) newPtch = targetPtch;
                    if (newSpd != loopSonicLastSpeed[vi]) {
                        sonicSetSpeed(sonic, newSpd);
                        loopSonicLastSpeed[vi] = newSpd;
                    }
                    if (newPtch != loopSonicLastPitch[vi]) {
                        sonicSetPitch(sonic, newPtch);
                        loopSonicLastPitch[vi] = newPtch;
                    }
                }

                float spd = loopSonicLastSpeed[vi];   // current (ramped) speed for feed calc

                // ── Sonic feeding: top-up to feedTarget level ───────────────────
                // feedTarget = numFrames*spd*3+512 (same as confirmed-working #103).
                // Only feed the DIFFERENCE (feedTarget - avail) to maintain this
                // level. Unconditional feeding would overfill Sonic's output ring
                // every callback → unbounded realloc growth on RT thread.
                // Crossfade the last XFADE input samples at the loop boundary
                // so the wrap-point click is inaudible (blends tail into head).
                static const int XFADE = 256;
                int feedTarget = (int)(numFrames * spd * 3.0f) + 512;
                if (feedTarget > SCRATCH_SIZE) feedTarget = SCRATCH_SIZE;
                int avail = sonicSamplesAvailable(sonic);
                if (avail < feedTarget) {
                    int toFeed = feedTarget - avail;
                    if (toFeed > SCRATCH_SIZE) toFeed = SCRATCH_SIZE;

                    int fed = 0;
                    size_t pcmSize = pb.pcm.size();
                    while (fed < toFeed) {
                        if (v.position >= pcmSize) v.position = 0;
                        size_t pos = v.position;
                        float s = pb.pcm[pos];
                        // Crossfade tail → head at loop boundary to eliminate wrap click
                        if (pcmSize > (size_t)(XFADE * 2) && pos >= pcmSize - (size_t)XFADE) {
                            size_t tailOff = pos - (pcmSize - XFADE); // 0 … XFADE-1
                            float t = (float)tailOff / (float)XFADE;  // 0.0 → 1.0
                            s = s * (1.0f - t) + pb.pcm[tailOff] * t; // blend tail→head
                        }
                        feedBuf[fed++] = s;
                        v.position++;
                    }
                    sonicWriteFloatToStream(sonic, feedBuf, fed);
                }

                // Read smooth-ramped output from Sonic
                int got = sonicReadFloatFromStream(sonic, readBuf,
                                                   numFrames < SCRATCH_SIZE ? numFrames : SCRATCH_SIZE);

                for (int i = 0; i < numFrames; i++) {
                    // Envelope
                    if (!v.releasing) {
                        v.envGain = std::min(1.f, v.envGain + v.attackRate);
                    } else {
                        v.envGain -= v.releaseRate;
                        if (v.envGain <= 0.f) {
                            v.active.store(false, std::memory_order_relaxed);
                            break;
                        }
                    }
                    out[i] += (i < got ? readBuf[i] : 0.f) * vol * v.envGain;
                }

            } else {
                // ── Drum/one-shot voice: linear-interpolation resampling ─────────
                // rate = speed × pitch: speed changes how fast the sample plays
                // (and therefore its duration), pitch shifts the frequency on top.
                // Linear resampling combines both effects into one step — classic
                // "tape speed" behaviour expected for one-shot pads.
                // Clamped to [0.1, 4.0] so Sonic never receives extreme values
                // that could cause integer overflow in the position accumulator.
                float rate = v.speed.load(std::memory_order_relaxed)
                           * v.pitch.load(std::memory_order_relaxed);
                if (rate < 0.1f) rate = 0.1f;
                if (rate > 4.0f) rate = 4.0f;

                for (int i = 0; i < numFrames; i++) {
                    float fpos = (float)v.position + v.pitchAcc;
                    int   ipos = (int)fpos;
                    float frac = fpos - ipos;

                    if ((size_t)ipos >= pb.pcm.size()) {
                        v.active.store(false, std::memory_order_relaxed);
                        break;
                    }

                    float s0   = pb.pcm[ipos];
                    float s1   = ((size_t)(ipos+1) < pb.pcm.size()) ? pb.pcm[ipos+1] : 0.f;
                    float samp = s0 + frac * (s1 - s0);

                    // Envelope
                    if (!v.releasing) {
                        v.envGain = std::min(1.f, v.envGain + v.attackRate);
                    } else {
                        v.envGain -= v.releaseRate;
                        if (v.envGain <= 0.f) {
                            v.active.store(false, std::memory_order_relaxed);
                            break;
                        }
                    }
                    samp *= vol * v.envGain;

                    // Delay tap — multi-repeat feedback echo (Roland SPD-20 Pro style):
                    // each repeat is delayOffset further back and quieter than the
                    // previous one by the feedback factor, instead of one static echo.
                    if (v.delayOn && v.delayOffset > 0) {
                        float amp = v.delayLevel;
                        for (int r = 1; r <= MAX_DELAY_REPEATS; r++) {
                            int offset = v.delayOffset * r;
                            if (offset >= DELAY_BUF_SIZE) break;
                            int ri = ((gDelayWrite + i - offset) % DELAY_BUF_SIZE + DELAY_BUF_SIZE) % DELAY_BUF_SIZE;
                            samp += gDelayBuf[ri] * amp;
                            amp *= v.delayLevel;
                            if (amp < MIN_DELAY_REPEAT_AMP) break;
                        }
                    }

                    out[i] += samp;

                    // Advance position by combined speed×pitch rate
                    v.pitchAcc += rate - 1.f;
                    int extra = (int)v.pitchAcc;
                    v.pitchAcc -= extra;
                    v.position += 1 + extra;
                    if (v.position >= pb.pcm.size()) {
                        v.active.store(false, std::memory_order_relaxed);
                        break;
                    }
                }
            }
        }

        // Write to delay buffer
        for (int i = 0; i < numFrames; i++)
            gDelayBuf[(gDelayWrite + i) % DELAY_BUF_SIZE] = out[i];
        gDelayWrite = (gDelayWrite + numFrames) % DELAY_BUF_SIZE;

        // Soft saturation (tanh): smoother than hard clip; avoids the harsh
        // click that hard clipping adds when transients exceed ±1.0.
        for (int i = 0; i < numFrames; i++) {
            out[i] = tanhf(out[i]);
        }

        // ── Internal/system-audio recording tap ─────────────────────────────
        // Runs AFTER saturation so the captured take matches exactly what the
        // listener hears. recordBuffer is preallocated (see startRecording),
        // so this only ever writes into already-owned memory — no malloc,
        // no lock, safe for the realtime thread.
        if (recordActive.load(std::memory_order_relaxed)) {
            size_t pos = recordWritePos.load(std::memory_order_relaxed);
            size_t cap = recordCapacity;
            float* dst = recordBuffer.data();
            int n = numFrames;
            if (pos + (size_t)n > cap) n = (int)(cap > pos ? cap - pos : 0);
            for (int i = 0; i < n; i++) dst[pos + i] = out[i];
            recordWritePos.store(pos + (size_t)n, std::memory_order_relaxed);
        }

        return oboe::DataCallbackResult::Continue;
    }

    // ── Recording controls (called from main/binder thread only) ───────────
    void startRecording() {
        recordCapacity = (size_t)sampleRate * (size_t)RECORD_MAX_SECONDS;
        recordBuffer.assign(recordCapacity, 0.f); // main-thread alloc, not RT
        recordWritePos.store(0, std::memory_order_relaxed);
        recordActive.store(true, std::memory_order_release);
    }

    void stopRecording() {
        recordActive.store(false, std::memory_order_release);
    }

    int getRecordedFrameCount() {
        return (int)recordWritePos.load(std::memory_order_acquire);
    }

    // Copies up to maxLen recorded frames (float -1..1 → 16-bit PCM) into out.
    // Returns the number of frames actually copied.
    int getRecordedPcm(short* out, int maxLen) {
        int count = getRecordedFrameCount();
        if (count > maxLen) count = maxLen;
        for (int i = 0; i < count; i++) {
            float s = recordBuffer[i];
            if (s > 1.f) s = 1.f;
            if (s < -1.f) s = -1.f;
            out[i] = (short)(s * 32767.f);
        }
        return count;
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
                vi = c.padIdx % LOOP_VOICES;
                // Destroy + create gives a completely fresh Sonic stream with
                // clean WSOLA/PSOLA internal state at each loop start.
                // sonicCreateStream is a small malloc (~1-2 KB) that happens
                // once at loop start — not in the speed/pitch update hot path.
                if (loopSonic[vi]) sonicDestroyStream(loopSonic[vi]);
                loopSonic[vi] = sonicCreateStream(sampleRate, 1);
                if (loopSonic[vi]) {
                    sonicSetSpeed(loopSonic[vi], c.speed);
                    sonicSetPitch(loopSonic[vi], c.pitch);
                    loopSonicLastSpeed[vi] = c.speed;
                    loopSonicLastPitch[vi] = c.pitch;
                }
            } else {
                vi = nextDrumVoice;
                nextDrumVoice = LOOP_VOICES + ((nextDrumVoice - LOOP_VOICES + 1) % DRUM_VOICES);
            }

            Voice& v      = voices[vi];
            v.active.store(false, std::memory_order_relaxed);
            v.padIndex    = c.padIdx;
            v.position    = 0;
            v.pitchAcc    = 0.f;
            v.volume.store(c.volume, std::memory_order_relaxed);
            v.speed .store(c.speed,  std::memory_order_relaxed);
            v.pitch .store(c.pitch,  std::memory_order_relaxed);
            v.chokeGroup  = c.chokeGroup;
            v.isLoop      = c.isLoop;
            v.delayOn     = c.delayOn;
            // Clamp to MAX_DELAY_FEEDBACK: delayLevel now doubles as the per-repeat
            // feedback amount, so 1.0 would make repeats decay forever without
            // fading out (endless buildup/clipping). This keeps the tail musical.
            v.delayLevel  = std::min(c.delayLevel, MAX_DELAY_FEEDBACK);
            // Use actual sampleRate for delay offset calculation (not hardcoded 44.1)
            v.delayOffset = c.delayOn ? (int)(c.delayMs * (sampleRate / 1000.0f)) : 0;
            if (v.delayOffset >= DELAY_BUF_SIZE) v.delayOffset = DELAY_BUF_SIZE - 1;

            // Use actual sampleRate for envelope ramp calculation
            const float sr = (float)sampleRate;
            v.attackRate  = (c.attackMs  > 0.f) ? (1.f / (c.attackMs  * sr / 1000.f)) : 1.f;
            v.releaseRate = (c.releaseMs > 0.f) ? (1.f / (c.releaseMs * sr / 1000.f)) : 0.f;
            v.envGain     = (c.attackMs  > 0.f) ? 0.f : 1.f;
            v.releasing   = false;
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

        case CMD_UPDATE_SPEED_PITCH:
            // Live speed/pitch update — update TARGET atomics ONLY.
            //
            // For loop voices: DO NOT call sonicSetSpeed/sonicSetPitch here.
            // The render loop's smooth-ramp (15%/callback) gradually moves
            // toward the target — tiny sonicSet steps that Sonic handles cleanly.
            // A hard jump was the root cause of crackling.
            //
            // For drum/one-shot voices: v.speed and v.pitch are read directly
            // in the render loop (no Sonic involved — linear resampling only).
            // Updating them here takes effect on the very next callback.

            // ── Update active loop voice at slot c.padIdx ──
            if (c.padIdx >= 0 && c.padIdx < LOOP_VOICES) {
                Voice& v = voices[c.padIdx];
                if (v.active.load() && v.isLoop) {
                    v.speed .store(c.speed,  std::memory_order_relaxed);
                    v.pitch .store(c.pitch,  std::memory_order_relaxed);
                    v.volume.store(c.volume, std::memory_order_relaxed);
                }
            }
            // ── Update active drum/one-shot voice with this pad index ──
            // Drum voices are allocated at indices LOOP_VOICES..NUM_VOICES-1.
            // They are identified by padIndex (pad number), not voice slot.
            for (int dvi = LOOP_VOICES; dvi < NUM_VOICES; dvi++) {
                Voice& dv = voices[dvi];
                if (dv.active.load() && dv.padIndex == c.padIdx && !dv.isLoop) {
                    dv.speed .store(c.speed,  std::memory_order_relaxed);
                    dv.pitch .store(c.pitch,  std::memory_order_relaxed);
                    dv.volume.store(c.volume, std::memory_order_relaxed);
                    break;  // at most one active one-shot per pad
                }
            }
            break;
        }
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result r) override {
        LOGE("Oboe stream error: %s", oboe::convertToText(r));
        // ERROR_DISCONNECTED fires for two very different situations:
        //
        //  (a) A real output-device change (earphone/BT plug or unplug). The Java
        //      AudioDeviceCallback in LoopsActivity/MainActivity handles this: it
        //      re-queries the device-native SR/burst from AudioManager and calls
        //      nativeReinitStream() from the main thread, then re-triggers any
        //      loops that were playing.
        //
        //  (b) An incoming phone call or a notification/message sound. Because
        //      the stream is opened in oboe::SharingMode::Exclusive, Android can
        //      forcibly preempt it so the system can play the ringtone/notification
        //      through the same hardware path — with NO audio-device add/remove
        //      event at all, so the Java AudioDeviceCallback above never fires.
        //      Previously this left the engine permanently silent (case (a)'s
        //      "let Java own it" early-return applied here too) until the user
        //      force-closed and reopened the app, which is the exact bug reported:
        //      sound stops for good the moment a call/notification sound plays.
        //
        // We can't tell (a) apart from (b) from this callback alone, and blindly
        // restarting here for every disconnect would race with (a)'s Java-owned
        // recovery (see the old comment this replaced) — Java might reinit with
        // the new device's correct SR/burst, then this callback fires moments
        // later and stomps it with the OLD params. So instead we run a short
        // watchdog: wait briefly for Java to heal the stream (case a); if nothing
        // reinitialized it in that window (case b, or Java's callback simply
        // never fires), heal it ourselves. Active voices (loops/one-shots) live
        // in `voices[]`, untouched by init(), so they keep playing the instant
        // the stream restarts — no explicit retrigger needed for this path.
        if (r == oboe::Result::ErrorDisconnected) {
            uint64_t genBefore = streamGeneration.load(std::memory_order_acquire);
            int restoreSR = sampleRate, restoreBurst = framesPerBurst;
            // Register the watchdog and check `destroying` atomically under the SAME
            // lock the destructor uses: if the destructor has already started tearing
            // down (or starts concurrently right here), this either sees destroying
            // already true and skips spawning entirely, or increments activeWatchdogs
            // before the destructor's wait can observe activeWatchdogs==0 — there is no
            // gap where a watchdog gets scheduled after the destructor stops watching
            // for it.
            {
                std::lock_guard<std::mutex> lk(watchdogMutex);
                if (destroying) return;
                activeWatchdogs++;
            }
            // Still detached (fire-and-forget thread handle), but the destructor
            // below blocks on watchdogCv until activeWatchdogs reaches 0, so this
            // thread is guaranteed to finish before `this` is ever freed.
            try {
                std::thread([this, genBefore, restoreSR, restoreBurst]() {
                    std::this_thread::sleep_for(std::chrono::milliseconds(400));
                    selfHealIfStillDisconnected(genBefore, restoreSR, restoreBurst);
                    {
                        std::lock_guard<std::mutex> lk(watchdogMutex);
                        if (--activeWatchdogs == 0) watchdogCv.notify_all();
                    }
                }).detach();
            } catch (...) {
                // Thread creation itself failed (e.g. resource exhaustion) — roll back
                // the count so the destructor doesn't wait forever for a watchdog that
                // never actually started.
                LOGE("Failed to spawn ErrorDisconnected watchdog thread");
                std::lock_guard<std::mutex> lk(watchdogMutex);
                if (--activeWatchdogs == 0) watchdogCv.notify_all();
            }
            return;
        }
        // For non-routing errors (underrun turned fatal, driver crash, etc.)
        // there is no Java callback, so we restart here immediately as a
        // best-effort recovery.
        LOGI("Non-routing error — restarting stream with current params");
        init(sampleRate, framesPerBurst);
    }

    // Guards init() against concurrent callers: Java can call it (via
    // nativeReinitStream, on the main thread) at nearly the same moment as the
    // ErrorDisconnected watchdog thread in onErrorAfterClose. Without this,
    // two threads could mutate `stream` at once (stop/close/reset racing with
    // a fresh openStream()), which is undefined behavior.
    std::mutex initMutex;

    // Tracks the ErrorDisconnected watchdog thread's lifetime so the destructor
    // can block until it's done, instead of letting a detached thread outlive
    // (and dereference) a freed AudioEngineImpl if the app is closed mid-sleep.
    std::mutex              watchdogMutex;
    std::condition_variable watchdogCv;
    int                     activeWatchdogs = 0;
    // Set by the destructor (under watchdogMutex) before it waits, so any
    // onErrorAfterClose racing to spawn a NEW watchdog right at teardown time sees
    // this and refuses to schedule one — otherwise a watchdog could be registered
    // after the destructor already observed activeWatchdogs==0 and moved on to free
    // `this`.
    bool                    destroying = false;

    // nativeSR:    device's actual hardware sample rate (from AudioManager)
    // nativeBurst: device's optimal frames-per-buffer (from AudioManager)
    // Matching these exactly avoids Android's internal audio resampler
    // and eliminates the ~20-40 ms latency it adds on non-native-rate streams.
    bool init(int nativeSR = 48000, int nativeBurst = 256) {
        std::lock_guard<std::mutex> lock(initMutex);
        return initLocked(nativeSR, nativeBurst);
    }

    // Body of init(), assumes initMutex is already held by the caller. Split out
    // so the ErrorDisconnected watchdog can re-check streamGeneration and run the
    // actual reinit atomically under a single lock acquisition (see
    // selfHealIfStillDisconnected) instead of racing between "check" and "init()".
    bool initLocked(int nativeSR, int nativeBurst) {
        sampleRate     = nativeSR;
        framesPerBurst = nativeBurst;

        if (stream) { stream->stop(); stream->close(); stream.reset(); }
        memset(gDelayBuf, 0, sizeof(gDelayBuf));

        // Reinitialize Sonic streams with the new sample rate.
        // Also reset loopSonicLastSpeed/Pitch to 1.0 so that the render
        // loop's smooth-ramp comparisons match the freshly created streams'
        // default state (speed=1.0, pitch=1.0). Without this reset, after
        // an Oboe error/restart the render loop sees "already at target"
        // (old last == old target) but the new stream is at default 1.0,
        // so sonicSetSpeed/Pitch would never fire → wrong playback speed/pitch.
        for (int i = 0; i < LOOP_VOICES; i++) {
            if (loopSonic[i]) sonicDestroyStream(loopSonic[i]);
            loopSonic[i] = sonicCreateStream(sampleRate, 1);
            // Reset last-applied values so the smooth ramp in the render loop
            // fires correctly after an Oboe error/restart cycle.
            loopSonicLastSpeed[i] = 1.0f;
            loopSonicLastPitch[i] = 1.0f;
        }

        oboe::AudioStreamBuilder b;
        // bufferCapacity: 3 bursts — gives the scheduler headroom without inflating latency
        // bufferSize (set after open): 2 bursts — minimum stable double-buffer
        oboe::Result r = b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setSampleRate(sampleRate)           // MATCH device native SR — no resampling
            ->setFramesPerCallback(framesPerBurst) // MATCH hardware burst — no extra buffering
            ->setBufferCapacityInFrames(framesPerBurst * 3)
            ->setDataCallback(this)
            ->openStream(stream);

        if (r != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(r));
            // Fallback: try shared mode (some devices deny exclusive)
            b.setSharingMode(oboe::SharingMode::Shared);
            r = b.openStream(stream);
            if (r != oboe::Result::OK) { LOGE("openStream shared also failed"); return false; }
        }

        // 1 burst = minimum latency for real-time drum triggers.
        // 2-burst "safe" default was adding ~5–10ms of unnecessary output
        // latency; hardware glitch-guard is the driver's job in Exclusive+LowLatency.
        stream->setBufferSizeInFrames(framesPerBurst * 1);

        r = stream->start();
        if (r != oboe::Result::OK) { LOGE("stream start: %s", oboe::convertToText(r)); return false; }

        LOGI("Oboe OK — rate=%d burst=%d bufSize=%d cap=%d api=%s sharing=%s",
             stream->getSampleRate(),
             stream->getFramesPerBurst(),
             stream->getBufferSizeInFrames(),
             stream->getBufferCapacityInFrames(),
             oboe::convertToText(stream->getAudioApi()),
             stream->getSharingMode() == oboe::SharingMode::Exclusive ? "exclusive" : "shared");
        streamGeneration.fetch_add(1, std::memory_order_release);
        return true;
    }

    // Called from the ErrorDisconnected watchdog thread after its delay. Re-checks
    // streamGeneration UNDER initMutex (not before acquiring it) so there is no gap
    // between "check" and "act": if Java's device-change reinit is concurrently
    // running, this call blocks on the lock until it finishes, then sees the bumped
    // generation and correctly skips — it can never stomp a fresh reinit with stale
    // pre-disconnect params.
    void selfHealIfStillDisconnected(uint64_t genBefore, int restoreSR, int restoreBurst) {
        std::lock_guard<std::mutex> lock(initMutex);
        if (streamGeneration.load(std::memory_order_acquire) != genBefore) {
            LOGI("Stream already reinitialized by Java device callback — watchdog no-op");
            return;
        }
        LOGI("No device-change reinit arrived after disconnect — "
             "self-healing stream (likely a call/notification sound, not a real device change)");
        initLocked(restoreSR, restoreBurst);
    }

    void loadSample(int padIdx, const short* data, int len) {
        if (padIdx < 0 || padIdx >= MAX_PADS || !data || len <= 0) return;
        std::vector<float> buf(len);
        for (int i = 0; i < len; i++)
            buf[i] = data[i] / 32768.0f;
        pads[padIdx].loaded.store(false, std::memory_order_release);
        pads[padIdx].pcm = std::move(buf);
        pads[padIdx].loaded.store(true,  std::memory_order_release);
        LOGI("Loaded pad %d: %d samples", padIdx, len);
    }

    // speed: time-stretch factor (1.0 = normal speed, independent of pitch)
    // pitch: pitch-shift factor  (1.0 = normal pitch, independent of speed)
    void playSample(int padIdx, float volume, float speed, float pitch,
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
        c.speed      = std::max(0.1f, std::min(4.f, speed));
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

    void playLoopSP(int padIdx, float volume, float speed, float pitchShift) {
        if (padIdx >= 0 && padIdx < LOOP_VOICES) {
            playSample(padIdx, volume, speed, pitchShift, false, 0.f, 0.f, 0, 0.f, 0.f, true);
        }
    }

    void updateLoopSpeedPitch(int padIdx, float volume, float speed, float pitch) {
        if (padIdx < 0 || padIdx >= LOOP_VOICES) return;
        Cmd c{};
        c.type   = CMD_UPDATE_SPEED_PITCH;
        c.padIdx = padIdx;
        c.volume = std::max(0.f, std::min(1.f, volume));
        c.speed  = std::max(0.1f, std::min(4.f, speed));
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
        // Block until any in-flight ErrorDisconnected watchdog thread (spawned in
        // onErrorAfterClose) has finished. Without this, nativeDestroyAudioEngine()
        // could delete this object while that detached thread is still sleeping,
        // and it would then dereference freed memory when it wakes up and calls
        // selfHealIfStillDisconnected(). Worst case this adds a bounded ~400ms
        // stall to app teardown, only in the rare case a disconnect just happened —
        // far preferable to a use-after-free crash.
        {
            std::unique_lock<std::mutex> lk(watchdogMutex);
            destroying = true; // refuse any new watchdog racing to start right now
            watchdogCv.wait(lk, [this] { return activeWatchdogs == 0; });
        }
        if (stream) { stream->stop(); stream->close(); }
        for (int i = 0; i < LOOP_VOICES; i++) {
            if (loopSonic[i]) sonicDestroyStream(loopSonic[i]);
        }
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

// nativeSR:    pass AudioManager.getProperty(PROPERTY_OUTPUT_SAMPLE_RATE)
// nativeBurst: pass AudioManager.getProperty(PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
// Matching these to the device hardware avoids internal Android resampling (~20-40 ms latency)
JNIEXPORT jlong JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeCreateAudioEngine(
        JNIEnv*, jobject, jint nativeSR, jint nativeBurst) {
    auto* e = new AudioEngineImpl();
    if (!e->init((int)nativeSR, (int)nativeBurst)) { delete e; return 0L; }
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
    // Legacy path: speed=1.0 (kept for backward compat). New code uses nativePlaySampleSP.
    if (e) e->playSample((int)padIdx, (float)volume, 1.f, (float)pitch,
                         (bool)delayOn, (float)delayMs, (float)delayLevel,
                         (int)chokeGroup, (float)attackMs, (float)releaseMs, false);
}

// New JNI: play one-shot/drum sample with BOTH speed + pitch applied.
// speed = playback rate multiplier for duration (1.0 = normal, 2.0 = 2× faster)
// pitch = pitch-shift multiplier on top (1.0 = normal, 2.0 = octave up)
// Combined effect: rate = speed × pitch via linear resampling in the render loop.
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativePlaySampleSP(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat speed, jfloat pitch,
        jboolean delayOn, jfloat delayMs, jfloat delayLevel,
        jfloat /*eqLow*/, jfloat /*eqMid*/, jfloat /*eqHigh*/,
        jint chokeGroup, jfloat attackMs, jfloat releaseMs) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playSample((int)padIdx, (float)volume, (float)speed, (float)pitch,
                         (bool)delayOn, (float)delayMs, (float)delayLevel,
                         (int)chokeGroup, (float)attackMs, (float)releaseMs, false);
}

// nativePlayLoop: speed and pitch are independent parameters
// speed = time-stretch (1.0 = normal, 2.0 = 2x faster with same pitch)
// pitch = pitch-shift   (1.0 = normal, 2.0 = one octave up at same speed)
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativePlayLoop(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat speed, jfloat pitch) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playSample((int)padIdx, (float)volume, (float)speed, (float)pitch,
                         false, 0.f, 0.f, 0, 0.f, 0.f, true);
}

// nativePlayLoopSP: start loop with independent speed + pitch
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativePlayLoopSP(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat speed, jfloat pitchShift) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playLoopSP((int)padIdx, (float)volume, (float)speed, (float)pitchShift);
}

// nativeUpdateLoopSpeedPitch: live update speed + pitch without restarting loop
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeUpdateLoopSpeedPitch(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat speed, jfloat pitch) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->updateLoopSpeedPitch((int)padIdx, (float)volume, (float)speed, (float)pitch);
}

// Keep old nativeUpdateLoopPitch for backward compatibility
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeUpdateLoopPitch(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat pitch) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->updateLoopSpeedPitch((int)padIdx, (float)volume, 1.f, (float)pitch);
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

// Called from Java AudioDeviceCallback when the audio output device changes
// (earphone plug/unplug, Bluetooth connect/disconnect, etc.).
// Re-opens the Oboe stream with the new device's native SR and burst size.
// All sample data and voice active-flags are preserved so loops resume
// seamlessly on the new output device.
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeReinitStream(
        JNIEnv* env, jobject obj, jint nativeSR, jint nativeBurst) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->init((int)nativeSR, (int)nativeBurst);
}

// ── Internal/system-audio recording (post-mix tap of the engine's own output) ──
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeStartRecording(JNIEnv* env, jobject obj) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->startRecording();
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeStopRecording(JNIEnv* env, jobject obj) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->stopRecording();
}

JNIEXPORT jint JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeGetRecordedFrameCount(JNIEnv* env, jobject obj) {
    AudioEngineImpl* e = getEngine(env, obj);
    return e ? (jint)e->getRecordedFrameCount() : 0;
}

// Fills `out` with up to out.length recorded PCM frames; returns count copied.
JNIEXPORT jint JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeGetRecordedPcm(
        JNIEnv* env, jobject obj, jshortArray out) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (!e || !out) return 0;
    jsize len = env->GetArrayLength(out);
    jshort* data = env->GetShortArrayElements(out, nullptr);
    int copied = e->getRecordedPcm((short*)data, (int)len);
    env->ReleaseShortArrayElements(out, data, 0);
    return (jint)copied;
}

} // extern "C"
