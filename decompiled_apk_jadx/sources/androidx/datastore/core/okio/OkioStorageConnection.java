package androidx.datastore.core.okio;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.datastore.core.InterProcessCoordinator;
import androidx.datastore.core.StorageConnection;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.sync.Mutex;
import kotlinx.coroutines.sync.MutexKt;
import okio.FileSystem;
import okio.Path;
/* compiled from: OkioStorage.kt */
@Metadata(d1 = {"\u0000p\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0000\u0018\u0000*\u0004\b\u0000\u0010\u00012\b\u0012\u0004\u0012\u0002H\u00010\u0002B9\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00028\u00000\b\u0012\u0006\u0010\t\u001a\u00020\n\u0012\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f¢\u0006\u0002\u0010\u000eJ\b\u0010\u0015\u001a\u00020\rH\u0002J\b\u0010\u0016\u001a\u00020\rH\u0016JX\u0010\u0017\u001a\u0002H\u0018\"\u0004\b\u0001\u0010\u00182B\u0010\u0019\u001a>\b\u0001\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u001b\u0012\u0013\u0012\u00110\u001c¢\u0006\f\b\u001d\u0012\b\b\u001e\u0012\u0004\b\b(\u001f\u0012\n\u0012\b\u0012\u0004\u0012\u0002H\u00180 \u0012\u0006\u0012\u0004\u0018\u00010!0\u001a¢\u0006\u0002\b\"H\u0096@¢\u0006\u0002\u0010#J=\u0010$\u001a\u00020\r2-\u0010\u0019\u001a)\b\u0001\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000&\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0 \u0012\u0006\u0012\u0004\u0018\u00010!0%¢\u0006\u0002\b\"H\u0096@¢\u0006\u0002\u0010'R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\u00020\nX\u0096\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00028\u00000\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006("}, d2 = {"Landroidx/datastore/core/okio/OkioStorageConnection;", "T", "Landroidx/datastore/core/StorageConnection;", "fileSystem", "Lokio/FileSystem;", "path", "Lokio/Path;", "serializer", "Landroidx/datastore/core/okio/OkioSerializer;", "coordinator", "Landroidx/datastore/core/InterProcessCoordinator;", "onClose", "Lkotlin/Function0;", "", "(Lokio/FileSystem;Lokio/Path;Landroidx/datastore/core/okio/OkioSerializer;Landroidx/datastore/core/InterProcessCoordinator;Lkotlin/jvm/functions/Function0;)V", "closed", "Landroidx/datastore/core/okio/AtomicBoolean;", "getCoordinator", "()Landroidx/datastore/core/InterProcessCoordinator;", "transactionMutex", "Lkotlinx/coroutines/sync/Mutex;", "checkNotClosed", "close", "readScope", "R", "block", "Lkotlin/Function3;", "Landroidx/datastore/core/ReadScope;", "", "Lkotlin/ParameterName;", "name", "locked", "Lkotlin/coroutines/Continuation;", "", "Lkotlin/ExtensionFunctionType;", "(Lkotlin/jvm/functions/Function3;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "writeScope", "Lkotlin/Function2;", "Landroidx/datastore/core/WriteScope;", "(Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "datastore-core-okio"}, k = 1, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class OkioStorageConnection<T> implements StorageConnection<T> {
    private final AtomicBoolean closed;
    private final InterProcessCoordinator coordinator;
    private final FileSystem fileSystem;
    private final Function0<Unit> onClose;
    private final Path path;
    private final OkioSerializer<T> serializer;
    private final Mutex transactionMutex;

    public OkioStorageConnection(FileSystem fileSystem, Path path, OkioSerializer<T> serializer, InterProcessCoordinator coordinator, Function0<Unit> onClose) {
        Intrinsics.checkNotNullParameter(fileSystem, "fileSystem");
        Intrinsics.checkNotNullParameter(path, "path");
        Intrinsics.checkNotNullParameter(serializer, "serializer");
        Intrinsics.checkNotNullParameter(coordinator, "coordinator");
        Intrinsics.checkNotNullParameter(onClose, "onClose");
        this.fileSystem = fileSystem;
        this.path = path;
        this.serializer = serializer;
        this.coordinator = coordinator;
        this.onClose = onClose;
        this.closed = new AtomicBoolean(false);
        this.transactionMutex = MutexKt.Mutex$default(false, 1, null);
    }

    @Override // androidx.datastore.core.StorageConnection
    public InterProcessCoordinator getCoordinator() {
        return this.coordinator;
    }

    /* JADX WARN: Removed duplicated region for block: B:10:0x0027  */
    /* JADX WARN: Removed duplicated region for block: B:12:0x002f  */
    /* JADX WARN: Removed duplicated region for block: B:17:0x0044  */
    /* JADX WARN: Removed duplicated region for block: B:32:0x0089 A[DONT_GENERATE] */
    /* JADX WARN: Removed duplicated region for block: B:36:0x0092 A[Catch: all -> 0x00a4, TRY_ENTER, TRY_LEAVE, TryCatch #2 {all -> 0x00a4, blocks: (B:36:0x0092, B:45:0x00a3, B:18:0x0052, B:40:0x0098), top: B:52:0x0052 }] */
    @Override // androidx.datastore.core.StorageConnection
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public <R> java.lang.Object readScope(kotlin.jvm.functions.Function3<? super androidx.datastore.core.ReadScope<T>, ? super java.lang.Boolean, ? super kotlin.coroutines.Continuation<? super R>, ? extends java.lang.Object> r13, kotlin.coroutines.Continuation<? super R> r14) {
        /*
            r12 = this;
            boolean r0 = r14 instanceof androidx.datastore.core.okio.OkioStorageConnection$readScope$1
            if (r0 == 0) goto L14
            r0 = r14
            androidx.datastore.core.okio.OkioStorageConnection$readScope$1 r0 = (androidx.datastore.core.okio.OkioStorageConnection$readScope$1) r0
            int r1 = r0.label
            r2 = -2147483648(0xffffffff80000000, float:-0.0)
            r1 = r1 & r2
            if (r1 == 0) goto L14
            int r14 = r0.label
            int r14 = r14 - r2
            r0.label = r14
            goto L19
        L14:
            androidx.datastore.core.okio.OkioStorageConnection$readScope$1 r0 = new androidx.datastore.core.okio.OkioStorageConnection$readScope$1
            r0.<init>(r12, r14)
        L19:
            r14 = r0
            java.lang.Object r0 = r14.result
            java.lang.Object r1 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
            int r2 = r14.label
            r3 = 1
            r4 = 0
            switch(r2) {
                case 0: goto L44;
                case 1: goto L2f;
                default: goto L27;
            }
        L27:
            java.lang.IllegalStateException r13 = new java.lang.IllegalStateException
            java.lang.String r14 = "call to 'resume' before 'invoke' with coroutine"
            r13.<init>(r14)
            throw r13
        L2f:
            r13 = 0
            r1 = 0
            boolean r2 = r14.Z$0
            java.lang.Object r5 = r14.L$1
            androidx.datastore.core.Closeable r5 = (androidx.datastore.core.Closeable) r5
            java.lang.Object r6 = r14.L$0
            androidx.datastore.core.okio.OkioStorageConnection r6 = (androidx.datastore.core.okio.OkioStorageConnection) r6
            kotlin.ResultKt.throwOnFailure(r0)     // Catch: java.lang.Throwable -> L41
            r13 = r0
            r8 = r4
            goto L7e
        L41:
            r1 = move-exception
            r7 = r4
            goto L96
        L44:
            kotlin.ResultKt.throwOnFailure(r0)
            r6 = r12
            r6.checkNotClosed()
            kotlinx.coroutines.sync.Mutex r2 = r6.transactionMutex
            boolean r2 = kotlinx.coroutines.sync.Mutex.DefaultImpls.tryLock$default(r2, r4, r3, r4)
            androidx.datastore.core.okio.OkioReadScope r5 = new androidx.datastore.core.okio.OkioReadScope     // Catch: java.lang.Throwable -> La4
            okio.FileSystem r7 = r6.fileSystem     // Catch: java.lang.Throwable -> La4
            okio.Path r8 = r6.path     // Catch: java.lang.Throwable -> La4
            androidx.datastore.core.okio.OkioSerializer<T> r9 = r6.serializer     // Catch: java.lang.Throwable -> La4
            r5.<init>(r7, r8, r9)     // Catch: java.lang.Throwable -> La4
            androidx.datastore.core.Closeable r5 = (androidx.datastore.core.Closeable) r5     // Catch: java.lang.Throwable -> La4
            r7 = 0
            r8 = 0
            r9 = r5
            androidx.datastore.core.okio.OkioReadScope r9 = (androidx.datastore.core.okio.OkioReadScope) r9     // Catch: java.lang.Throwable -> L93
            r10 = 0
            if (r2 == 0) goto L6a
            r11 = r3
            goto L6b
        L6a:
            r11 = 0
        L6b:
            java.lang.Boolean r11 = kotlin.coroutines.jvm.internal.Boxing.boxBoolean(r11)     // Catch: java.lang.Throwable -> L93
            r14.L$0 = r6     // Catch: java.lang.Throwable -> L93
            r14.L$1 = r5     // Catch: java.lang.Throwable -> L93
            r14.Z$0 = r2     // Catch: java.lang.Throwable -> L93
            r14.label = r3     // Catch: java.lang.Throwable -> L93
            java.lang.Object r13 = r13.invoke(r9, r11, r14)     // Catch: java.lang.Throwable -> L93
            if (r13 != r1) goto L7e
            return r1
        L7e:
            r5.close()     // Catch: java.lang.Throwable -> L84
            goto L87
        L84:
            r8 = move-exception
        L87:
            if (r8 != 0) goto L91
            if (r2 == 0) goto L90
            kotlinx.coroutines.sync.Mutex r1 = r6.transactionMutex
            kotlinx.coroutines.sync.Mutex.DefaultImpls.unlock$default(r1, r4, r3, r4)
        L90:
            return r13
        L91:
            throw r8     // Catch: java.lang.Throwable -> La4
        L93:
            r1 = move-exception
            r13 = r7
            r7 = r8
        L96:
            r7 = r1
            r5.close()     // Catch: java.lang.Throwable -> L9c
            goto La1
        L9c:
            r1 = move-exception
            kotlin.ExceptionsKt.addSuppressed(r7, r1)     // Catch: java.lang.Throwable -> La4
        La1:
            throw r7     // Catch: java.lang.Throwable -> La4
        La4:
            r13 = move-exception
            if (r2 == 0) goto Lac
            kotlinx.coroutines.sync.Mutex r1 = r6.transactionMutex
            kotlinx.coroutines.sync.Mutex.DefaultImpls.unlock$default(r1, r4, r3, r4)
        Lac:
            throw r13
        */
        throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.okio.OkioStorageConnection.readScope(kotlin.jvm.functions.Function3, kotlin.coroutines.Continuation):java.lang.Object");
    }

    /* JADX WARN: Not initialized variable reg: 10, insn: 0x0154: INVOKE  
      (r11v0 ?? I:kotlinx.coroutines.sync.Mutex A[D('$this$withLock_u24default$iv' kotlinx.coroutines.sync.Mutex)])
      (r10 I:java.lang.Object A[D('owner$iv' java.lang.Object)])
     type: INTERFACE call: kotlinx.coroutines.sync.Mutex.unlock(java.lang.Object):void, block:B:70:0x0154 */
    /* JADX WARN: Not initialized variable reg: 11, insn: 0x0154: INVOKE  
      (r11 I:kotlinx.coroutines.sync.Mutex A[D('$this$withLock_u24default$iv' kotlinx.coroutines.sync.Mutex)])
      (r10 I:java.lang.Object A[D('owner$iv' java.lang.Object)])
     type: INTERFACE call: kotlinx.coroutines.sync.Mutex.unlock(java.lang.Object):void, block:B:70:0x0154 */
    /* JADX WARN: Not initialized variable reg: 9, insn: 0x013d: MOVE  (r7 I:??[OBJECT, ARRAY]) = (r9 I:??[OBJECT, ARRAY] A[D('scratchPath' okio.Path)]), block:B:59:0x013c */
    /* JADX WARN: Removed duplicated region for block: B:10:0x002d  */
    /* JADX WARN: Removed duplicated region for block: B:12:0x0035  */
    /* JADX WARN: Removed duplicated region for block: B:17:0x0053  */
    /* JADX WARN: Removed duplicated region for block: B:18:0x006c  */
    /* JADX WARN: Removed duplicated region for block: B:30:0x00ec A[RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:31:0x00ed  */
    /* JADX WARN: Removed duplicated region for block: B:39:0x0107 A[Catch: IOException -> 0x013b, all -> 0x0153, TRY_ENTER, TryCatch #3 {all -> 0x0153, blocks: (B:39:0x0107, B:41:0x010f, B:43:0x0117, B:62:0x0141, B:64:0x014a, B:68:0x0152, B:47:0x0122, B:57:0x013a, B:55:0x0135, B:25:0x00a3, B:26:0x00c1), top: B:80:0x002a }] */
    /* JADX WARN: Removed duplicated region for block: B:47:0x0122 A[Catch: IOException -> 0x013b, all -> 0x0153, TRY_ENTER, TRY_LEAVE, TryCatch #3 {all -> 0x0153, blocks: (B:39:0x0107, B:41:0x010f, B:43:0x0117, B:62:0x0141, B:64:0x014a, B:68:0x0152, B:47:0x0122, B:57:0x013a, B:55:0x0135, B:25:0x00a3, B:26:0x00c1), top: B:80:0x002a }] */
    @Override // androidx.datastore.core.StorageConnection
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public java.lang.Object writeScope(kotlin.jvm.functions.Function2<? super androidx.datastore.core.WriteScope<T>, ? super kotlin.coroutines.Continuation<? super kotlin.Unit>, ? extends java.lang.Object> r18, kotlin.coroutines.Continuation<? super kotlin.Unit> r19) {
        /*
            Method dump skipped, instructions count: 366
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.okio.OkioStorageConnection.writeScope(kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation):java.lang.Object");
    }

    private final void checkNotClosed() {
        if (!(!this.closed.get())) {
            throw new IllegalStateException("StorageConnection has already been disposed.".toString());
        }
    }

    @Override // androidx.datastore.core.Closeable
    public void close() {
        this.closed.set(true);
        this.onClose.invoke();
    }
}
