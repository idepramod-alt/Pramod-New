package com.google.firebase.database.core.utilities;
/* loaded from: classes.dex */
public final class Pair<T, U> {
    private final T first;
    private final U second;

    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return this.first;
    }

    public U getSecond() {
        return this.second;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair pair = (Pair) o;
        if (this.first == null ? pair.first != null : !this.first.equals(pair.first)) {
            return false;
        }
        if (this.second == null ? pair.second == null : this.second.equals(pair.second)) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        int result = this.first != null ? this.first.hashCode() : 0;
        return (result * 31) + (this.second != null ? this.second.hashCode() : 0);
    }

    public String toString() {
        return "Pair(" + this.first + "," + this.second + ")";
    }
}
