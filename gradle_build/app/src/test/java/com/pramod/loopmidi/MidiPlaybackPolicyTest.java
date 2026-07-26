package com.pramod.loopmidi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MidiPlaybackPolicyTest {
    @Test
    public void midiShouldRespectSinglePadModeWhenMultiPlayIsOff() {
        assertTrue(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, false, true));
        assertTrue(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, true, false));
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(true, true, false));
    }

    @Test
    public void midiKitLockShouldBlockDifferentKitNumbersAndUnknownKits() {
        assertTrue(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, 5));
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, 3));
        assertTrue(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, -1));
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(-1, 5));
    }
}
