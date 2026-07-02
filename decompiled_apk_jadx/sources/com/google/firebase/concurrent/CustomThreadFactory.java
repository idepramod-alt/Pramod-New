package com.google.firebase.concurrent;

import android.os.Process;
import android.os.StrictMode;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class CustomThreadFactory implements ThreadFactory {
    private static final ThreadFactory DEFAULT = Executors.defaultThreadFactory();
    private final String namePrefix;
    private final StrictMode.ThreadPolicy policy;
    private final int priority;
    private final AtomicLong threadCount = new AtomicLong();

    /* JADX INFO: Access modifiers changed from: package-private */
    public CustomThreadFactory(String namePrefix, int priority, @Nullable StrictMode.ThreadPolicy policy) {
        this.namePrefix = namePrefix;
        this.priority = priority;
        this.policy = policy;
    }

    @Override // java.util.concurrent.ThreadFactory
    public Thread newThread(final Runnable r) {
        Thread thread = DEFAULT.newThread(new Runnable() { // from class: com.google.firebase.concurrent.CustomThreadFactory$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                CustomThreadFactory.this.m112x29e2b42(r);
            }
        });
        thread.setName(String.format(Locale.ROOT, "%s Thread #%d", this.namePrefix, Long.valueOf(this.threadCount.getAndIncrement())));
        return thread;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$newThread$0$com-google-firebase-concurrent-CustomThreadFactory  reason: not valid java name */
    public /* synthetic */ void m112x29e2b42(Runnable r) {
        Process.setThreadPriority(this.priority);
        if (this.policy != null) {
            StrictMode.setThreadPolicy(this.policy);
        }
        r.run();
    }
}
