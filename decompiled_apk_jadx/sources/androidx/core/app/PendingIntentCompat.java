package androidx.core.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
/* loaded from: classes.dex */
public final class PendingIntentCompat {

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes.dex */
    public @interface Flags {
    }

    public static PendingIntent getActivities(Context context, int requestCode, Intent[] intents, int flags, Bundle options, boolean isMutable) {
        return Api16Impl.getActivities(context, requestCode, intents, addMutabilityFlags(isMutable, flags), options);
    }

    public static PendingIntent getActivities(Context context, int requestCode, Intent[] intents, int flags, boolean isMutable) {
        return PendingIntent.getActivities(context, requestCode, intents, addMutabilityFlags(isMutable, flags));
    }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags, boolean isMutable) {
        return PendingIntent.getActivity(context, requestCode, intent, addMutabilityFlags(isMutable, flags));
    }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags, Bundle options, boolean isMutable) {
        return Api16Impl.getActivity(context, requestCode, intent, addMutabilityFlags(isMutable, flags), options);
    }

    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags, boolean isMutable) {
        return PendingIntent.getBroadcast(context, requestCode, intent, addMutabilityFlags(isMutable, flags));
    }

    public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags, boolean isMutable) {
        return Api26Impl.getForegroundService(context, requestCode, intent, addMutabilityFlags(isMutable, flags));
    }

    public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags, boolean isMutable) {
        return PendingIntent.getService(context, requestCode, intent, addMutabilityFlags(isMutable, flags));
    }

    private static int addMutabilityFlags(boolean isMutable, int flags) {
        if (isMutable) {
            if (Build.VERSION.SDK_INT >= 31) {
                return flags | 33554432;
            }
            return flags;
        }
        return flags | 67108864;
    }

    private PendingIntentCompat() {
    }

    /* loaded from: classes.dex */
    private static class Api16Impl {
        private Api16Impl() {
        }

        public static PendingIntent getActivities(Context context, int requestCode, Intent[] intents, int flags, Bundle options) {
            return PendingIntent.getActivities(context, requestCode, intents, flags, options);
        }

        public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags, Bundle options) {
            return PendingIntent.getActivity(context, requestCode, intent, flags, options);
        }
    }

    /* loaded from: classes.dex */
    private static class Api26Impl {
        private Api26Impl() {
        }

        public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags) {
            return PendingIntent.getForegroundService(context, requestCode, intent, flags);
        }
    }
}
