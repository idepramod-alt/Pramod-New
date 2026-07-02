package com.google.firebase.concurrent;

import java.util.concurrent.Executor;
/* loaded from: classes.dex */
public interface PausableExecutor extends Executor {
    boolean isPaused();

    void pause();

    void resume();
}
