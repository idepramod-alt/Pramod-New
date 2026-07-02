package com.google.android.gms.common.stats;

import android.os.PowerManager;
import android.os.Process;
import android.text.TextUtils;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
@Deprecated
/* loaded from: classes.dex */
public class StatsUtils {
    public static String getEventKey(PowerManager.WakeLock wakeLock, String secondaryName) {
        String valueOf = String.valueOf((Process.myPid() << 32) | System.identityHashCode(wakeLock));
        if (true == TextUtils.isEmpty(secondaryName)) {
            secondaryName = "";
        }
        return String.valueOf(valueOf).concat(String.valueOf(secondaryName));
    }
}
