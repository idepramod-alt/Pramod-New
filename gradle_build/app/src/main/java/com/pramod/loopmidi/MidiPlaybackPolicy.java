package com.pramod.loopmidi;

public final class MidiPlaybackPolicy {
    private MidiPlaybackPolicy() {}

    public static boolean shouldEnforceSinglePadModeForMidi(
            boolean isMultiMode,
            boolean audioAlreadyTriggered,
            boolean isLoopModePath
    ) {
        // Single-pad enforcement sirf tab karo jab:
        //   1. Multi-Play button OFF ho (!isMultiMode)
        //   2. Touch input ho — MIDI fast path NE audio pehle se fire NAHI kiya (!audioAlreadyTriggered)
        //   3. Loop/one-shot mode ho — Drum mode mein kabhi bhi choke nahi hona chahiye
        //
        // Jab audioAlreadyTriggered=true hota hai (MIDI fast path), tab tak sab simultaneously
        // triggered pads ka audio midiTriggerDrumPadImmediate() mein fire ho chuka hota hai.
        // UI thread pe aakar unhe band karna galat hai — isliye MIDI path pe enforcement nahi.
        // Loop mode mein single-pad enforcement midiTriggerDrumPadImmediate() mein already ho
        // chuki hoti hai; yahan dobara karna jaruri nahi.
        // Real drum mode must stay polyphonic, so MIDI drum hits should not choke each other.
        return !isMultiMode && !audioAlreadyTriggered && isLoopModePath;
    }

    public static boolean shouldBlockMidiPlaybackForLockedKit(int lockedKit, int currentSpdKit) {
        // Live-performance lock behavior:
        // - if lock is OFF (-1), always allow playback
        // - if lock is ON and current SPD kit is unknown (-1), ALLOW playback.
        //   Reason: app start pe SPD Program Change nahi aaya hoga, par SPD already
        //   locked kit pe hoga. Strict block karne se live pe sound nahi aata jab tak
        //   user SPD pe kit switch na kare — yeh unacceptable hai.
        // - if lock is ON and current SPD kit matches locked kit, allow playback
        // - if lock is ON and current SPD kit is a DIFFERENT known kit, block playback
        //   (SPD apni original sounds bajata rahega, app silent rahega)
        return lockedKit != -1 && currentSpdKit != -1 && currentSpdKit != lockedKit;
    }
}
