package com.google.firebase;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.jvm.internal.PropertyReference1Impl;
/* compiled from: Timestamp.kt */
@Metadata(k = 3, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
/* synthetic */ class Timestamp$compareTo$2 extends PropertyReference1Impl {
    public static final Timestamp$compareTo$2 INSTANCE = new Timestamp$compareTo$2();

    Timestamp$compareTo$2() {
        super(Timestamp.class, "nanoseconds", "getNanoseconds()I", 0);
    }

    @Override // kotlin.jvm.internal.PropertyReference1Impl, kotlin.reflect.KProperty1
    public Object get(Object receiver0) {
        return Integer.valueOf(((Timestamp) receiver0).getNanoseconds());
    }
}
