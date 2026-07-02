package com.google.firebase.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
/* loaded from: classes.dex */
final class PausableExecutorImpl implements PausableExecutor {
    private final Executor delegate;
    private volatile boolean paused;
    final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    /* JADX INFO: Access modifiers changed from: package-private */
    public PausableExecutorImpl(boolean paused, Executor delegate) {
        this.paused = paused;
        this.delegate = delegate;
    }

    @Override // java.util.concurrent.Executor
    public void execute(Runnable command) {
        this.queue.offer(command);
        maybeEnqueueNext();
    }

    private void maybeEnqueueNext() {
        if (this.paused) {
            return;
        }
        Runnable next = this.queue.poll();
        while (next != null) {
            this.delegate.execute(next);
            if (!this.paused) {
                Runnable next2 = this.queue.poll();
                next = next2;
            } else {
                next = null;
            }
        }
    }

    @Override // com.google.firebase.concurrent.PausableExecutor
    public void pause() {
        this.paused = true;
    }

    @Override // com.google.firebase.concurrent.PausableExecutor
    public void resume() {
        this.paused = false;
        maybeEnqueueNext();
    }

    @Override // com.google.firebase.concurrent.PausableExecutor
    public boolean isPaused() {
        return this.paused;
    }
}
