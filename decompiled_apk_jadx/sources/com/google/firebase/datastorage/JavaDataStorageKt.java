package com.google.firebase.datastorage;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.datastore.preferences.core.Preferences;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
/* compiled from: JavaDataStorage.kt */
@Metadata(d1 = {"\u0000\u0012\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u001a+\u0010\u0000\u001a\u0002H\u0001\"\u0004\b\u0000\u0010\u0001*\u00020\u00022\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u0002H\u00010\u00042\u0006\u0010\u0005\u001a\u0002H\u0001¢\u0006\u0002\u0010\u0006¨\u0006\u0007"}, d2 = {"getOrDefault", "T", "Landroidx/datastore/preferences/core/Preferences;", "key", "Landroidx/datastore/preferences/core/Preferences$Key;", "defaultValue", "(Landroidx/datastore/preferences/core/Preferences;Landroidx/datastore/preferences/core/Preferences$Key;Ljava/lang/Object;)Ljava/lang/Object;", "com.google.firebase-firebase-common"}, k = 2, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class JavaDataStorageKt {
    public static final <T> T getOrDefault(Preferences $this$getOrDefault, Preferences.Key<T> key, T t) {
        Intrinsics.checkNotNullParameter($this$getOrDefault, "<this>");
        Intrinsics.checkNotNullParameter(key, "key");
        T t2 = (T) $this$getOrDefault.get(key);
        return t2 == null ? t : t2;
    }
}
