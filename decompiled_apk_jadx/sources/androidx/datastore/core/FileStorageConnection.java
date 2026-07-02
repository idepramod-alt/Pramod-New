package androidx.datastore.core;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.io.File;
import java.io.IOException;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.sync.Mutex;
import kotlinx.coroutines.sync.MutexKt;
/* compiled from: FileStorage.kt */
@Metadata(d1 = {"\u0000j\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u0000*\u0004\b\u0000\u0010\u00012\b\u0012\u0004\u0012\u0002H\u00010\u0002B1\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00028\u00000\u0006\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n¢\u0006\u0002\u0010\fJ\b\u0010\u0013\u001a\u00020\u000bH\u0002J\b\u0010\u0014\u001a\u00020\u000bH\u0016JX\u0010\u0015\u001a\u0002H\u0016\"\u0004\b\u0001\u0010\u00162B\u0010\u0017\u001a>\b\u0001\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\u0019\u0012\u0013\u0012\u00110\u001a¢\u0006\f\b\u001b\u0012\b\b\u001c\u0012\u0004\b\b(\u001d\u0012\n\u0012\b\u0012\u0004\u0012\u0002H\u00160\u001e\u0012\u0006\u0012\u0004\u0018\u00010\u001f0\u0018¢\u0006\u0002\b H\u0096@¢\u0006\u0002\u0010!J=\u0010\"\u001a\u00020\u000b2-\u0010\u0017\u001a)\b\u0001\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000$\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\u001e\u0012\u0006\u0012\u0004\u0018\u00010\u001f0#¢\u0006\u0002\b H\u0096@¢\u0006\u0002\u0010%J\f\u0010&\u001a\u00020\u000b*\u00020\u0004H\u0002R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\u00020\bX\u0096\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00028\u00000\u0006X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006'"}, d2 = {"Landroidx/datastore/core/FileStorageConnection;", "T", "Landroidx/datastore/core/StorageConnection;", "file", "Ljava/io/File;", "serializer", "Landroidx/datastore/core/Serializer;", "coordinator", "Landroidx/datastore/core/InterProcessCoordinator;", "onClose", "Lkotlin/Function0;", "", "(Ljava/io/File;Landroidx/datastore/core/Serializer;Landroidx/datastore/core/InterProcessCoordinator;Lkotlin/jvm/functions/Function0;)V", "closed", "Ljava/util/concurrent/atomic/AtomicBoolean;", "getCoordinator", "()Landroidx/datastore/core/InterProcessCoordinator;", "transactionMutex", "Lkotlinx/coroutines/sync/Mutex;", "checkNotClosed", "close", "readScope", "R", "block", "Lkotlin/Function3;", "Landroidx/datastore/core/ReadScope;", "", "Lkotlin/ParameterName;", "name", "locked", "Lkotlin/coroutines/Continuation;", "", "Lkotlin/ExtensionFunctionType;", "(Lkotlin/jvm/functions/Function3;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "writeScope", "Lkotlin/Function2;", "Landroidx/datastore/core/WriteScope;", "(Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "createParentDirectories", "datastore-core_release"}, k = 1, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class FileStorageConnection<T> implements StorageConnection<T> {
    private final java.util.concurrent.atomic.AtomicBoolean closed;
    private final InterProcessCoordinator coordinator;
    private final File file;
    private final Function0<Unit> onClose;
    private final Serializer<T> serializer;
    private final Mutex transactionMutex;

    public FileStorageConnection(File file, Serializer<T> serializer, InterProcessCoordinator coordinator, Function0<Unit> onClose) {
        Intrinsics.checkNotNullParameter(file, "file");
        Intrinsics.checkNotNullParameter(serializer, "serializer");
        Intrinsics.checkNotNullParameter(coordinator, "coordinator");
        Intrinsics.checkNotNullParameter(onClose, "onClose");
        this.file = file;
        this.serializer = serializer;
        this.coordinator = coordinator;
        this.onClose = onClose;
        this.closed = new java.util.concurrent.atomic.AtomicBoolean(false);
        this.transactionMutex = MutexKt.Mutex$default(false, 1, null);
    }

    @Override // androidx.datastore.core.StorageConnection
    public InterProcessCoordinator getCoordinator() {
        return this.coordinator;
    }

    /* JADX WARN: Removed duplicated region for block: B:10:0x0027  */
    /* JADX WARN: Removed duplicated region for block: B:12:0x002f  */
    /* JADX WARN: Removed duplicated region for block: B:17:0x0045  */
    /* JADX WARN: Removed duplicated region for block: B:34:0x008d A[DONT_GENERATE] */
    /* JADX WARN: Removed duplicated region for block: B:38:0x0096 A[Catch: all -> 0x00a8, TRY_ENTER, TRY_LEAVE, TryCatch #4 {all -> 0x00a8, blocks: (B:38:0x0096, B:47:0x00a7, B:18:0x0053, B:42:0x009c), top: B:58:0x0053 }] */
    @Override // androidx.datastore.core.StorageConnection
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public <R> java.lang.Object readScope(kotlin.jvm.functions.Function3<? super androidx.datastore.core.ReadScope<T>, ? super java.lang.Boolean, ? super kotlin.coroutines.Continuation<? super R>, ? extends java.lang.Object> r13, kotlin.coroutines.Continuation<? super R> r14) {
        /*
            r12 = this;
            boolean r0 = r14 instanceof androidx.datastore.core.FileStorageConnection$readScope$1
            if (r0 == 0) goto L14
            r0 = r14
            androidx.datastore.core.FileStorageConnection$readScope$1 r0 = (androidx.datastore.core.FileStorageConnection$readScope$1) r0
            int r1 = r0.label
            r2 = -2147483648(0xffffffff80000000, float:-0.0)
            r1 = r1 & r2
            if (r1 == 0) goto L14
            int r14 = r0.label
            int r14 = r14 - r2
            r0.label = r14
            goto L19
        L14:
            androidx.datastore.core.FileStorageConnection$readScope$1 r0 = new androidx.datastore.core.FileStorageConnection$readScope$1
            r0.<init>(r12, r14)
        L19:
            r14 = r0
            java.lang.Object r0 = r14.result
            java.lang.Object r1 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
            int r2 = r14.label
            r3 = 1
            r4 = 0
            switch(r2) {
                case 0: goto L45;
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
            androidx.datastore.core.FileStorageConnection r6 = (androidx.datastore.core.FileStorageConnection) r6
            kotlin.ResultKt.throwOnFailure(r0)     // Catch: java.lang.Throwable -> L42
            r7 = r13
            r13 = r0
            r8 = r4
            goto L7e
        L42:
            r1 = move-exception
            r7 = r4
            goto L9a
        L45:
            kotlin.ResultKt.throwOnFailure(r0)
            r6 = r12
            r6.checkNotClosed()
            kotlinx.coroutines.sync.Mutex r2 = r6.transactionMutex
            boolean r2 = kotlinx.coroutines.sync.Mutex.DefaultImpls.tryLock$default(r2, r4, r3, r4)
            androidx.datastore.core.FileReadScope r5 = new androidx.datastore.core.FileReadScope     // Catch: java.lang.Throwable -> La8
            java.io.File r7 = r6.file     // Catch: java.lang.Throwable -> La8
            androidx.datastore.core.Serializer<T> r8 = r6.serializer     // Catch: java.lang.Throwable -> La8
            r5.<init>(r7, r8)     // Catch: java.lang.Throwable -> La8
            androidx.datastore.core.Closeable r5 = (androidx.datastore.core.Closeable) r5     // Catch: java.lang.Throwable -> La8
            r7 = 0
            r8 = 0
            r9 = r5
            androidx.datastore.core.FileReadScope r9 = (androidx.datastore.core.FileReadScope) r9     // Catch: java.lang.Throwable -> L97
            r10 = 0
            if (r2 == 0) goto L69
            r11 = r3
            goto L6a
        L69:
            r11 = 0
        L6a:
            java.lang.Boolean r11 = kotlin.coroutines.jvm.internal.Boxing.boxBoolean(r11)     // Catch: java.lang.Throwable -> L97
            r14.L$0 = r6     // Catch: java.lang.Throwable -> L97
            r14.L$1 = r5     // Catch: java.lang.Throwable -> L97
            r14.Z$0 = r2     // Catch: java.lang.Throwable -> L97
            r14.label = r3     // Catch: java.lang.Throwable -> L97
            java.lang.Object r13 = r13.invoke(r9, r11, r14)     // Catch: java.lang.Throwable -> L97
            if (r13 != r1) goto L7d
            return r1
        L7d:
            r1 = r10
        L7e:
            r1 = r8
            r5.close()     // Catch: java.lang.Throwable -> L86
            goto L8a
        L86:
            r5 = move-exception
            r1 = r5
        L8a:
            if (r1 != 0) goto L95
        L8d:
            if (r2 == 0) goto L94
            kotlinx.coroutines.sync.Mutex r1 = r6.transactionMutex
            kotlinx.coroutines.sync.Mutex.DefaultImpls.unlock$default(r1, r4, r3, r4)
        L94:
            return r13
        L95:
            throw r1     // Catch: java.lang.Throwable -> La8
        L97:
            r1 = move-exception
            r13 = r7
            r7 = r8
        L9a:
            r7 = r1
            r5.close()     // Catch: java.lang.Throwable -> La0
            goto La5
        La0:
            r1 = move-exception
            kotlin.ExceptionsKt.addSuppressed(r7, r1)     // Catch: java.lang.Throwable -> La8
        La5:
            throw r7     // Catch: java.lang.Throwable -> La8
        La8:
            r13 = move-exception
            if (r2 == 0) goto Lb0
            kotlinx.coroutines.sync.Mutex r1 = r6.transactionMutex
            kotlinx.coroutines.sync.Mutex.DefaultImpls.unlock$default(r1, r4, r3, r4)
        Lb0:
            throw r13
        */
        throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.FileStorageConnection.readScope(kotlin.jvm.functions.Function3, kotlin.coroutines.Continuation):java.lang.Object");
    }

    /* JADX WARN: Not initialized variable reg: 10, insn: 0x014e: INVOKE  
      (r11v0 ?? I:kotlinx.coroutines.sync.Mutex A[D('$this$withLock_u24default$iv' kotlinx.coroutines.sync.Mutex)])
      (r10 I:java.lang.Object A[D('owner$iv' java.lang.Object)])
     type: INTERFACE call: kotlinx.coroutines.sync.Mutex.unlock(java.lang.Object):void, block:B:68:0x014e */
    /* JADX WARN: Not initialized variable reg: 11, insn: 0x014e: INVOKE  
      (r11 I:kotlinx.coroutines.sync.Mutex A[D('$this$withLock_u24default$iv' kotlinx.coroutines.sync.Mutex)])
      (r10 I:java.lang.Object A[D('owner$iv' java.lang.Object)])
     type: INTERFACE call: kotlinx.coroutines.sync.Mutex.unlock(java.lang.Object):void, block:B:68:0x014e */
    /* JADX WARN: Not initialized variable reg: 9, insn: 0x0142: INVOKE  (r2 I:boolean) = (r9 I:java.io.File A[D('scratchFile' java.io.File)]) type: VIRTUAL call: java.io.File.exists():boolean, block:B:62:0x0142 */
    /* JADX WARN: Removed duplicated region for block: B:10:0x002c  */
    /* JADX WARN: Removed duplicated region for block: B:12:0x0034  */
    /* JADX WARN: Removed duplicated region for block: B:17:0x0052  */
    /* JADX WARN: Removed duplicated region for block: B:18:0x0067  */
    /* JADX WARN: Removed duplicated region for block: B:29:0x00d3 A[RETURN] */
    /* JADX WARN: Removed duplicated region for block: B:30:0x00d4  */
    /* JADX WARN: Removed duplicated region for block: B:38:0x00e8 A[Catch: IOException -> 0x0141, all -> 0x014d, TRY_ENTER, TryCatch #2 {IOException -> 0x0141, blocks: (B:38:0x00e8, B:40:0x00ee, B:43:0x00f7, B:44:0x0121, B:50:0x012e, B:60:0x0140, B:58:0x013b, B:25:0x00b1), top: B:74:0x0029, outer: #3 }] */
    /* JADX WARN: Removed duplicated region for block: B:50:0x012e A[Catch: IOException -> 0x0141, all -> 0x014d, TRY_ENTER, TRY_LEAVE, TryCatch #2 {IOException -> 0x0141, blocks: (B:38:0x00e8, B:40:0x00ee, B:43:0x00f7, B:44:0x0121, B:50:0x012e, B:60:0x0140, B:58:0x013b, B:25:0x00b1), top: B:74:0x0029, outer: #3 }] */
    /* JADX WARN: Type inference failed for: r9v0, types: [java.io.File] */
    @Override // androidx.datastore.core.StorageConnection
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public java.lang.Object writeScope(kotlin.jvm.functions.Function2<? super androidx.datastore.core.WriteScope<T>, ? super kotlin.coroutines.Continuation<? super kotlin.Unit>, ? extends java.lang.Object> r17, kotlin.coroutines.Continuation<? super kotlin.Unit> r18) {
        /*
            Method dump skipped, instructions count: 348
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.FileStorageConnection.writeScope(kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation):java.lang.Object");
    }

    @Override // androidx.datastore.core.Closeable
    public void close() {
        this.closed.set(true);
        this.onClose.invoke();
    }

    private final void checkNotClosed() {
        if (!(!this.closed.get())) {
            throw new IllegalStateException("StorageConnection has already been disposed.".toString());
        }
    }

    private final void createParentDirectories(File $this$createParentDirectories) {
        File parent = $this$createParentDirectories.getCanonicalFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
            if (!parent.isDirectory()) {
                throw new IOException("Unable to create parent directories of " + $this$createParentDirectories);
            }
        }
    }
}
