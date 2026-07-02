package com.google.firebase.components;

import java.lang.annotation.Annotation;
/* loaded from: classes.dex */
public final class Qualified<T> {
    private final Class<? extends Annotation> qualifier;
    private final Class<T> type;

    /* loaded from: classes.dex */
    private @interface Unqualified {
    }

    public Qualified(Class<? extends Annotation> qualifier, Class<T> type) {
        this.qualifier = qualifier;
        this.type = type;
    }

    public static <T> Qualified<T> unqualified(Class<T> type) {
        return new Qualified<>(Unqualified.class, type);
    }

    public static <T> Qualified<T> qualified(Class<? extends Annotation> qualifier, Class<T> type) {
        return new Qualified<>(qualifier, type);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Qualified<?> qualified = (Qualified) o;
        if (!this.type.equals(qualified.type)) {
            return false;
        }
        return this.qualifier.equals(qualified.qualifier);
    }

    public int hashCode() {
        int result = this.type.hashCode();
        return (result * 31) + this.qualifier.hashCode();
    }

    public String toString() {
        if (this.qualifier == Unqualified.class) {
            return this.type.getName();
        }
        return "@" + this.qualifier.getName() + " " + this.type.getName();
    }
}
