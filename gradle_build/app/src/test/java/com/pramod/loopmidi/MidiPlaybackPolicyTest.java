package com.pramod.loopmidi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MidiPlaybackPolicyTest {
    @Test
    public void loopMidiShouldRespectSinglePadModeWhenMultiPlayIsOff() {
        assertTrue(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, false, true));
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(true, false, true));
    }

    @Test
    public void midiKitLockShouldBlockDifferentKitNumbers() {
        assertTrue(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, 5));
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, 3));
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(-1, 5));
    }
}
