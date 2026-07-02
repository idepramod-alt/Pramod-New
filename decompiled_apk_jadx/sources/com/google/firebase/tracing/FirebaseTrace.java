package com.google.firebase.tracing;

import android.os.Trace;
/* loaded from: classes.dex */
public final class FirebaseTrace {
    private FirebaseTrace() {
    }

    public static void pushTrace(String name) {
        Trace.beginSection(name);
    }

    public static void popTrace() {
        Trace.endSection();
    }
}
