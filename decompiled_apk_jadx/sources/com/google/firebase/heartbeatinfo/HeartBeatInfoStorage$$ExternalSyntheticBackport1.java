package com.google.firebase.heartbeatinfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
/* compiled from: D8$$SyntheticClass */
/* loaded from: classes.dex */
public final /* synthetic */ class HeartBeatInfoStorage$$ExternalSyntheticBackport1 {
    public static /* synthetic */ Set m(Object[] objArr) {
        HashSet hashSet = new HashSet(objArr.length);
        for (Object obj : objArr) {
            if (!hashSet.add(Objects.requireNonNull(obj))) {
                throw new IllegalArgumentException("duplicate element: " + obj);
            }
        }
        return Collections.unmodifiableSet(hashSet);
    }
}
