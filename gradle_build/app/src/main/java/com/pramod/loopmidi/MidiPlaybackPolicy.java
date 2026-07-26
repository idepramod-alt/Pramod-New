package com.pramod.loopmidi;

public final class MidiPlaybackPolicy {
    private MidiPlaybackPolicy() {}

    public static boolean shouldEnforceSinglePadModeForMidi(
            boolean isMultiMode,
            boolean audioAlreadyTriggered,
            boolean isLoopModePath
    ) {
        // MIDI input should behave like single-trigger input when Multi-Play is off:
        // - loop/one-shot mode: stop the previous pad on the next MIDI hit
        // - drum mode: also stop the previous pad for MIDI so only one pad plays at a time
        return !isMultiMode && (audioAlreadyTriggered || isLoopModePath);
    }

    public static boolean shouldBlockMidiPlaybackForLockedKit(int lockedKit, int currentSpdKit) {
        // Strict lock behavior:
        // - if lock is OFF, allow playback
        // - if lock is ON and current SPD kit is unknown, block playback for safety
        // - if lock is ON and current SPD kit differs from the locked kit, block playback
        return lockedKit != -1 && (currentSpdKit == -1 || currentSpdKit != lockedKit);
    }
}
