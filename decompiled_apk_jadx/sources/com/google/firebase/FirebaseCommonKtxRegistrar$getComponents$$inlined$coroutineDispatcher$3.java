package com.google.firebase;

import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentFactory;
import com.google.firebase.components.Qualified;
import java.util.concurrent.Executor;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.ExecutorsKt;
/* compiled from: Firebase.kt */
@Metadata(k = 3, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class FirebaseCommonKtxRegistrar$getComponents$$inlined$coroutineDispatcher$3<T> implements ComponentFactory {
    public static final FirebaseCommonKtxRegistrar$getComponents$$inlined$coroutineDispatcher$3<T> INSTANCE = new FirebaseCommonKtxRegistrar$getComponents$$inlined$coroutineDispatcher$3<>();

    @Override // com.google.firebase.components.ComponentFactory
    public final CoroutineDispatcher create(ComponentContainer c) {
        Object obj = c.get(Qualified.qualified(Blocking.class, Executor.class));
        Intrinsics.checkNotNullExpressionValue(obj, "get(...)");
        return ExecutorsKt.from((Executor) obj);
    }
}
