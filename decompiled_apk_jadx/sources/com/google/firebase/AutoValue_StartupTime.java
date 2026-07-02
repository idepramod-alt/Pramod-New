package com.google.firebase;
/* loaded from: classes.dex */
final class AutoValue_StartupTime extends StartupTime {
    private final long elapsedRealtime;
    private final long epochMillis;
    private final long uptimeMillis;

    /* JADX INFO: Access modifiers changed from: package-private */
    public AutoValue_StartupTime(long epochMillis, long elapsedRealtime, long uptimeMillis) {
        this.epochMillis = epochMillis;
        this.elapsedRealtime = elapsedRealtime;
        this.uptimeMillis = uptimeMillis;
    }

    @Override // com.google.firebase.StartupTime
    public long getEpochMillis() {
        return this.epochMillis;
    }

    @Override // com.google.firebase.StartupTime
    public long getElapsedRealtime() {
        return this.elapsedRealtime;
    }

    @Override // com.google.firebase.StartupTime
    public long getUptimeMillis() {
        return this.uptimeMillis;
    }

    public String toString() {
        return "StartupTime{epochMillis=" + this.epochMillis + ", elapsedRealtime=" + this.elapsedRealtime + ", uptimeMillis=" + this.uptimeMillis + "}";
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof StartupTime) {
            StartupTime that = (StartupTime) o;
            return this.epochMillis == that.getEpochMillis() && this.elapsedRealtime == that.getElapsedRealtime() && this.uptimeMillis == that.getUptimeMillis();
        }
        return false;
    }

    public int hashCode() {
        int h$ = 1 * 1000003;
        return ((((h$ ^ ((int) ((this.epochMillis >>> 32) ^ this.epochMillis))) * 1000003) ^ ((int) ((this.elapsedRealtime >>> 32) ^ this.elapsedRealtime))) * 1000003) ^ ((int) ((this.uptimeMillis >>> 32) ^ this.uptimeMillis));
    }
}
