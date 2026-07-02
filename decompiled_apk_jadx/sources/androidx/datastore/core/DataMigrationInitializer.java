package androidx.datastore.core;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.List;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
/* compiled from: DataMigrationInitializer.kt */
@Metadata(d1 = {"\u0000\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0003\b\u0000\u0018\u0000 \u0004*\u0004\b\u0000\u0010\u00012\u00020\u0002:\u0001\u0004B\u0005¢\u0006\u0002\u0010\u0003¨\u0006\u0005"}, d2 = {"Landroidx/datastore/core/DataMigrationInitializer;", "T", "", "()V", "Companion", "datastore-core_release"}, k = 1, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class DataMigrationInitializer<T> {
    public static final Companion Companion = new Companion(null);

    /* compiled from: DataMigrationInitializer.kt */
    @Metadata(d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002JV\u0010\u0003\u001a3\b\u0001\u0012\u0019\u0012\u0017\u0012\u0004\u0012\u0002H\u00060\u0005¢\u0006\f\b\u0007\u0012\b\b\b\u0012\u0004\b\b(\t\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u0004\"\u0004\b\u0001\u0010\u00062\u0012\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u0002H\u00060\u000e0\r¢\u0006\u0002\u0010\u000fJ6\u0010\u0010\u001a\u00020\u000b\"\u0004\b\u0001\u0010\u00062\u0012\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u0002H\u00060\u000e0\r2\f\u0010\t\u001a\b\u0012\u0004\u0012\u0002H\u00060\u0005H\u0082@¢\u0006\u0002\u0010\u0011¨\u0006\u0012"}, d2 = {"Landroidx/datastore/core/DataMigrationInitializer$Companion;", "", "()V", "getInitializer", "Lkotlin/Function2;", "Landroidx/datastore/core/InitializerApi;", "T", "Lkotlin/ParameterName;", "name", "api", "Lkotlin/coroutines/Continuation;", "", "migrations", "", "Landroidx/datastore/core/DataMigration;", "(Ljava/util/List;)Lkotlin/jvm/functions/Function2;", "runMigrations", "(Ljava/util/List;Landroidx/datastore/core/InitializerApi;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "datastore-core_release"}, k = 1, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
    /* loaded from: classes.dex */
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final <T> Function2<InitializerApi<T>, Continuation<? super Unit>, Object> getInitializer(List<? extends DataMigration<T>> migrations) {
            Intrinsics.checkNotNullParameter(migrations, "migrations");
            return new DataMigrationInitializer$Companion$getInitializer$1(migrations, null);
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* JADX WARN: Removed duplicated region for block: B:10:0x0025  */
        /* JADX WARN: Removed duplicated region for block: B:12:0x002d  */
        /* JADX WARN: Removed duplicated region for block: B:17:0x003d  */
        /* JADX WARN: Removed duplicated region for block: B:18:0x0045  */
        /* JADX WARN: Removed duplicated region for block: B:25:0x0079  */
        /* JADX WARN: Removed duplicated region for block: B:35:0x0099  */
        /* JADX WARN: Removed duplicated region for block: B:36:0x009c  */
        /* JADX WARN: Removed duplicated region for block: B:40:0x00af  */
        /* JADX WARN: Removed duplicated region for block: B:42:0x00b2  */
        /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:35:0x0099 -> B:23:0x0073). Please submit an issue!!! */
        /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:36:0x009c -> B:23:0x0073). Please submit an issue!!! */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public final <T> java.lang.Object runMigrations(java.util.List<? extends androidx.datastore.core.DataMigration<T>> r8, androidx.datastore.core.InitializerApi<T> r9, kotlin.coroutines.Continuation<? super kotlin.Unit> r10) {
            /*
                r7 = this;
                boolean r0 = r10 instanceof androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$1
                if (r0 == 0) goto L14
                r0 = r10
                androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$1 r0 = (androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$1) r0
                int r1 = r0.label
                r2 = -2147483648(0xffffffff80000000, float:-0.0)
                r1 = r1 & r2
                if (r1 == 0) goto L14
                int r10 = r0.label
                int r10 = r10 - r2
                r0.label = r10
                goto L19
            L14:
                androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$1 r0 = new androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$1
                r0.<init>(r7, r10)
            L19:
                r10 = r0
                java.lang.Object r0 = r10.result
                java.lang.Object r1 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
                int r2 = r10.label
                switch(r2) {
                    case 0: goto L45;
                    case 1: goto L3d;
                    case 2: goto L2d;
                    default: goto L25;
                }
            L25:
                java.lang.IllegalStateException r8 = new java.lang.IllegalStateException
                java.lang.String r9 = "call to 'resume' before 'invoke' with coroutine"
                r8.<init>(r9)
                throw r8
            L2d:
                r8 = 0
                r9 = 0
                java.lang.Object r2 = r10.L$1
                java.util.Iterator r2 = (java.util.Iterator) r2
                java.lang.Object r3 = r10.L$0
                kotlin.jvm.internal.Ref$ObjectRef r3 = (kotlin.jvm.internal.Ref.ObjectRef) r3
                kotlin.ResultKt.throwOnFailure(r0)     // Catch: java.lang.Throwable -> L3b
                goto L90
            L3b:
                r4 = move-exception
                goto L95
            L3d:
                java.lang.Object r8 = r10.L$0
                java.util.List r8 = (java.util.List) r8
                kotlin.ResultKt.throwOnFailure(r0)
                goto L64
            L45:
                kotlin.ResultKt.throwOnFailure(r0)
                java.util.ArrayList r2 = new java.util.ArrayList
                r2.<init>()
                java.util.List r2 = (java.util.List) r2
                androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$2 r3 = new androidx.datastore.core.DataMigrationInitializer$Companion$runMigrations$2
                r4 = 0
                r3.<init>(r8, r2, r4)
                kotlin.jvm.functions.Function2 r3 = (kotlin.jvm.functions.Function2) r3
                r10.L$0 = r2
                r4 = 1
                r10.label = r4
                java.lang.Object r8 = r9.updateData(r3, r10)
                if (r8 != r1) goto L63
                return r1
            L63:
                r8 = r2
            L64:
                kotlin.jvm.internal.Ref$ObjectRef r9 = new kotlin.jvm.internal.Ref$ObjectRef
                r9.<init>()
                java.lang.Iterable r8 = (java.lang.Iterable) r8
                r2 = 0
                java.util.Iterator r3 = r8.iterator()
                r8 = r2
                r2 = r3
                r3 = r9
            L73:
                boolean r9 = r2.hasNext()
                if (r9 == 0) goto La8
                java.lang.Object r9 = r2.next()
                kotlin.jvm.functions.Function1 r9 = (kotlin.jvm.functions.Function1) r9
                r4 = 0
                r10.L$0 = r3     // Catch: java.lang.Throwable -> L91
                r10.L$1 = r2     // Catch: java.lang.Throwable -> L91
                r5 = 2
                r10.label = r5     // Catch: java.lang.Throwable -> L91
                java.lang.Object r5 = r9.invoke(r10)     // Catch: java.lang.Throwable -> L91
                if (r5 != r1) goto L8f
                return r1
            L8f:
                r9 = r4
            L90:
                goto La6
            L91:
                r9 = move-exception
                r6 = r4
                r4 = r9
                r9 = r6
            L95:
                T r5 = r3.element
                if (r5 != 0) goto L9c
                r3.element = r4
                goto La6
            L9c:
                T r5 = r3.element
                kotlin.jvm.internal.Intrinsics.checkNotNull(r5)
                java.lang.Throwable r5 = (java.lang.Throwable) r5
                kotlin.ExceptionsKt.addSuppressed(r5, r4)
            La6:
                goto L73
            La8:
                T r8 = r3.element
                java.lang.Throwable r8 = (java.lang.Throwable) r8
                if (r8 != 0) goto Lb2
                kotlin.Unit r8 = kotlin.Unit.INSTANCE
                return r8
            Lb2:
                r9 = 0
                throw r8
            */
            throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.DataMigrationInitializer.Companion.runMigrations(java.util.List, androidx.datastore.core.InitializerApi, kotlin.coroutines.Continuation):java.lang.Object");
        }
    }
}
