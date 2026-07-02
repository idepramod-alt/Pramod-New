package androidx.core.util;

import androidx.core.util.Predicate;
import java.util.Objects;
/* loaded from: classes.dex */
public interface Predicate<T> {
    Predicate<T> and(Predicate<? super T> predicate);

    Predicate<T> negate();

    Predicate<T> or(Predicate<? super T> predicate);

    boolean test(T t);

    /* renamed from: androidx.core.util.Predicate$-CC  reason: invalid class name */
    /* loaded from: classes.dex */
    public final /* synthetic */ class CC {
        public static /* synthetic */ boolean $private$lambda$and$0(Predicate _this, Predicate other, Object t) {
            return _this.test(t) && other.test(t);
        }

        public static Predicate $default$negate(final Predicate _this) {
            return new Predicate() { // from class: androidx.core.util.Predicate$$ExternalSyntheticLambda5
                @Override // androidx.core.util.Predicate
                public /* synthetic */ Predicate and(Predicate predicate) {
                    return Objects.requireNonNull(predicate);
                }

                @Override // androidx.core.util.Predicate
                public /* synthetic */ Predicate negate() {
                    return Predicate.CC.$default$negate(this);
                }

                @Override // androidx.core.util.Predicate
                public /* synthetic */ Predicate or(Predicate predicate) {
                    return Objects.requireNonNull(predicate);
                }

                @Override // androidx.core.util.Predicate
                public final boolean test(Object obj) {
                    return Predicate.CC.$private$lambda$negate$1(Predicate.this, obj);
                }
            };
        }

        public static /* synthetic */ boolean $private$lambda$negate$1(Predicate _this, Object t) {
            return !_this.test(t);
        }

        public static /* synthetic */ boolean $private$lambda$or$2(Predicate _this, Predicate other, Object t) {
            return _this.test(t) || other.test(t);
        }

        public static <T> Predicate<T> isEqual(final Object targetRef) {
            if (targetRef == null) {
                return new Predicate() { // from class: androidx.core.util.Predicate$$ExternalSyntheticLambda2
                    @Override // androidx.core.util.Predicate
                    public /* synthetic */ Predicate and(Predicate predicate) {
                        return Objects.requireNonNull(predicate);
                    }

                    @Override // androidx.core.util.Predicate
                    public /* synthetic */ Predicate negate() {
                        return Predicate.CC.$default$negate(this);
                    }

                    @Override // androidx.core.util.Predicate
                    public /* synthetic */ Predicate or(Predicate predicate) {
                        return Objects.requireNonNull(predicate);
                    }

                    @Override // androidx.core.util.Predicate
                    public final boolean test(Object obj) {
                        boolean m;
                        m = Predicate$$ExternalSyntheticBackport0.m(obj);
                        return m;
                    }
                };
            }
            return new Predicate() { // from class: androidx.core.util.Predicate$$ExternalSyntheticLambda3
                @Override // androidx.core.util.Predicate
                public /* synthetic */ Predicate and(Predicate predicate) {
                    return Objects.requireNonNull(predicate);
                }

                @Override // androidx.core.util.Predicate
                public /* synthetic */ Predicate negate() {
                    return Predicate.CC.$default$negate(this);
                }

                @Override // androidx.core.util.Predicate
                public /* synthetic */ Predicate or(Predicate predicate) {
                    return Objects.requireNonNull(predicate);
                }

                @Override // androidx.core.util.Predicate
                public final boolean test(Object obj) {
                    boolean equals;
                    equals = targetRef.equals(obj);
                    return equals;
                }
            };
        }

        public static <T> Predicate<T> not(Predicate<? super T> target) {
            Objects.requireNonNull(target);
            return (Predicate<? super T>) target.negate();
        }
    }
}
