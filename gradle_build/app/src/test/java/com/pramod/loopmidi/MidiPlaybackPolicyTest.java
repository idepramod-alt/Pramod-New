package com.pramod.loopmidi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MidiPlaybackPolicyTest {
    @Test
    public void midiShouldRespectSinglePadModeWhenMultiPlayIsOff() {
        // Touch input + loop mode + multi-play OFF → enforce single-pad (stop other loops)
        assertTrue(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, false, true));

        // MIDI fast path + drum mode + multi-play OFF → do NOT enforce (all simultaneous pads must play)
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, true, false));

        // MIDI fast path + drum mode + multi-play ON → do NOT enforce
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(true, true, false));

        // MIDI fast path + loop mode + multi-play OFF → do NOT enforce (midiTriggerDrumPadImmediate already handled it)
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, true, true));

        // Touch input + loop mode + multi-play ON → do NOT enforce
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(true, false, true));

        // Touch input + drum mode + multi-play OFF → do NOT enforce (drum mode never chokes other pads)
        assertFalse(MidiPlaybackPolicy.shouldEnforceSinglePadModeForMidi(false, false, false));
    }

    @Test
    public void midiKitLockShouldBlockDifferentKitNumbersButAllowUnknownKit() {
        // Lock ON + different known kit → BLOCK (SPD is on a different kit)
        assertTrue(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, 5));

        // Lock ON + correct kit → ALLOW
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, 3));

        // Lock ON + unknown kit (-1) → ALLOW
        // Live performance: app start pe SPD already locked kit pe hoga.
        // Strict block se live pe sound nahi aata jab tak kit switch na ho.
        // Lenient behavior: jab tak SPD koi aur kit send na kare, ALLOW karo.
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(3, -1));

        // Lock OFF → ALLOW (regardless of current SPD kit)
        assertFalse(MidiPlaybackPolicy.shouldBlockMidiPlaybackForLockedKit(-1, 5));
    }
}
