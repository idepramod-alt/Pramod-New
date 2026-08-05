package com.pramod.audioeditor.data;

/**
 * 4-band EQ settings. All values in dB, range ±10.
 */
public class EqSettings {
    public float lowDb = 0f;    // Low shelf (200 Hz)
    public float midDb = 0f;    // Peaking (1 kHz)
    public float highDb = 0f;   // High shelf (4 kHz)
    public float gainDb = 0f;   // Master gain
    public float volumeDb = 0f; // Output volume
    public boolean bypass = false;
    public boolean clipGuard = true;

    public EqSettings copy() {
        EqSettings s = new EqSettings();
        s.lowDb = lowDb;
        s.midDb = midDb;
        s.highDb = highDb;
        s.gainDb = gainDb;
        s.volumeDb = volumeDb;
        s.bypass = bypass;
        s.clipGuard = clipGuard;
        return s;
    }

    /** Compute auto gain compensation to prevent clipping. */
    public float autoGainDb() {
        float totalBoost = Math.max(0, lowDb) + Math.max(0, midDb) + Math.max(0, highDb);
        return totalBoost > 0 ? -totalBoost * 0.5f : 0f;
    }

    public boolean isFlat() {
        return Math.abs(lowDb) < 0.01f && Math.abs(midDb) < 0.01f
                && Math.abs(highDb) < 0.01f && Math.abs(gainDb) < 0.01f;
    }
}
