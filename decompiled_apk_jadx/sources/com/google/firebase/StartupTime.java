package com.google.firebase;

import android.os.SystemClock;
/* loaded from: classes.dex */
public abstract class StartupTime {
    public abstract long getElapsedRealtime();

    public abstract long getEpochMillis();

    public abstract long getUptimeMillis();

    public static StartupTime create(long epochMillis, long elapsedRealtime, long uptimeMillis) {
        return new AutoValue_StartupTime(epochMillis, elapsedRealtime, uptimeMillis);
    }

    public static StartupTime now() {
        return create(System.currentTimeMillis(), SystemClock.elapsedRealtime(), SystemClock.uptimeMillis());
    }
}
