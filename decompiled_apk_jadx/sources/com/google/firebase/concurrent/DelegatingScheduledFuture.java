package com.google.firebase.concurrent;

import androidx.concurrent.futures.AbstractResolvableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
/* loaded from: classes.dex */
class DelegatingScheduledFuture<V> extends AbstractResolvableFuture<V> implements ScheduledFuture<V> {
    private final ScheduledFuture<?> upstreamFuture;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public interface Completer<T> {
        void set(T t);

        void setException(Throwable th);
    }

    /* loaded from: classes.dex */
    interface Resolver<T> {
        ScheduledFuture<?> addCompleter(Completer<T> completer);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public DelegatingScheduledFuture(Resolver<V> resolver) {
        this.upstreamFuture = resolver.addCompleter(new Completer<V>() { // from class: com.google.firebase.concurrent.DelegatingScheduledFuture.1
            @Override // com.google.firebase.concurrent.DelegatingScheduledFuture.Completer
            public void set(V value) {
                DelegatingScheduledFuture.this.set(value);
            }

            @Override // com.google.firebase.concurrent.DelegatingScheduledFuture.Completer
            public void setException(Throwable ex) {
                DelegatingScheduledFuture.this.setException(ex);
            }
        });
    }

    @Override // androidx.concurrent.futures.AbstractResolvableFuture
    protected void afterDone() {
        this.upstreamFuture.cancel(wasInterrupted());
    }

    @Override // java.util.concurrent.Delayed
    public long getDelay(TimeUnit unit) {
        return this.upstreamFuture.getDelay(unit);
    }

    @Override // java.lang.Comparable
    public int compareTo(Delayed o) {
        return this.upstreamFuture.compareTo(o);
    }
}
