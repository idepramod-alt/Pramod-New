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
static const float MAX_DELAY_FEEDBACK = 0.82f; // keeps the tail musical, never runs away

// ─── 3-band tone EQ (low-shelf / mid-peak / high-shelf), RBJ cookbook biquads ──
// Ported from the reference engine: eqLow/eqMid/eqHigh are dB-style gains
// (~-15..+15, see MainActivity's (progress-100)*0.15f mapping) that shape the
// pad's tone before it hits the delay line, so echoes inherit the same
// coloration as the dry hit — this is what was missing from our delay: the
// eqLow/eqMid/eqHigh sliders were wired into playSample() but silently
// discarded, so they never affected the sound at all.
struct BiquadCoeffs { float b0 = 1.f, b1 = 0.f, b2 = 0.f, a1 = 0.f, a2 = 0.f; };
struct BiquadState  { float z1 = 0.f, z2 = 0.f; };

static inline float dbToGainSqrt(float dB) { return powf(10.f, dB / 40.f); } // sqrt(10^(dB/20))

// Guards against invalid/unstable coefficients on low sample rates (engine
// supports SR down to a few kHz on some devices) — a shelf/peak frequency at
// or above Nyquist produces a degenerate or unstable biquad.
static inline float safeFreq(float freq, float sr) {
    float nyquistSafe = 0.45f * sr;
    return std::min(freq, nyquistSafe);
}

static BiquadCoeffs makeLowShelf(float sr, float freq, float dB) {
    BiquadCoeffs c;
    if (dB == 0.f) return c;
    freq = safeFreq(freq, sr);
    float A = dbToGainSqrt(dB);
    float w0 = 2.f * (float)M_PI * freq / sr;
    float cosw0 = cosf(w0), sinw0 = sinf(w0);
    float S = 1.f; // shelf slope
    float alpha = sinw0 / 2.f * sqrtf((A + 1.f/A) * (1.f/S - 1.f) + 2.f);
    float sqrtA = sqrtf(A);
    float b0 =    A*((A+1) - (A-1)*cosw0 + 2*sqrtA*alpha);
    float b1 =  2*A*((A-1) - (A+1)*cosw0);
    float b2 =    A*((A+1) - (A-1)*cosw0 - 2*sqrtA*alpha);
    float a0 =      (A+1) + (A-1)*cosw0 + 2*sqrtA*alpha;
    float a1 =   -2*((A-1) + (A+1)*cosw0);
    float a2 =      (A+1) + (A-1)*cosw0 - 2*sqrtA*alpha;
    c.b0 = b0/a0; c.b1 = b1/a0; c.b2 = b2/a0; c.a1 = a1/a0; c.a2 = a2/a0;
    return c;
}

static BiquadCoeffs makeHighShelf(float sr, float freq, float dB) {
    BiquadCoeffs c;
    if (dB == 0.f) return c;
    freq = safeFreq(freq, sr);
    float A = dbToGainSqrt(dB);
    float w0 = 2.f * (float)M_PI * freq / sr;
    float cosw0 = cosf(w0), sinw0 = sinf(w0);
    float S = 1.f;
    float alpha = sinw0 / 2.f * sqrtf((A + 1.f/A) * (1.f/S - 1.f) + 2.f);
    float sqrtA = sqrtf(A);
    float b0 =    A*((A+1) + (A-1)*cosw0 + 2*sqrtA*alpha);
    float b1 = -2*A*((A-1) + (A+1)*cosw0);
    float b2 =    A*((A+1) + (A-1)*cosw0 - 2*sqrtA*alpha);
    float a0 =      (A+1) - (A-1)*cosw0 + 2*sqrtA*alpha;
    float a1 =    2*((A-1) - (A+1)*cosw0);
    float a2 =      (A+1) - (A-1)*cosw0 - 2*sqrtA*alpha;
    c.b0 = b0/a0; c.b1 = b1/a0; c.b2 = b2/a0; c.a1 = a1/a0; c.a2 = a2/a0;
    return c;
}

static BiquadCoeffs makePeaking(float sr, float freq, float dB, float Q) {
    BiquadCoeffs c;
    if (dB == 0.f) return c;
    freq = safeFreq(freq, sr);
    float A = dbToGainSqrt(dB);
    float w0 = 2.f * (float)M_PI * freq / sr;
    float cosw0 = cosf(w0), sinw0 = sinf(w0);
    float alpha = sinw0 / (2.f * Q);
    float b0 = 1 + alpha*A;
    float b1 = -2*cosw0;
    float b2 = 1 - alpha*A;
    float a0 = 1 + alpha/A;
    float a1 = -2*cosw0;
    float a2 = 1 - alpha/A;
    c.b0 = b0/a0; c.b1 = b1/a0; c.b2 = b2/a0; c.a1 = a1/a0; c.a2 = a2/a0;
    return c;
}

static inline float biquadProcess(const BiquadCoeffs& c, BiquadState& s, float x) {
    // Transposed Direct Form II — stable, cheap, standard for per-sample RT use.
    float y = c.b0 * x + s.z1;
    s.z1 = c.b1 * x - c.a1 * y + s.z2;
    s.z2 = c.b2 * x - c.a2 * y;
    // Defensive: a pathological coefficient set (shouldn't happen post-safeFreq/
    // dB clamping, but extreme device sample rates are untested) could still
    // diverge into NaN/Inf. Reset state and pass the input through dry rather
    // than let a runaway filter corrupt the output stream.
    if (!std::isfinite(y) || !std::isfinite(s.z1) || !std::isfinite(s.z2)) {
        s = BiquadState{};
        return x;
    }
    return y;
}

// ─── Pad buffer (loaded samples) ─────────────────────────────────────────────
struct PadBuffer {
    std::vector<float> pcm;      // interleaved [L0,R0,L1,R1,...] when channels==2
    std::atomic<bool>  loaded{false};
    int                chokeGroup = 0;
    int                channels   = 1;  // 1=mono, 2=stereo
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
    std::atomic<float> pan{0.f};  // stereo pan: -1.0=full L, 0.0=center, +1.0=full R
    // Delay
    bool    delayOn    = false;
    float   delayLevel = 0.f;
    int     delayOffset= 0;
    // 3-band tone EQ (low-shelf/mid-peak/high-shelf) — shapes the dry hit AND,
    // since the delay buffer stores the post-EQ output, the echoes too.
    // Separate filter state per channel so L and R retain their stereo image.
    bool         eqOn = false;
    BiquadCoeffs eqLowC, eqMidC, eqHighC;
    BiquadState  eqLowSL, eqMidSL, eqHighSL;   // Left channel EQ state
    BiquadState  eqLowSR, eqMidSR, eqHighSR;   // Right channel EQ state
    // OLA granular synthesis state
    int   grainStartA = 0;
    int   grainStartB = 0;
    float synPhase    = 0.f;
};

// ─── Lock-free SPSC command queue ────────────────────────────────────────────
enum CmdType { CMD_PLAY, CMD_STOP_PAD, CMD_STOP_ALL, CMD_UPDATE_SPEED_PITCH, CMD_RELEASE_PAD };

struct Cmd {
    CmdType type;
    int     padIdx;
    float   volume;
    float   speed;    // time-stretch factor (1.0 = normal, 2.0 = 2x faster, no pitch change)
    float   pitch;    // pitch shift factor  (1.0 = normal, 2.0 = one octave up, no speed change)
    bool    delayOn;
    float   delayMs;
    float   delayLevel;
    float   eqLow;
    float   eqMid;
    float   eqHigh;
    int     chokeGroup;
    float   attackMs;
    float   releaseMs;
    float   pan;       // stereo pan: -1.0=full L, 0.0=center, +1.0=full R
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
    int audioSessionId = 0;  // Oboe stream's audio session ID for Equalizer attachment
    // Bumped every time init() successfully (re)opens the stream. Used by the
    // ErrorDisconnected fallback-restart watchdog (see onErrorAfterClose) to
    // detect whether someone else (Java's AudioDeviceCallback path) already
    // healed the stream before the watchdog's delay elapses.
    std::atomic<uint64_t> streamGeneration{0};

    // Device-native audio parameters (set from Java via AudioManager queries)
    // Using native SR avoids Android's internal resampler and cuts ~20-40 ms latency
    int sampleRate    = 48000;
    int framesPerBurst = 256;
    // Actual channel count reported by Oboe after stream open. We request 2
    // (stereo) but the device may fall back to 1 (mono). Stored here so the
    // audio callback can use the real value for memset and L+R expansion.
    int streamChannels = 1;

    // Per-voice delay lines (NOT a single shared/global buffer). Each voice
    // (i.e. each individual pad hit) gets its own delay history, so a pad
    // with delay OFF never picks up echoes from a different pad that has
    // delay ON, and one pad's delay tail can't be perceived as affecting
    // another pad's choke/cutoff behavior.
    float delayBuf[NUM_VOICES][DELAY_BUF_SIZE];
    int   delayWrite[NUM_VOICES] = {0};

    // ── Global 3-band EQ (master bus) ──────────────────────────────────────
    // Applied to the final mixed output after soft saturation. Uses the same
    // biquad filters as per-pad EQ but on the master bus, so it affects ALL
    // audio (loops + drums). Updated from Java via nativeSetGlobalEQ().
    BiquadCoeffs gEqLowC, gEqMidC, gEqHighC;
    BiquadState  gEqLowSL, gEqMidSL, gEqHighSL;   // Left channel
    BiquadState  gEqLowSR, gEqMidSR, gEqHighSR;   // Right channel
    bool         gEqDirty = true;  // recompute coefficients on next render
    std::atomic<float> gEqLowDB{0.f};
    std::atomic<float> gEqMidDB{0.f};
    std::atomic<float> gEqHighDB{0.f};

    // ── Loop voices: Sonic (speed/WSOLA) + engine ring resampler (pitch) ────
    // ARCHITECTURE (gives INDEPENDENT speed and pitch, both click-free):
    //   • Sonic applies SPEED only (pitch pinned to 1.0). WSOLA time-stretch
    //     is tempo-only — speed changes never shift pitch.
    //   • The engine's ring-buffer LINEAR FRACTIONAL resampler applies PITCH
    //     to Sonic's output. Linear interpolation is value-continuous for any
    //     rate change, so live pitch drags can never click — and Sonic is never
    //     re-synced by a pitch change (its sinc re-sync WAS the crack source).
    //   • Ring occupancy is kept constant (drift-free): pos advances by exactly
    //     `got` frames per callback, so the ring never wraps onto the reader.
    sonicStream loopSonic[LOOP_VOICES];
    float loopSonicLastSpeed[LOOP_VOICES];  // ramped speed fed to Sonic (WSOLA)
    float loopSonicLastVol[LOOP_VOICES];    // smooth volume ramp (zipper-free)
    float loopSonicLastPan[LOOP_VOICES];    // smooth pan ramp
    float loopPitchRamp[LOOP_VOICES];       // ramped pitch target for the resampler
    float loopPitchPos[LOOP_VOICES];        // absolute resampler read pos (frames)
    long  loopRingTotal[LOOP_VOICES];       // total frames Sonic wrote into ring
    int   loopSonicChannels[LOOP_VOICES];   // 1=mono, 2=stereo
    static const int RING_CAP   = 8192;     // per-voice ring capacity (frames)
    static const int RING_HEAD  = 256;      // priming headroom so reader never starves
    float loopRing[LOOP_VOICES * RING_CAP * 2];  // interleaved stereo ring

    // scratch buffers (audio thread only — no malloc in callback)
    static const int SCRATCH_SIZE = 4096;
    float feedBuf[SCRATCH_SIZE * 2];
    float readBuf[SCRATCH_SIZE * 2];

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
            // Pre-warm Sonic streams at construction time so the first loop
            // play() call doesn't incur sonicCreateStream malloc on the audio
            // thread — eliminates the 2-5ms "first hit" delay for loop pads.
            loopSonic[i] = sonicCreateStream(48000, 1); // 48kHz default; reinit() updates SR
            loopSonicLastSpeed[i] = 1.f;
            loopSonicLastVol[i]   = 1.f;
            loopSonicLastPan[i]   = 0.f;
            loopPitchRamp[i]      = 1.f;
            loopPitchPos[i]       = 0.f;
            loopRingTotal[i]      = 0;
            loopSonicChannels[i]  = 1;   // default mono; updated on CMD_PLAY
        }
    }

    // ── Audio callback (realtime thread — no malloc, no mutex, no blocking) ──
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream*, void* audioData, int32_t numFrames) override {

        float* out = static_cast<float*>(audioData);
        const int nCh = streamChannels;               // 1 (mono) or 2 (stereo)
        memset(out, 0, sizeof(float) * numFrames * nCh);

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
                sonicStream sonic = loopSonic[vi];

                // ── Speed (WSOLA) + pitch (ring resampler) — INDEPENDENT ──────
                // Sonic applies SPEED only (pitch pinned 1.0): tempo changes
                // don't shift pitch. Pitch is applied by the engine's drift-free
                // linear ring resampler on Sonic's output, so pitch drags never
                // re-sync Sonic (no sinc re-sync click) and never starve (ring
                // occupancy is constant). Both controls stay independent.
                float targetSpd  = v.speed.load(std::memory_order_relaxed);
                float targetPtch = v.pitch.load(std::memory_order_relaxed);

                // ── Pitch: slow per-callback ramp + per-sample glide ─────────────
                // The resampler rate is ramped gently each callback (rateStart →
                // rateEnd, ~2-3% of remaining) and then glides LINEARLY sample by
                // sample inside the callback. A per-callback rate STEP is the
                // audible crack during pitch drags; a per-sample glide is
                // mathematically continuous → zero click, zero step.
                if (targetPtch < 0.1f) targetPtch = 0.1f;
                if (targetPtch > 8.0f) targetPtch = 8.0f;
                float rateStart = loopPitchRamp[vi];         // rate at callback start
                float rateEnd   = rateStart;
                float ptchDelta = targetPtch - rateStart;
                if (fabsf(ptchDelta) > 0.001f) {
                    float step = (fabsf(ptchDelta) > 0.5f) ? 0.02f : 0.03f;
                    rateEnd = rateStart + step * ptchDelta;
                    if (fabsf(targetPtch - rateEnd) < step) rateEnd = targetPtch;
                }
                loopPitchRamp[vi] = rateEnd;                 // persist for next callback
                if (rateEnd < 0.1f) rateEnd = 0.1f;
                if (rateEnd > 8.0f) rateEnd = 8.0f;
                // Average rate over this callback — the ring is balanced against it
                float rateAvg = 0.5f * (rateStart + rateEnd);

                // ── Speed (Sonic WSOLA) with tempo compensation ─────────────────
                // Sonic's speed is set to rampedSpeed / rateEnd so that the ring
                // resampler's pitch effect on tempo (×P) is cancelled out: the
                // loop then plays at tempo = rampedSpeed REGARDLESS of pitch —
                // speed and pitch are fully independent (speed = tempo only,
                // pitch = pitch only), which is what live performance needs.
                // Sonic's WSOLA speed is ramped gently (3-6%/callback) so its
                // period detection never jumps.
                {
                    const float spdDelta = fabsf(targetSpd - loopSonicLastSpeed[vi]);
                    float SMOOTH = (spdDelta > 0.5f) ? 0.03f : 0.06f;
                    float newSpd = loopSonicLastSpeed[vi] + SMOOTH * (targetSpd - loopSonicLastSpeed[vi]);
                    if (fabsf(newSpd - targetSpd) < 0.002f) newSpd = targetSpd;
                    // Compensated speed: tempo S is recovered after the ×P ring
                    // resample. Clamp to Sonic's usable WSOLA range [0.15, 6].
                    float compSpd = newSpd / rateEnd;
                    if (compSpd < 0.15f) compSpd = 0.15f;
                    if (compSpd > 6.0f)  compSpd = 6.0f;
                    if (fabsf(compSpd - loopSonicLastSpeed[vi]) > 0.002f) {
                        sonicSetSpeed(sonic, compSpd);
                    }
                    loopSonicLastSpeed[vi] = newSpd;         // the un-compensated S
                }

                // ── Smooth volume/pan ramping (zipper-free) ────────────────────
                {
                    float targetVol = v.volume.load(std::memory_order_relaxed);
                    float targetPan = v.pan.load(std::memory_order_relaxed);
                    float newVol = loopSonicLastVol[vi] + 0.06f * (targetVol - loopSonicLastVol[vi]);
                    float newPan = loopSonicLastPan[vi] + 0.06f * (targetPan - loopSonicLastPan[vi]);
                    if (fabsf(newVol - targetVol) < 0.002f) newVol = targetVol;
                    if (fabsf(newPan - targetPan) < 0.002f) newPan = targetPan;
                    loopSonicLastVol[vi] = newVol;
                    loopSonicLastPan[vi] = newPan;
                }

                int   loopCh = loopSonicChannels[vi];   // 1=mono pad, 2=stereo pad
                if (loopCh < 1 || loopCh > 2) loopCh = 1;
                size_t numFrm = pb.pcm.size() / (size_t)loopCh;
                if (numFrm < 2) continue;

                float spd = loopSonicLastSpeed[vi];   // un-compensated tempo S
                // Actual Sonic speed (after ÷rateEnd compensation) drives its input
                // consumption, so the feed headroom must use THIS value.
                float sonicSpd = spd / rateEnd;
                if (sonicSpd < 0.15f) sonicSpd = 0.15f;
                if (sonicSpd > 6.0f)  sonicSpd = 6.0f;

                // ── Sonic feed (speed + pitch-aware — never starves the ring) ───
                // Sonic (pitch pinned 1.0) produces input/sonicSpd output; the feed
                // target must cover BOTH the WSOLA 3x headroom AND this callback's
                // ring read rate (readCount = numFrames×rateAvg), so Sonic always
                // has enough output for the resampler — no underflow, no reader
                // catch-up (a catch-up forces a ring repeat = click).
                static const int XFADE = 256;
                float feedRate = fmaxf(sonicSpd * 3.0f, rateAvg);
                int feedTarget = (int)(numFrames * feedRate) + 512;
                if (feedTarget > SCRATCH_SIZE) feedTarget = SCRATCH_SIZE;
                int avail = sonicSamplesAvailable(sonic);
                if (avail < feedTarget) {
                    // Sonic (speed=sonicSpd, pitch=1) turns N input frames into
                    // N/sonicSpd output frames — so to raise the output by the
                    // deficit we must feed deficit × sonicSpd INPUT frames.
                    int toFeed = (int)((feedTarget - avail) * sonicSpd) + 8;
                    if (toFeed > SCRATCH_SIZE) toFeed = SCRATCH_SIZE;
                    int fed = 0;
                    while (fed < toFeed) {
                        if (v.position >= (int)numFrm) v.position = 0;
                        size_t pos = v.position;
                        float fL = pb.pcm[pos * (size_t)loopCh];
                        float fR = (loopCh > 1) ? pb.pcm[pos * (size_t)loopCh + 1] : fL;
                        // Crossfade tail → head at the loop boundary (wrap click).
                        if (numFrm > (size_t)(XFADE * 2) && pos >= numFrm - (size_t)XFADE) {
                            size_t tailOff = pos - (numFrm - XFADE);
                            float  t       = (float)tailOff / (float)XFADE;
                            float  headL   = pb.pcm[tailOff * (size_t)loopCh];
                            float  headR   = (loopCh > 1) ? pb.pcm[tailOff * (size_t)loopCh + 1] : headL;
                            fL = fL * (1.f - t) + headL * t;
                            fR = fR * (1.f - t) + headR * t;
                        }
                        feedBuf[fed * loopCh]     = fL;
                        if (loopCh > 1) feedBuf[fed * loopCh + 1] = fR;
                        fed++;
                        v.position++;
                    }
                    sonicWriteFloatToStream(sonic, feedBuf, fed);
                }

                // ── Read Sonic output into the pitch ring (drift-free) ─────────
                // readCount = round(numFrames × rateAvg) — the resampler glides
                // from rateStart to rateEnd this callback and its average rate is
                // rateAvg, so reading exactly rateAvg×numFrames keeps ring
                // occupancy constant (no accumulation, no wrap onto the reader).
                int readCount = (int)(numFrames * rateAvg + 0.5f);
                if (readCount > SCRATCH_SIZE) readCount = SCRATCH_SIZE;
                if (readCount < 0) readCount = 0;
                int got = sonicReadFloatFromStream(sonic, readBuf, readCount);

                long ringBase  = (long)vi * RING_CAP * 2;
                long ringTotal = loopRingTotal[vi];
                for (int i = 0; i < got; i++) {
                    long slot = ringBase + ((ringTotal + i) % RING_CAP) * 2;
                    float sL  = readBuf[i * loopCh];
                    float sR  = (loopCh > 1) ? readBuf[i * loopCh + 1] : sL;
                    loopRing[slot]     = sL;
                    loopRing[slot + 1] = sR;
                }
                loopRingTotal[vi] = ringTotal + got;

                // ── Engine ring resampler (cubic, per-sample rate glide) ────────
                // pos advances at a rate that GLIDES linearly from rateStart to
                // rateEnd across the callback — zero rate steps between samples.
                // Total advance = rateAvg × numFrames ≈ got → ring occupancy is
                // constant, no drift, no wrap. Catmull-Rom interpolation removes
                // the aliasing/stair-step of linear interpolation (audible on
                // sustained loops). RING_HEAD priming keeps the reader clear of
                // unwritten slots during Sonic's startup latency.
                float pos = loopPitchPos[vi];
                // pos must be ≥ 1 so the cubic's p0 = pos-1 never reads a negative
                // (unwritten) ring slot during the first callbacks after play.
                if (pos < 1.f) pos = 1.f;
                // Keep the reader RING_HEAD frames behind the write head: this
                // primes the ring at startup AND absorbs Sonic's WSOLA burstiness
                // (when WSOLA stalls a few callbacks then dumps a chunk, the 256
                // frames of history keep the resampler fed). With the feed now
                // covering readCount, pos should never actually catch up.
                if (pos + RING_HEAD > (float)loopRingTotal[vi]) {
                    pos = (float)(loopRingTotal[vi] - RING_HEAD);
                    if (pos < 1.f) pos = 1.f;
                }
                // If rate is changing, per-sample advance glides rateStart→rateEnd;
                // otherwise it's constant. (Precomputed to keep the hot loop cheap.)
                const float dRate = (rateEnd - rateStart) / (float)(numFrames > 0 ? numFrames : 1);

                float panV  = loopSonicLastPan[vi];
                float lGain = (panV <= 0.f) ? 1.f : (1.f - panV);
                float rGain = (panV >= 0.f) ? 1.f : (1.f + panV);

                float rr = rateStart;
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
                    float ev = loopSonicLastVol[vi] * v.envGain;

                    long p1 = (long)pos;                       // target frame
                    if (p1 + 2 >= loopRingTotal[vi]) break;    // cubic needs p3 valid
                    long p0 = p1 - 1, p2 = p1 + 1, p3 = p1 + 2;
                    float frac = pos - (float)p1;
                    // Ring slot for a frame index (absolute ringTotal-space → slot)
                    long q0 = ringBase + ((p0 % RING_CAP + RING_CAP) % RING_CAP) * 2;
                    long q1 = ringBase + ((p1 % RING_CAP + RING_CAP) % RING_CAP) * 2;
                    long q2 = ringBase + ((p2 % RING_CAP + RING_CAP) % RING_CAP) * 2;
                    long q3 = ringBase + ((p3 % RING_CAP + RING_CAP) % RING_CAP) * 2;
                    // Catmull-Rom cubic — C1 continuous, removes linear aliasing
                    float m0 = 0.5f * (loopRing[q2] - loopRing[q0]);
                    float m1 = 0.5f * (loopRing[q3] - loopRing[q1]);
                    float L  = loopRing[q1] * (2.f*frac*frac*frac - 3.f*frac*frac + 1.f)
                             + loopRing[q2] * (3.f*frac*frac - 2.f*frac*frac*frac)
                             + m0 * (frac*frac*frac - 2.f*frac*frac + frac)
                             + m1 * (frac*frac*frac - frac*frac);
                    float R = L;
                    if (loopCh > 1) {
                        m0 = 0.5f * (loopRing[q2 + 1] - loopRing[q0 + 1]);
                        m1 = 0.5f * (loopRing[q3 + 1] - loopRing[q1 + 1]);
                        R  = loopRing[q1 + 1] * (2.f*frac*frac*frac - 3.f*frac*frac + 1.f)
                           + loopRing[q2 + 1] * (3.f*frac*frac - 2.f*frac*frac*frac)
                           + m0 * (frac*frac*frac - 2.f*frac*frac + frac)
                           + m1 * (frac*frac*frac - frac*frac);
                    }

                    if (nCh == 2) {
                        out[2 * i]     += L * ev * lGain;
                        out[2 * i + 1] += R * ev * rGain;
                    } else {
                        out[i] += (loopCh > 1 ? (L + R) * 0.5f : L) * ev;
                    }
                    pos += rr;                    // glide: advance changes per sample
                    rr  += dRate;
                }
                loopPitchPos[vi] = pos;
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

                int    ch        = pb.channels;                         // 1 or 2
                size_t numFrmPb  = pb.pcm.size() / (size_t)ch;         // total frames in pad

                // Pan law: linear balance (center=1/1, full-L=1/0, full-R=0/1)
                float panV  = v.pan.load(std::memory_order_relaxed);
                float lGain = (panV <= 0.f) ? 1.f : (1.f - panV);
                float rGain = (panV >= 0.f) ? 1.f : (1.f + panV);

                for (int i = 0; i < numFrames; i++) {
                    float fpos = (float)v.position + v.pitchAcc;
                    int   ipos = (int)fpos;
                    float frac = fpos - ipos;

                    if ((size_t)ipos >= numFrmPb) {
                        v.active.store(false, std::memory_order_relaxed);
                        break;
                    }

                    // Read interleaved stereo (or mono) sample pair at frame ipos
                    float s0L = pb.pcm[(size_t)ipos * ch];
                    float s0R = (ch > 1) ? pb.pcm[(size_t)ipos * ch + 1] : s0L;
                    float s1L = ((size_t)(ipos + 1) < numFrmPb) ? pb.pcm[(size_t)(ipos + 1) * ch]     : 0.f;
                    float s1R = (ch > 1 && (size_t)(ipos + 1) < numFrmPb)
                                    ? pb.pcm[(size_t)(ipos + 1) * ch + 1] : s1L;
                    float sampL = s0L + frac * (s1L - s0L);
                    float sampR = s0R + frac * (s1R - s0R);

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
                    float ev = vol * v.envGain;
                    sampL *= ev;
                    sampR *= ev;

                    // 3-band tone EQ — separate biquad state per channel so L/R
                    // retain their independent filter history (preserves stereo image).
                    if (v.eqOn) {
                        sampL = biquadProcess(v.eqLowC,  v.eqLowSL,  sampL);
                        sampL = biquadProcess(v.eqMidC,  v.eqMidSL,  sampL);
                        sampL = biquadProcess(v.eqHighC, v.eqHighSL, sampL);
                        sampR = biquadProcess(v.eqLowC,  v.eqLowSR,  sampR);
                        sampR = biquadProcess(v.eqMidC,  v.eqMidSR,  sampR);
                        sampR = biquadProcess(v.eqHighC, v.eqHighSR, sampR);
                    }

                    // Delay: write mono mix (L+R)/2 to delay line so the delay
                    // buffer size stays at its current single-channel allocation.
                    // Echo is added equally to both output channels — musically correct.
                    int   dw        = delayWrite[vi];
                    float sampMono  = (sampL + sampR) * 0.5f;
                    delayBuf[vi][(dw + i) % DELAY_BUF_SIZE] = sampMono;

                    float echoMono = 0.f;
                    if (v.delayOn && v.delayOffset > 0 && v.delayOffset < DELAY_BUF_SIZE) {
                        int ri = ((dw + i - v.delayOffset) % DELAY_BUF_SIZE
                                  + DELAY_BUF_SIZE) % DELAY_BUF_SIZE;
                        echoMono = delayBuf[vi][ri] * v.delayLevel;
                    }

                    // Mix to output — apply pan law to L/R (mono output: pan ignored).
                    if (nCh == 2) {
                        out[2 * i]     += (sampL + echoMono) * lGain;
                        out[2 * i + 1] += (sampR + echoMono) * rGain;
                    } else {
                        out[i] += sampMono + echoMono;
                    }

                    // Advance position (in frames) by combined speed×pitch rate
                    v.pitchAcc += rate - 1.f;
                    int extra = (int)v.pitchAcc;
                    v.pitchAcc -= extra;
                    v.position += 1 + extra;
                    if (v.position >= numFrmPb) {
                        v.active.store(false, std::memory_order_relaxed);
                        break;
                    }
                }

                // Advance this voice's own delay-line write cursor by a full
                // callback's worth of frames, regardless of whether the loop
                // above broke early (sample ended) — keeps its ring buffer
                // position consistent across callbacks.
                delayWrite[vi] = (delayWrite[vi] + numFrames) % DELAY_BUF_SIZE;
            }
        }

        // Soft saturation (tanh) applied to the full output buffer.
        {
            int outSamples = numFrames * nCh;
            for (int i = 0; i < outSamples; i++) {
                out[i] = tanhf(out[i]);
            }
        }

        // ── Global 3-band EQ (master bus) ──────────────────────────────────
        // Applied to the final mixed output after soft saturation. Uses the
        // same biquad filters as per-pad EQ. Recomputes coefficients when
        // dB values change (gEqDirty flag). Processes L/R independently.
        if (gEqDirty) {
            float lo = gEqLowDB.load(std::memory_order_relaxed);
            float mi = gEqMidDB.load(std::memory_order_relaxed);
            float hi = gEqHighDB.load(std::memory_order_relaxed);
            gEqLowC  = makeLowShelf ((float)sampleRate,  150.f, lo);
            gEqMidC  = makePeaking  ((float)sampleRate, 1000.f, mi, 0.9f);
            gEqHighC = makeHighShelf((float)sampleRate, 6000.f, hi);
            gEqDirty = false;
        }
        if (nCh == 2) {
            for (int i = 0; i < numFrames; i++) {
                out[i * 2]     = biquadProcess(gEqLowC,  gEqLowSL,  biquadProcess(gEqMidC,  gEqMidSL,  biquadProcess(gEqHighC, gEqHighSL, out[i * 2])));
                out[i * 2 + 1] = biquadProcess(gEqLowC,  gEqLowSR,  biquadProcess(gEqMidC,  gEqMidSR,  biquadProcess(gEqHighC, gEqHighSR, out[i * 2 + 1])));
            }
        }

        // ── Internal/system-audio recording tap ─────────────────────────────
        // Captures a mono downmix (L+R)/2 so the existing mono WAV writer
        // and track-length accounting remain correct regardless of stream channels.
        if (recordActive.load(std::memory_order_relaxed)) {
            size_t pos = recordWritePos.load(std::memory_order_relaxed);
            size_t cap = recordCapacity;
            float* dst = recordBuffer.data();
            int n = numFrames;
            if (pos + (size_t)n > cap) n = (int)(cap > pos ? cap - pos : 0);
            if (nCh == 2) {
                for (int i = 0; i < n; i++)
                    dst[pos + i] = (out[2 * i] + out[2 * i + 1]) * 0.5f;
            } else {
                for (int i = 0; i < n; i++) dst[pos + i] = out[i];
            }
            recordWritePos.store(pos + (size_t)n, std::memory_order_relaxed);
        }
        // NOTE: The old "Mono → Stereo expansion" block has been removed.
        // Voices now write directly to out[2*i]/out[2*i+1] during mixing,
        // preserving each pad's true stereo image from the decoded source.

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
                // Match Sonic's channel count to the loaded pad so stereo pads
                // get interleaved L/R output from Sonic (not mono dup).
                int loopCh = (c.padIdx >= 0 && c.padIdx < MAX_PADS)
                             ? pads[c.padIdx].channels : 1;
                if (loopCh < 1 || loopCh > 2) loopCh = 1;
                if (loopSonic[vi]) sonicDestroyStream(loopSonic[vi]);
                loopSonic[vi]          = sonicCreateStream(sampleRate, loopCh);
                loopSonicChannels[vi]  = loopCh;
                if (loopSonic[vi]) {
                    // Sonic runs at the compensated speed (tempo S ÷ pitch P) so
                    // that the engine ring resampler's ×P restores tempo = S.
                    float cSpd = (c.pitch > 0.01f) ? c.speed / c.pitch : c.speed;
                    if (cSpd < 0.15f) cSpd = 0.15f;
                    if (cSpd > 6.0f)  cSpd = 6.0f;
                    sonicSetSpeed(loopSonic[vi], cSpd);
                    sonicSetPitch(loopSonic[vi], 1.0f);  // Sonic = speed ONLY; pitch is engine-side
                    loopSonicLastSpeed[vi] = c.speed;
                    loopSonicLastVol[vi]   = c.volume;
                    loopSonicLastPan[vi]   = c.pan;
                    loopPitchRamp[vi]      = c.pitch;
                    loopPitchPos[vi]       = 0.f;
                    loopRingTotal[vi]      = 0;
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
            v.pan   .store(c.pan,    std::memory_order_relaxed);
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

            // 3-band EQ: low-shelf @150Hz, mid-peak @1kHz (Q=0.9), high-shelf @6kHz.
            // Coefficients only need recomputing once per note-on (not per-sample).
            v.eqOn = (c.eqLow != 0.f || c.eqMid != 0.f || c.eqHigh != 0.f);
            if (v.eqOn) {
                const float sr2 = (float)sampleRate;
                v.eqLowC  = makeLowShelf (sr2,  150.f, c.eqLow);
                v.eqMidC  = makePeaking  (sr2, 1000.f, c.eqMid, 0.9f);
                v.eqHighC = makeHighShelf(sr2, 6000.f, c.eqHigh);
            }
            v.eqLowSL = v.eqLowSR = BiquadState{};
            v.eqMidSL = v.eqMidSR = BiquadState{};
            v.eqHighSL = v.eqHighSR = BiquadState{};

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

        // Smooth release: instead of hard-killing the voice, engage the existing
        // per-voice release envelope. The render loop already decrements envGain
        // by releaseRate per sample and deactivates the voice when it reaches 0,
        // giving a click-free 100-200 ms fade-out (used by Smooth Pad Transition).
        case CMD_RELEASE_PAD:
            for (auto& v : voices)
                if (v.active.load() && v.padIndex == c.padIdx && !v.releasing) {
                    v.releasing  = true;
                    v.releaseRate = (c.releaseMs > 0.f)
                            ? (1.f / (c.releaseMs * (float)sampleRate / 1000.f))
                            : 1.f;   // tiny positive rate → near-instant fade
                }
            break;

        case CMD_STOP_ALL:
            for (auto& v : voices)
                v.active.store(false, std::memory_order_relaxed);
            break;

        case CMD_UPDATE_SPEED_PITCH:
            // Live speed/pitch update — update TARGET atomics ONLY.
            //
            // For loop voices: DO NOT call sonicSetSpeed/sonicSetPitch here. The
            // render loop ramps speed (WSOLA) toward the target with gentle steps
            // and ramps the engine-side pitch target; both take effect on the
            // next callback via Sonic (speed) and the ring resampler (pitch).
            //
            // For drum/one-shot voices: v.speed and v.pitch are read directly
            // in the render loop (linear resampling only). Updating them here
            // takes effect on the very next callback.

            // ── Update active loop voice at slot c.padIdx ──
            if (c.padIdx >= 0 && c.padIdx < LOOP_VOICES) {
                Voice& v = voices[c.padIdx];
                if (v.active.load() && v.isLoop) {
                    v.speed .store(c.speed,  std::memory_order_relaxed);
                    v.pitch .store(c.pitch,  std::memory_order_relaxed);
                    v.volume.store(c.volume, std::memory_order_relaxed);
                    v.pan   .store(c.pan,    std::memory_order_relaxed);
                }
            }
            // ── Update active drum/one-shot voice with this pad index ──
            // Drum voices are allocated at indices LOOP_VOICES..NUM_VOICES-1.
            // They are identified by padIndex (pad number), not voice slot.
            // Update ALL active drum voices for this pad (rapid overlapping
            // hits may have multiple active voices without choke groups).
            for (int dvi = LOOP_VOICES; dvi < NUM_VOICES; dvi++) {
                Voice& dv = voices[dvi];
                if (dv.active.load() && dv.padIndex == c.padIdx && !dv.isLoop) {
                    dv.speed .store(c.speed,  std::memory_order_relaxed);
                    dv.pitch .store(c.pitch,  std::memory_order_relaxed);
                    dv.volume.store(c.volume, std::memory_order_relaxed);
                    dv.pan   .store(c.pan,    std::memory_order_relaxed);
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
        memset(delayBuf, 0, sizeof(delayBuf));
        memset(delayWrite, 0, sizeof(delayWrite));

        // Reinitialize Sonic streams with the new sample rate.
        // Also reset loopSonicLastSpeed/Pitch to 1.0 so the render loop's
        // smooth-ramp comparisons match the freshly created streams' default
        // state (speed=1.0, pitch=1.0). Without this reset, after an Oboe
        // error/restart the render loop sees "already at target" but the new
        // stream is at default 1.0 → wrong playback speed.
        for (int i = 0; i < LOOP_VOICES; i++) {
            if (loopSonic[i]) sonicDestroyStream(loopSonic[i]);
            loopSonic[i] = sonicCreateStream(sampleRate, 1);
            loopSonicLastSpeed[i] = 1.0f;
            loopSonicLastVol[i]   = 1.0f;
            loopSonicLastPan[i]   = 0.0f;
            loopPitchRamp[i]      = 1.0f;
            loopPitchPos[i]       = 0.0f;
            loopRingTotal[i]      = 0;
            loopSonicChannels[i]  = 1; // reset; updated at next CMD_PLAY
        }

        oboe::AudioStreamBuilder b;
        // bufferCapacity: 3 bursts — gives the scheduler headroom without inflating latency
        // bufferSize (set after open): 1 burst — absolute minimum latency
        // setUsage(Game) + setContentType(Music): tells Android HAL this is a
        // real-time instrument app → OS scheduler gives audio thread highest
        // priority, reduces wakeup jitter by 3–8ms on many devices.
        oboe::Result r = b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(2)            // stereo: same mono mix on L + R
            ->setSampleRate(sampleRate)            // MATCH device native SR — no resampling
            ->setFramesPerCallback(framesPerBurst) // MATCH hardware burst — no extra buffering
            ->setBufferCapacityInFrames(framesPerBurst * 3)
            ->setUsage(oboe::Usage::Game)          // real-time instrument → max scheduler priority
            ->setContentType(oboe::ContentType::Music)
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

        // Store Oboe's assigned audio session ID so Java Equalizer can attach
        // to this stream instead of the default session 0 (which bypasses Oboe).
        audioSessionId = stream->getSessionId();
        LOGI("Audio session ID: %d", audioSessionId);

        // Store the actual channel count Oboe negotiated with the device.
        // We request 2 but some devices silently open as 1 in exclusive mode.
        streamChannels = stream->getChannelCount();

        r = stream->start();
        if (r != oboe::Result::OK) { LOGE("stream start: %s", oboe::convertToText(r)); return false; }

        LOGI("Oboe OK — rate=%d burst=%d bufSize=%d cap=%d ch=%d api=%s sharing=%s",
             stream->getSampleRate(),
             stream->getFramesPerBurst(),
             stream->getBufferSizeInFrames(),
             stream->getBufferCapacityInFrames(),
             streamChannels,
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

    // numFrames = number of audio frames (samples per channel).
    // channels  = 1 (mono) or 2 (stereo); data is interleaved [L0,R0,L1,R1,...].
    void loadSample(int padIdx, const short* data, int numFrames, int channels) {
        if (padIdx < 0 || padIdx >= MAX_PADS || !data || numFrames <= 0) return;
        channels = (channels < 1) ? 1 : (channels > 2 ? 2 : channels);
        int totalSamples = numFrames * channels;
        std::vector<float> buf(totalSamples);
        for (int i = 0; i < totalSamples; i++)
            buf[i] = data[i] / 32768.0f;
        pads[padIdx].channels = channels;
        pads[padIdx].loaded.store(false, std::memory_order_release);
        pads[padIdx].pcm = std::move(buf);
        pads[padIdx].loaded.store(true,  std::memory_order_release);
        LOGI("Loaded pad %d: %d frames %dch", padIdx, numFrames, channels);
    }

    // speed: time-stretch factor (1.0 = normal speed, independent of pitch)
    // pitch: pitch-shift factor  (1.0 = normal pitch, independent of speed)
    void playSample(int padIdx, float volume, float speed, float pitch,
                    bool delayOn, float delayMs, float delayLevel,
                    float eqLow, float eqMid, float eqHigh,
                    int chokeGroup, float attackMs, float releaseMs,
                    float pan, bool isLoop) {
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
        // eqLow/eqMid/eqHigh arrive as dB-style gains (~-15..+15); clamp defensively
        // so a malformed value can't blow up the biquad coefficient math.
        c.eqLow      = std::max(-24.f, std::min(24.f, eqLow));
        c.eqMid      = std::max(-24.f, std::min(24.f, eqMid));
        c.eqHigh     = std::max(-24.f, std::min(24.f, eqHigh));
        c.chokeGroup = chokeGroup;
        c.attackMs   = attackMs;
        c.releaseMs  = releaseMs;
        c.pan        = std::max(-1.f, std::min(1.f, pan));
        c.isLoop     = isLoop;
        cmdQ.push(c);
    }

    void playLoopSP(int padIdx, float volume, float speed, float pitchShift, float pan = 0.f) {
        if (padIdx >= 0 && padIdx < LOOP_VOICES) {
            playSample(padIdx, volume, speed, pitchShift, false, 0.f, 0.f, 0.f, 0.f, 0.f, 0, 0.f, 0.f, pan, true);
        }
    }

    void updateLoopSpeedPitch(int padIdx, float volume, float speed, float pitch, float pan = 0.f) {
        if (padIdx < 0 || padIdx >= LOOP_VOICES) return;
        Cmd c{};
        c.type   = CMD_UPDATE_SPEED_PITCH;
        c.padIdx = padIdx;
        c.volume = std::max(0.f, std::min(1.f, volume));
        c.speed  = std::max(0.1f, std::min(4.f, speed));
        c.pitch  = std::max(0.1f, std::min(8.f, pitch));
        c.pan    = std::max(-1.f, std::min(1.f, pan));
        cmdQ.push(c);
    }

    void stopPad(int padIdx) {
        Cmd c{};
        c.type   = CMD_STOP_PAD;
        c.padIdx = padIdx;
        cmdQ.push(c);
    }

    void releasePad(int padIdx, float releaseMs) {
        Cmd c{};
        c.type      = CMD_RELEASE_PAD;
        c.padIdx    = padIdx;
        c.releaseMs = releaseMs;
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
        JNIEnv* env, jobject obj, jint padIdx, jshortArray arr, jint numFrames, jint channels) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (!e) return;
    jshort* data = env->GetShortArrayElements(arr, nullptr);
    e->loadSample((int)padIdx, (const short*)data, (int)numFrames, (int)channels);
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
    // Legacy path: speed=1.0, pan=0.0 (kept for backward compat). New code uses nativePlaySampleSP.
    if (e) e->playSample((int)padIdx, (float)volume, 1.f, (float)pitch,
                         (bool)delayOn, (float)delayMs, (float)delayLevel,
                         (float)eqLow, (float)eqMid, (float)eqHigh,
                         (int)chokeGroup, (float)attackMs, (float)releaseMs, 0.f, false);
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
        jfloat eqLow, jfloat eqMid, jfloat eqHigh,
        jint chokeGroup, jfloat attackMs, jfloat releaseMs, jfloat pan) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playSample((int)padIdx, (float)volume, (float)speed, (float)pitch,
                         (bool)delayOn, (float)delayMs, (float)delayLevel,
                         (float)eqLow, (float)eqMid, (float)eqHigh,
                         (int)chokeGroup, (float)attackMs, (float)releaseMs,
                         (float)pan, false);
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
                         false, 0.f, 0.f, 0.f, 0.f, 0.f, 0, 0.f, 0.f, 0.f, true);
}

// nativePlayLoopSP: start loop with independent speed + pitch
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativePlayLoopSP(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat speed, jfloat pitchShift, jfloat pan) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->playLoopSP((int)padIdx, (float)volume, (float)speed, (float)pitchShift, (float)pan);
}

// nativeUpdateLoopSpeedPitch: live update speed + pitch without restarting loop
JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeUpdateLoopSpeedPitch(
        JNIEnv* env, jobject obj,
        jint padIdx, jfloat volume, jfloat speed, jfloat pitch, jfloat pan) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->updateLoopSpeedPitch((int)padIdx, (float)volume, (float)speed, (float)pitch, (float)pan);
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

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeReleasePad(JNIEnv* env, jobject obj, jint padIdx, jfloat releaseMs) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (e) e->releasePad((int)padIdx, (float)releaseMs);
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

JNIEXPORT jint JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeGetAudioSessionId(JNIEnv* env, jobject obj) {
    AudioEngineImpl* e = getEngine(env, obj);
    return e ? e->audioSessionId : 0;
}

JNIEXPORT void JNICALL
Java_com_pramod_loopmidi_AudioEngine_nativeSetGlobalEQ(
        JNIEnv* env, jobject obj, jfloat lowDB, jfloat midDB, jfloat highDB) {
    AudioEngineImpl* e = getEngine(env, obj);
    if (!e) return;
    e->gEqLowDB.store((float)lowDB,  std::memory_order_relaxed);
    e->gEqMidDB.store((float)midDB,  std::memory_order_relaxed);
    e->gEqHighDB.store((float)highDB, std::memory_order_relaxed);
    e->gEqDirty = true;
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
