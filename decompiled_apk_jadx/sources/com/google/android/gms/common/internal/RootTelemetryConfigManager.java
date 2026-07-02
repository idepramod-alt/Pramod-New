package com.google.android.gms.common.internal;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public final class RootTelemetryConfigManager {
    private static RootTelemetryConfigManager zza = null;
    private static final RootTelemetryConfiguration zzb = new RootTelemetryConfiguration(0, false, false, 0, 0);
    private RootTelemetryConfiguration zzc;

    private RootTelemetryConfigManager() {
    }

    public static synchronized RootTelemetryConfigManager getInstance() {
        RootTelemetryConfigManager rootTelemetryConfigManager;
        synchronized (RootTelemetryConfigManager.class) {
            if (zza == null) {
                zza = new RootTelemetryConfigManager();
            }
            rootTelemetryConfigManager = zza;
        }
        return rootTelemetryConfigManager;
    }

    public RootTelemetryConfiguration getConfig() {
        return this.zzc;
    }

    /* JADX WARN: Code restructure failed: missing block: B:11:0x0015, code lost:
        if (r0.getVersion() >= r3.getVersion()) goto L6;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final synchronized void zza(com.google.android.gms.common.internal.RootTelemetryConfiguration r3) {
        /*
            r2 = this;
            monitor-enter(r2)
            if (r3 != 0) goto L9
            com.google.android.gms.common.internal.RootTelemetryConfiguration r3 = com.google.android.gms.common.internal.RootTelemetryConfigManager.zzb     // Catch: java.lang.Throwable -> L18
        L5:
            r2.zzc = r3     // Catch: java.lang.Throwable -> L18
        L7:
            monitor-exit(r2)
            return
        L9:
            com.google.android.gms.common.internal.RootTelemetryConfiguration r0 = r2.zzc     // Catch: java.lang.Throwable -> L18
            if (r0 == 0) goto L17
            int r0 = r0.getVersion()     // Catch: java.lang.Throwable -> L18
            int r1 = r3.getVersion()     // Catch: java.lang.Throwable -> L18
            if (r0 >= r1) goto L7
        L17:
            goto L5
        L18:
            r3 = move-exception
            monitor-exit(r2)
            throw r3
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.gms.common.internal.RootTelemetryConfigManager.zza(com.google.android.gms.common.internal.RootTelemetryConfiguration):void");
    }
}
