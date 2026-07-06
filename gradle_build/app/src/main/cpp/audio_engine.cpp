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
    int       nextDrumVoice = LOOP_VOICES;
    std::shared_ptr<oboe::AudioStream> stream;

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

    AudioEngineImpl() {
        memset(loopSonic, 0, sizeof(loopSonic));
        for (int i = 0; i < LOOP_VOICES; i++) {
            loopSonicLastSpeed[i] = 1.f;
            loopSonicLastPitch[i] = 1.f;
        }
    }

    // ── Drain a Sonic stream to a clean "fresh" state — safe for RT thread ────
    //
    // Why not just sonicFlushStream() first?
    //   sonicFlushStream() pushes remaining buffered input through WSOLA into
    //   the output ring.  If the output ring is already near-full, Sonic may
    //   call realloc() to grow it — and realloc() locks the system allocator
    //   mutex → priority inversion → crackling.
    //
    // Safe drain order:
    //   1. Read-drain all existing output first  → output ring is now empty
    //      (maximum free space — realloc impossible in step 2).
    //   2. sonicFlushStream() → processes any remaining buffered input and
    //      writes result to the now-empty output ring (no realloc needed).
    //   3. Read-drain the flushed output.
    //
    // Additionally: all Sonic streams are pre-sized in init() by writing and
    // draining SCRATCH_SIZE samples before any callback runs.  This guarantees
    // the input/pitch/output ring capacities are already large enough for all
    // subsequent writes ≤ SCRATCH_SIZE, eliminating realloc paths entirely.
    //
    // After this call the stream is in a clean idle state: input ring empty,
    // output ring empty, WSOLA buffers flushed — equivalent to a fresh stream
    // without any heap allocation in this function itself.
    void drainSonicStream(sonicStream s, float* tmpBuf, int tmpLen) {
        if (!s) return;
        // Step 1: drain pre-existing output (frees output ring space)
        int avail;
        while ((avail = sonicSamplesAvailable(s)) > 0) {
            int n = (avail < tmpLen) ? avail : tmpLen;
            sonicReadFloatFromStream(s, tmpBuf, n);
        }
        // Step 2: flush remaining input → output (output ring has max free space now)
        sonicFlushStream(s);
        // Step 3: drain the flushed output
        while ((avail = sonicSamplesAvailable(s)) > 0) {
            int n = (avail < tmpLen) ? avail : tmpLen;
            sonicReadFloatFromStream(s, tmpBuf, n);
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

                // ── Sonic feeding with loop-boundary crossfade ───────────────────
                // Keep enough OUTPUT samples buffered (3× numFrames) so playback
                // never starves mid-buffer. sonicSamplesAvailable() returns OUTPUT
                // samples (after speed processing), so the threshold must be in the
                // output domain too. When avail drops below the target, feed enough
                // RAW INPUT samples to refill it — scale by spd because Sonic needs
                // spd× more input per output frame at higher speeds.
                // Crossfade the last XFADE input samples at the loop boundary so the
                // wrap point is inaudible (blends tail smoothly into head).
                static const int XFADE = 256;
                const int OUTPUT_TARGET = numFrames * 3;  // desired buffered output frames
                int avail = sonicSamplesAvailable(sonic);
                if (avail < OUTPUT_TARGET) {
                    // How many raw input samples to feed to reach OUTPUT_TARGET
                    int toFeed = (int)((OUTPUT_TARGET - avail) * spd) + 256;
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
                // ── Drum voice: simple linear-interpolation resampling ───────────
                float pitch = v.pitch.load(std::memory_order_relaxed);

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

                    // Delay tap
                    if (v.delayOn && v.delayOffset > 0) {
                        int ri = ((gDelayWrite + i - v.delayOffset) % DELAY_BUF_SIZE + DELAY_BUF_SIZE) % DELAY_BUF_SIZE;
                        samp += gDelayBuf[ri] * v.delayLevel;
                    }

                    out[i] += samp;

                    // Advance position with pitch shift (resampling)
                    v.pitchAcc += pitch - 1.f;
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
                vi = c.padIdx % LOOP_VOICES;
                // Reset Sonic stream for clean restart — no malloc in hot path.
                // drainSonicStream() flushes WSOLA state without destroying the
                // allocation; sonicSetSpeed/Pitch on the drained stream then
                // starts fresh with zero artifacts.
                // Null guard: streams are pre-allocated in init(), but if one is
                // somehow null (e.g. sonicCreateStream() returned null at startup
                // due to OOM) we fall back to creating one here.  This branch is
                // NOT on the hot path — it fires at most once per lifetime.
                if (!loopSonic[vi]) {
                    loopSonic[vi] = sonicCreateStream(sampleRate, 1);
                } else {
                    drainSonicStream(loopSonic[vi], feedBuf, SCRATCH_SIZE);
                }
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
            v.delayLevel  = c.delayLevel;
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
            // DO NOT call sonicSetSpeed/sonicSetPitch here.
            // The render loop's smooth-ramp block (15% per callback) reads
            // v.speed / v.pitch each callback and gradually moves
            // loopSonicLastSpeed/Pitch toward the target, calling sonicSet
            // with tiny per-callback steps that Sonic's WSOLA handles cleanly.
            //
            // A hard sonicSet jump to the target value from the command handler
            // was the root cause of crackling: Sonic's internal WSOLA overlap
            // windows re-synchronise abruptly → discontinuity → audible crack.
            // Spreading the same change over ~6 callbacks (~30ms) is inaudible.
            if (c.padIdx >= 0 && c.padIdx < LOOP_VOICES) {
                Voice& v = voices[c.padIdx];
                if (v.active.load()) {
                    v.speed .store(c.speed,  std::memory_order_relaxed);
                    v.pitch .store(c.pitch,  std::memory_order_relaxed);
                    v.volume.store(c.volume, std::memory_order_relaxed);
                }
            }
            break;
        }
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result r) override {
        LOGE("Oboe error: %s — restarting", oboe::convertToText(r));
        // Re-init using the same native SR/burst that were set at startup
        init(sampleRate, framesPerBurst);
    }

    // nativeSR:    device's actual hardware sample rate (from AudioManager)
    // nativeBurst: device's optimal frames-per-buffer (from AudioManager)
    // Matching these exactly avoids Android's internal audio resampler
    // and eliminates the ~20-40 ms latency it adds on non-native-rate streams.
    bool init(int nativeSR = 48000, int nativeBurst = 256) {
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
            loopSonicLastSpeed[i] = 1.0f;
            loopSonicLastPitch[i] = 1.0f;

            // ── Pre-size Sonic internal buffers at WORST-CASE (max speed + max pitch) ──
            //
            // Why this is critical:
            //   Sonic's WSOLA internally calls enlargeInputBufferIfNeeded() /
            //   enlargeOutputBufferIfNeeded() / enlargePitchBufferIfNeeded()
            //   whenever it needs more space.  These call realloc() — which locks
            //   the system allocator mutex.  If the RT audio thread holds realloc
            //   while the GC runs, that's priority inversion → buffer underrun
            //   → crackling (or worse, crash).
            //
            //   The buffer sizes depend on `maxRequired`, which is proportional to
            //   speed × pitch.  Pre-sizing here at speed=4.0 (max) and pitch=8.0
            //   (max) guarantees all rings are already at their largest possible
            //   size.  Any subsequent sonicWriteFloatToStream call in the audio
            //   callback — at any speed/pitch ≤ these maximums — will NEVER need
            //   to reallocate.  This makes the smooth-ramp approach (calling
            //   sonicSetSpeed/Pitch with tiny per-callback steps) permanently safe.
            if (loopSonic[i]) {
                float dummy[SCRATCH_SIZE];
                memset(dummy, 0, sizeof(dummy));
                // Set to absolute maximums so Sonic computes max `maxRequired`
                sonicSetSpeed(loopSonic[i], 4.0f);   // app-side max speed
                sonicSetPitch(loopSonic[i], 8.0f);   // app-side max pitch
                // Write + flush + drain: grows all internal rings to their
                // maximum size while still on a non-RT thread (mallocs are safe here)
                sonicWriteFloatToStream(loopSonic[i], dummy, SCRATCH_SIZE);
                sonicFlushStream(loopSonic[i]);
                int avail;
                while ((avail = sonicSamplesAvailable(loopSonic[i])) > 0) {
                    int n = avail < SCRATCH_SIZE ? avail : SCRATCH_SIZE;
                    sonicReadFloatFromStream(loopSonic[i], dummy, n);
                }
                // Reset to defaults — stream is now sized for worst case + clean
                sonicSetSpeed(loopSonic[i], 1.0f);
                sonicSetPitch(loopSonic[i], 1.0f);
                // Stream is ready: no further allocs at any speed/pitch ≤ max values
            }
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

        // 2 bursts = lowest stable latency on most devices
        stream->setBufferSizeInFrames(framesPerBurst * 2);

        r = stream->start();
        if (r != oboe::Result::OK) { LOGE("stream start: %s", oboe::convertToText(r)); return false; }

        LOGI("Oboe OK — rate=%d burst=%d bufSize=%d cap=%d api=%s sharing=%s",
             stream->getSampleRate(),
             stream->getFramesPerBurst(),
             stream->getBufferSizeInFrames(),
             stream->getBufferCapacityInFrames(),
             oboe::convertToText(stream->getAudioApi()),
             stream->getSharingMode() == oboe::SharingMode::Exclusive ? "exclusive" : "shared");
        return true;
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
    if (e) e->playSample((int)padIdx, (float)volume, 1.f, (float)pitch,
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

} // extern "C"
