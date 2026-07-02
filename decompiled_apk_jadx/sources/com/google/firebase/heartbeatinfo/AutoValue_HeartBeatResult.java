package com.google.firebase.heartbeatinfo;

import java.util.List;
/* loaded from: classes.dex */
final class AutoValue_HeartBeatResult extends HeartBeatResult {
    private final List<String> usedDates;
    private final String userAgent;

    /* JADX INFO: Access modifiers changed from: package-private */
    public AutoValue_HeartBeatResult(String userAgent, List<String> usedDates) {
        if (userAgent == null) {
            throw new NullPointerException("Null userAgent");
        }
        this.userAgent = userAgent;
        if (usedDates == null) {
            throw new NullPointerException("Null usedDates");
        }
        this.usedDates = usedDates;
    }

    @Override // com.google.firebase.heartbeatinfo.HeartBeatResult
    public String getUserAgent() {
        return this.userAgent;
    }

    @Override // com.google.firebase.heartbeatinfo.HeartBeatResult
    public List<String> getUsedDates() {
        return this.usedDates;
    }

    public String toString() {
        return "HeartBeatResult{userAgent=" + this.userAgent + ", usedDates=" + this.usedDates + "}";
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof HeartBeatResult) {
            HeartBeatResult that = (HeartBeatResult) o;
            return this.userAgent.equals(that.getUserAgent()) && this.usedDates.equals(that.getUsedDates());
        }
        return false;
    }

    public int hashCode() {
        int h$ = 1 * 1000003;
        return ((h$ ^ this.userAgent.hashCode()) * 1000003) ^ this.usedDates.hashCode();
    }
}
