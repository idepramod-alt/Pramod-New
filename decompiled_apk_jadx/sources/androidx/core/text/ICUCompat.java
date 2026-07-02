package androidx.core.text;

import android.icu.util.ULocale;
import android.os.Build;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
/* loaded from: classes.dex */
public final class ICUCompat {
    private static final String TAG = "ICUCompat";
    private static Method sAddLikelySubtagsMethod;
    private static Method sGetScriptMethod;

    static {
        if (Build.VERSION.SDK_INT < 24) {
            try {
                Class<?> clazz = Class.forName("libcore.icu.ICU");
                sAddLikelySubtagsMethod = clazz.getMethod("addLikelySubtags", Locale.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static String maximizeAndGetScript(Locale locale) {
        if (Build.VERSION.SDK_INT >= 24) {
            Object uLocale = Api24Impl.addLikelySubtags(Api24Impl.forLocale(locale));
            return Api24Impl.getScript(uLocale);
        }
        try {
            Object[] args = {locale};
            return Api21Impl.getScript((Locale) sAddLikelySubtagsMethod.invoke(null, args));
        } catch (IllegalAccessException e) {
            Log.w(TAG, e);
            return Api21Impl.getScript(locale);
        } catch (InvocationTargetException e2) {
            Log.w(TAG, e2);
            return Api21Impl.getScript(locale);
        }
    }

    private static String getScriptBelowApi21(String localeStr) {
        try {
            if (sGetScriptMethod != null) {
                Object[] args = {localeStr};
                return (String) sGetScriptMethod.invoke(null, args);
            }
        } catch (IllegalAccessException e) {
            Log.w(TAG, e);
        } catch (InvocationTargetException e2) {
            Log.w(TAG, e2);
        }
        return null;
    }

    private static String addLikelySubtagsBelowApi21(Locale locale) {
        String localeStr = locale.toString();
        try {
            if (sAddLikelySubtagsMethod != null) {
                Object[] args = {localeStr};
                return (String) sAddLikelySubtagsMethod.invoke(null, args);
            }
        } catch (IllegalAccessException e) {
            Log.w(TAG, e);
        } catch (InvocationTargetException e2) {
            Log.w(TAG, e2);
        }
        return localeStr;
    }

    private ICUCompat() {
    }

    /* loaded from: classes.dex */
    static class Api24Impl {
        private Api24Impl() {
        }

        static ULocale forLocale(Locale loc) {
            return ULocale.forLocale(loc);
        }

        static ULocale addLikelySubtags(Object loc) {
            return ULocale.addLikelySubtags((ULocale) loc);
        }

        static String getScript(Object uLocale) {
            return ((ULocale) uLocale).getScript();
        }
    }

    /* loaded from: classes.dex */
    static class Api21Impl {
        private Api21Impl() {
        }

        static String getScript(Locale locale) {
            return locale.getScript();
        }
    }
}
