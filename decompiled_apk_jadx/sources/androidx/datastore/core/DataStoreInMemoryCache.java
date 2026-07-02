package androidx.datastore.core;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
/* compiled from: DataStoreInMemoryCache.kt */
@Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0000\u0018\u0000*\u0004\b\u0000\u0010\u00012\u00020\u0002B\u0005¢\u0006\u0002\u0010\u0003J\u001a\u0010\u000f\u001a\b\u0012\u0004\u0012\u00028\u00000\u00062\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00028\u00000\u0006R \u0010\u0004\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u00060\u0005X\u0082\u0004¢\u0006\b\n\u0000\u0012\u0004\b\u0007\u0010\u0003R\u0017\u0010\b\u001a\b\u0012\u0004\u0012\u00028\u00000\u00068F¢\u0006\u0006\u001a\u0004\b\t\u0010\nR\u001d\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u00060\f8F¢\u0006\u0006\u001a\u0004\b\r\u0010\u000e¨\u0006\u0011"}, d2 = {"Landroidx/datastore/core/DataStoreInMemoryCache;", "T", "", "()V", "cachedValue", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Landroidx/datastore/core/State;", "getCachedValue$annotations", "currentState", "getCurrentState", "()Landroidx/datastore/core/State;", "flow", "Lkotlinx/coroutines/flow/Flow;", "getFlow", "()Lkotlinx/coroutines/flow/Flow;", "tryUpdate", "newState", "datastore-core_release"}, k = 1, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class DataStoreInMemoryCache<T> {
    private final MutableStateFlow<State<T>> cachedValue;

    private static /* synthetic */ void getCachedValue$annotations() {
    }

    public DataStoreInMemoryCache() {
        UnInitialized unInitialized = UnInitialized.INSTANCE;
        Intrinsics.checkNotNull(unInitialized, "null cannot be cast to non-null type androidx.datastore.core.State<T of androidx.datastore.core.DataStoreInMemoryCache>");
        this.cachedValue = StateFlowKt.MutableStateFlow(unInitialized);
    }

    public final State<T> getCurrentState() {
        return this.cachedValue.getValue();
    }

    public final Flow<State<T>> getFlow() {
        return this.cachedValue;
    }

    /* JADX WARN: Code restructure failed: missing block: B:12:0x002d, code lost:
        if (r8.getVersion() > r3.getVersion()) goto L22;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final androidx.datastore.core.State<T> tryUpdate(androidx.datastore.core.State<T> r8) {
        /*
            r7 = this;
            java.lang.String r0 = "newState"
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(r8, r0)
            kotlinx.coroutines.flow.MutableStateFlow<androidx.datastore.core.State<T>> r0 = r7.cachedValue
            r1 = 0
        L8:
            java.lang.Object r2 = r0.getValue()
            r3 = r2
            androidx.datastore.core.State r3 = (androidx.datastore.core.State) r3
            r4 = 0
            boolean r5 = r3 instanceof androidx.datastore.core.ReadException
            if (r5 == 0) goto L18
            r5 = 1
            goto L1e
        L18:
            androidx.datastore.core.UnInitialized r5 = androidx.datastore.core.UnInitialized.INSTANCE
            boolean r5 = kotlin.jvm.internal.Intrinsics.areEqual(r3, r5)
        L1e:
            if (r5 == 0) goto L21
            goto L30
        L21:
            boolean r5 = r3 instanceof androidx.datastore.core.Data
            if (r5 == 0) goto L33
            int r5 = r8.getVersion()
            int r6 = r3.getVersion()
            if (r5 <= r6) goto L32
        L30:
            r5 = r8
            goto L39
        L32:
            goto L38
        L33:
            boolean r5 = r3 instanceof androidx.datastore.core.Final
            if (r5 == 0) goto L43
        L38:
            r5 = r3
        L39:
            r3 = r5
            boolean r4 = r0.compareAndSet(r2, r3)
            if (r4 == 0) goto L8
        L41:
            r0 = r3
            return r0
        L43:
            kotlin.NoWhenBranchMatchedException r5 = new kotlin.NoWhenBranchMatchedException
            r5.<init>()
            throw r5
        */
        throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.DataStoreInMemoryCache.tryUpdate(androidx.datastore.core.State):androidx.datastore.core.State");
    }
}
