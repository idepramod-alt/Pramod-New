package com.pramod.loopmidi;

public final class MidiPlaybackPolicy {
    private MidiPlaybackPolicy() {}

    public static boolean shouldEnforceSinglePadModeForMidi(
            boolean isMultiMode,
            boolean audioAlreadyTriggered,
            boolean isLoopModePath
    ) {
        return isLoopModePath && !audioAlreadyTriggered && !isMultiMode;
    }

    public static boolean shouldBlockMidiPlaybackForLockedKit(int lockedKit, int currentSpdKit) {
        return lockedKit != -1 && currentSpdKit != -1 && currentSpdKit != lockedKit;
    }
}
