package com.google.android.gms.common.logging;

import android.util.Log;
import com.google.android.gms.common.internal.GmsLogger;
import java.util.Locale;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public class Logger {
    private final String zza;
    private final String zzb;
    private final GmsLogger zzc;
    private final int zzd;

    public Logger(String tag, String... categories) {
        String sb;
        if (categories.length == 0) {
            sb = "";
        } else {
            StringBuilder sb2 = new StringBuilder();
            sb2.append('[');
            for (String str : categories) {
                if (sb2.length() > 1) {
                    sb2.append(",");
                }
                sb2.append(str);
            }
            sb2.append("] ");
            sb = sb2.toString();
        }
        this.zzb = sb;
        this.zza = tag;
        this.zzc = new GmsLogger(tag);
        int i = 2;
        while (i <= 7 && !Log.isLoggable(this.zza, i)) {
            i++;
        }
        this.zzd = i;
    }

    public void d(String msg, Object... optionalFormatArgs) {
        if (isLoggable(3)) {
            Log.d(this.zza, format(msg, optionalFormatArgs));
        }
    }

    public void e(String msg, Throwable tr, Object... optionalFormatArgs) {
        Log.e(this.zza, format(msg, optionalFormatArgs), tr);
    }

    protected String format(String message, Object... optionalFormatArgs) {
        if (optionalFormatArgs != null && optionalFormatArgs.length > 0) {
            message = String.format(Locale.US, message, optionalFormatArgs);
        }
        return this.zzb.concat(message);
    }

    public String getTag() {
        return this.zza;
    }

    public void i(String msg, Object... optionalFormatArgs) {
        Log.i(this.zza, format(msg, optionalFormatArgs));
    }

    public boolean isLoggable(int i) {
        return this.zzd <= i;
    }

    public void v(String msg, Throwable tr, Object... optionalFormatArgs) {
        if (isLoggable(2)) {
            Log.v(this.zza, format(msg, optionalFormatArgs), tr);
        }
    }

    public void w(String msg, Object... optionalFormatArgs) {
        Log.w(this.zza, format(msg, optionalFormatArgs));
    }

    public void wtf(String msg, Throwable tr, Object... optionalFormatArgs) {
        Log.wtf(this.zza, format(msg, optionalFormatArgs), tr);
    }

    public void e(String msg, Object... optionalFormatArgs) {
        Log.e(this.zza, format(msg, optionalFormatArgs));
    }

    public void wtf(Throwable tr) {
        Log.wtf(this.zza, tr);
    }

    public void v(String msg, Object... optionalFormatArgs) {
        if (isLoggable(2)) {
            Log.v(this.zza, format(msg, optionalFormatArgs));
        }
    }
}
