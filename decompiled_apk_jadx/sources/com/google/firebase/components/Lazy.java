package com.google.firebase.components;

import com.google.firebase.inject.Provider;
/* loaded from: classes.dex */
public class Lazy<T> implements Provider<T> {
    private static final Object UNINITIALIZED = new Object();
    private volatile Object instance;
    private volatile Provider<T> provider;

    Lazy(T instance) {
        this.instance = UNINITIALIZED;
        this.instance = instance;
    }

    public Lazy(Provider<T> provider) {
        this.instance = UNINITIALIZED;
        this.provider = provider;
    }

    @Override // com.google.firebase.inject.Provider
    public T get() {
        Object result = this.instance;
        if (result == UNINITIALIZED) {
            synchronized (this) {
                result = this.instance;
                if (result == UNINITIALIZED) {
                    result = this.provider.get();
                    this.instance = result;
                    this.provider = null;
                }
            }
        }
        T tResult = (T) result;
        return tResult;
    }

    boolean isInitialized() {
        return this.instance != UNINITIALIZED;
    }
}
