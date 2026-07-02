package kotlinx.coroutines.internal;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.TuplesKt;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.CopyableThrowable;
/* compiled from: ExceptionsConstructor.kt */
@Metadata(d1 = {"\u0000(\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\u001a!\u0010\u0007\u001a\u0004\u0018\u0001H\b\"\b\b\u0000\u0010\b*\u00020\u00042\u0006\u0010\t\u001a\u0002H\bH\u0000¢\u0006\u0002\u0010\n\u001a7\u0010\u000b\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00040\u0003j\u0002`\f\"\b\b\u0000\u0010\b*\u00020\u00042\f\u0010\r\u001a\b\u0012\u0004\u0012\u0002H\b0\u000eH\u0002¢\u0006\u0002\u0010\u000f\u001a3\u0010\u0010\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00040\u0003j\u0002`\f2\u0012\u0010\u0011\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040\u0003H\u0002¢\u0006\u0002\u0010\u0012\u001a\u0018\u0010\u0013\u001a\u00020\u0001*\u0006\u0012\u0002\b\u00030\u000e2\u0006\u0010\u0014\u001a\u00020\u0001H\u0002\u001a\u001b\u0010\u0015\u001a\u00020\u0001*\u0006\u0012\u0002\b\u00030\u000e2\b\b\u0002\u0010\u0016\u001a\u00020\u0001H\u0082\u0010\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082\u0004¢\u0006\u0002\n\u0000\"\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004¢\u0006\u0002\n\u0000*(\b\u0002\u0010\u0002\"\u0010\u0012\u0004\u0012\u00020\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00040\u00032\u0010\u0012\u0004\u0012\u00020\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00040\u0003¨\u0006\u0017"}, d2 = {"throwableFields", "", "Ctor", "Lkotlin/Function1;", "", "ctorCache", "Lkotlinx/coroutines/internal/CtorCache;", "tryCopyException", "E", "exception", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;", "createConstructor", "Lkotlinx/coroutines/internal/Ctor;", "clz", "Ljava/lang/Class;", "(Ljava/lang/Class;)Lkotlin/jvm/functions/Function1;", "safeCtor", "block", "(Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function1;", "fieldsCountOrDefault", "defaultValue", "fieldsCount", "accumulator", "kotlinx-coroutines-core"}, k = 2, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public final class ExceptionsConstructorKt {
    private static final CtorCache ctorCache;
    private static final int throwableFields = fieldsCountOrDefault(Throwable.class, -1);

    public static final /* synthetic */ Function1 access$createConstructor(Class clz) {
        return createConstructor(clz);
    }

    static {
        CtorCache ctorCache2;
        try {
            ctorCache2 = FastServiceLoaderKt.getANDROID_DETECTED() ? WeakMapCtorCache.INSTANCE : ClassValueCtorCache.INSTANCE;
        } catch (Throwable th) {
            ctorCache2 = WeakMapCtorCache.INSTANCE;
        }
        ctorCache = ctorCache2;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final <E extends Throwable> E tryCopyException(E e) {
        Object m227constructorimpl;
        if (e instanceof CopyableThrowable) {
            try {
                Result.Companion companion = Result.Companion;
                m227constructorimpl = Result.m227constructorimpl(((CopyableThrowable) e).createCopy());
            } catch (Throwable th) {
                Result.Companion companion2 = Result.Companion;
                m227constructorimpl = Result.m227constructorimpl(ResultKt.createFailure(th));
            }
            if (Result.m233isFailureimpl(m227constructorimpl)) {
                m227constructorimpl = null;
            }
            return (E) m227constructorimpl;
        }
        return (E) ctorCache.get(e.getClass()).invoke(e);
    }

    public static final <E extends Throwable> Function1<Throwable, Throwable> createConstructor(Class<E> cls) {
        Function1<Throwable, Throwable> function1;
        char c;
        Pair pair;
        Function1 nullResult = ExceptionsConstructorKt$createConstructor$nullResult$1.INSTANCE;
        char c2 = 0;
        if (throwableFields != fieldsCountOrDefault(cls, 0)) {
            return nullResult;
        }
        Constructor<?>[] constructors = cls.getConstructors();
        Collection destination$iv$iv = new ArrayList(constructors.length);
        int length = constructors.length;
        int i = 0;
        while (true) {
            Object maxElem$iv = null;
            if (i >= length) {
                Iterable $this$maxByOrNull$iv = (List) destination$iv$iv;
                Iterator iterator$iv = $this$maxByOrNull$iv.iterator();
                if (iterator$iv.hasNext()) {
                    maxElem$iv = iterator$iv.next();
                    if (iterator$iv.hasNext()) {
                        Pair p0 = (Pair) maxElem$iv;
                        int maxValue$iv = ((Number) p0.getSecond()).intValue();
                        do {
                            Object e$iv = iterator$iv.next();
                            Pair p02 = (Pair) e$iv;
                            int v$iv = ((Number) p02.getSecond()).intValue();
                            if (maxValue$iv < v$iv) {
                                maxValue$iv = v$iv;
                                maxElem$iv = e$iv;
                            }
                        } while (iterator$iv.hasNext());
                    }
                }
                Pair pair2 = (Pair) maxElem$iv;
                return (pair2 == null || (function1 = (Function1) pair2.getFirst()) == null) ? nullResult : function1;
            }
            final Constructor<?> constructor = constructors[i];
            Class[] p = constructor.getParameterTypes();
            switch (p.length) {
                case 0:
                    c = 0;
                    pair = TuplesKt.to(safeCtor(new Function1() { // from class: kotlinx.coroutines.internal.ExceptionsConstructorKt$$ExternalSyntheticLambda4
                        @Override // kotlin.jvm.functions.Function1
                        public final Object invoke(Object obj) {
                            Throwable createConstructor$lambda$7$lambda$6;
                            createConstructor$lambda$7$lambda$6 = ExceptionsConstructorKt.createConstructor$lambda$7$lambda$6(constructor, (Throwable) obj);
                            return createConstructor$lambda$7$lambda$6;
                        }
                    }), 0);
                    break;
                case 1:
                    Class cls2 = p[0];
                    if (!Intrinsics.areEqual(cls2, String.class)) {
                        if (!Intrinsics.areEqual(cls2, Throwable.class)) {
                            pair = TuplesKt.to(null, -1);
                            c = 0;
                            break;
                        } else {
                            pair = TuplesKt.to(safeCtor(new Function1() { // from class: kotlinx.coroutines.internal.ExceptionsConstructorKt$$ExternalSyntheticLambda3
                                @Override // kotlin.jvm.functions.Function1
                                public final Object invoke(Object obj) {
                                    Throwable createConstructor$lambda$7$lambda$4;
                                    createConstructor$lambda$7$lambda$4 = ExceptionsConstructorKt.createConstructor$lambda$7$lambda$4(constructor, (Throwable) obj);
                                    return createConstructor$lambda$7$lambda$4;
                                }
                            }), 1);
                            c = 0;
                            break;
                        }
                    } else {
                        pair = TuplesKt.to(safeCtor(new Function1() { // from class: kotlinx.coroutines.internal.ExceptionsConstructorKt$$ExternalSyntheticLambda2
                            @Override // kotlin.jvm.functions.Function1
                            public final Object invoke(Object obj) {
                                Throwable createConstructor$lambda$7$lambda$3;
                                createConstructor$lambda$7$lambda$3 = ExceptionsConstructorKt.createConstructor$lambda$7$lambda$3(constructor, (Throwable) obj);
                                return createConstructor$lambda$7$lambda$3;
                            }
                        }), 2);
                        c = 0;
                        break;
                    }
                case 2:
                    if (!Intrinsics.areEqual(p[c2], String.class) || !Intrinsics.areEqual(p[1], Throwable.class)) {
                        pair = TuplesKt.to(null, -1);
                        c = 0;
                        break;
                    } else {
                        pair = TuplesKt.to(safeCtor(new Function1() { // from class: kotlinx.coroutines.internal.ExceptionsConstructorKt$$ExternalSyntheticLambda1
                            @Override // kotlin.jvm.functions.Function1
                            public final Object invoke(Object obj) {
                                Throwable createConstructor$lambda$7$lambda$1;
                                createConstructor$lambda$7$lambda$1 = ExceptionsConstructorKt.createConstructor$lambda$7$lambda$1(constructor, (Throwable) obj);
                                return createConstructor$lambda$7$lambda$1;
                            }
                        }), 3);
                        c = 0;
                        break;
                    }
                    break;
                default:
                    c = c2;
                    pair = TuplesKt.to(null, -1);
                    break;
            }
            destination$iv$iv.add(pair);
            i++;
            c2 = c;
        }
    }

    public static final Throwable createConstructor$lambda$7$lambda$1(Constructor $constructor, Throwable e) {
        Object newInstance = $constructor.newInstance(e.getMessage(), e);
        Intrinsics.checkNotNull(newInstance, "null cannot be cast to non-null type kotlin.Throwable");
        return (Throwable) newInstance;
    }

    public static final Throwable createConstructor$lambda$7$lambda$3(Constructor $constructor, Throwable e) {
        Object newInstance = $constructor.newInstance(e.getMessage());
        Intrinsics.checkNotNull(newInstance, "null cannot be cast to non-null type kotlin.Throwable");
        Throwable it = (Throwable) newInstance;
        it.initCause(e);
        return it;
    }

    public static final Throwable createConstructor$lambda$7$lambda$4(Constructor $constructor, Throwable e) {
        Object newInstance = $constructor.newInstance(e);
        Intrinsics.checkNotNull(newInstance, "null cannot be cast to non-null type kotlin.Throwable");
        return (Throwable) newInstance;
    }

    public static final Throwable createConstructor$lambda$7$lambda$6(Constructor $constructor, Throwable e) {
        Object newInstance = $constructor.newInstance(new Object[0]);
        Intrinsics.checkNotNull(newInstance, "null cannot be cast to non-null type kotlin.Throwable");
        Throwable it = (Throwable) newInstance;
        it.initCause(e);
        return it;
    }

    private static final Function1<Throwable, Throwable> safeCtor(final Function1<? super Throwable, ? extends Throwable> function1) {
        return new Function1() { // from class: kotlinx.coroutines.internal.ExceptionsConstructorKt$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                Throwable safeCtor$lambda$9;
                safeCtor$lambda$9 = ExceptionsConstructorKt.safeCtor$lambda$9(Function1.this, (Throwable) obj);
                return safeCtor$lambda$9;
            }
        };
    }

    public static final Throwable safeCtor$lambda$9(Function1 $block, Throwable e) {
        Object m227constructorimpl;
        try {
            Result.Companion companion = Result.Companion;
            Throwable result = (Throwable) $block.invoke(e);
            if (!Intrinsics.areEqual(e.getMessage(), result.getMessage()) && !Intrinsics.areEqual(result.getMessage(), e.toString())) {
                result = null;
            }
            m227constructorimpl = Result.m227constructorimpl(result);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.Companion;
            m227constructorimpl = Result.m227constructorimpl(ResultKt.createFailure(th));
        }
        return Result.m233isFailureimpl(m227constructorimpl) ? null : m227constructorimpl;
    }

    private static final int fieldsCountOrDefault(Class<?> cls, int defaultValue) {
        Integer m227constructorimpl;
        JvmClassMappingKt.getKotlinClass(cls);
        try {
            Result.Companion companion = Result.Companion;
            m227constructorimpl = Result.m227constructorimpl(Integer.valueOf(fieldsCount$default(cls, 0, 1, null)));
        } catch (Throwable th) {
            Result.Companion companion2 = Result.Companion;
            m227constructorimpl = Result.m227constructorimpl(ResultKt.createFailure(th));
        }
        Integer valueOf = Integer.valueOf(defaultValue);
        if (Result.m233isFailureimpl(m227constructorimpl)) {
            m227constructorimpl = valueOf;
        }
        return ((Number) m227constructorimpl).intValue();
    }

    static /* synthetic */ int fieldsCount$default(Class cls, int i, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            i = 0;
        }
        return fieldsCount(cls, i);
    }

    private static final int fieldsCount(Class<?> cls, int accumulator) {
        while (true) {
            int count$iv = 0;
            for (Field field : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    count$iv++;
                }
            }
            int fieldsCount = count$iv;
            int totalFields = accumulator + fieldsCount;
            Class<? super Object> superclass = cls.getSuperclass();
            if (superclass == null) {
                return totalFields;
            }
            cls = superclass;
            accumulator = totalFields;
        }
    }
}
