package androidx.datastore.core;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.io.File;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
/* compiled from: FileStorage.kt */
@Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0000\u0018\u0000*\u0004\b\u0000\u0010\u00012\b\u0012\u0004\u0012\u0002H\u00010\u00022\b\u0012\u0004\u0012\u0002H\u00010\u0003B\u001b\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00028\u00000\u0007¢\u0006\u0002\u0010\bJ\u0016\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00028\u0000H\u0096@¢\u0006\u0002\u0010\f¨\u0006\r"}, d2 = {"Landroidx/datastore/core/FileWriteScope;", "T", "Landroidx/datastore/core/FileReadScope;", "Landroidx/datastore/core/WriteScope;", "file", "Ljava/io/File;", "serializer", "Landroidx/datastore/core/Serializer;", "(Ljava/io/File;Landroidx/datastore/core/Serializer;)V", "writeData", "", "value", "(Ljava/lang/Object;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "datastore-core_release"}, k = 1, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class FileWriteScope<T> extends FileReadScope<T> implements WriteScope<T> {
    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public FileWriteScope(File file, Serializer<T> serializer) {
        super(file, serializer);
        Intrinsics.checkNotNullParameter(file, "file");
        Intrinsics.checkNotNullParameter(serializer, "serializer");
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:10:0x0025  */
    /* JADX WARN: Removed duplicated region for block: B:12:0x002d  */
    /* JADX WARN: Removed duplicated region for block: B:17:0x003c  */
    /* JADX WARN: Type inference failed for: r2v4, types: [java.io.Closeable] */
    /* JADX WARN: Type inference failed for: r2v5 */
    /* JADX WARN: Type inference failed for: r2v6, types: [java.io.Closeable] */
    /* JADX WARN: Type inference failed for: r2v8, types: [java.io.Closeable] */
    /* JADX WARN: Type inference failed for: r2v9 */
    @Override // androidx.datastore.core.WriteScope
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public java.lang.Object writeData(T r10, kotlin.coroutines.Continuation<? super kotlin.Unit> r11) {
        /*
            r9 = this;
            boolean r0 = r11 instanceof androidx.datastore.core.FileWriteScope$writeData$1
            if (r0 == 0) goto L14
            r0 = r11
            androidx.datastore.core.FileWriteScope$writeData$1 r0 = (androidx.datastore.core.FileWriteScope$writeData$1) r0
            int r1 = r0.label
            r2 = -2147483648(0xffffffff80000000, float:-0.0)
            r1 = r1 & r2
            if (r1 == 0) goto L14
            int r11 = r0.label
            int r11 = r11 - r2
            r0.label = r11
            goto L19
        L14:
            androidx.datastore.core.FileWriteScope$writeData$1 r0 = new androidx.datastore.core.FileWriteScope$writeData$1
            r0.<init>(r9, r11)
        L19:
            r11 = r0
            java.lang.Object r0 = r11.result
            java.lang.Object r1 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
            int r2 = r11.label
            switch(r2) {
                case 0: goto L3c;
                case 1: goto L2d;
                default: goto L25;
            }
        L25:
            java.lang.IllegalStateException r10 = new java.lang.IllegalStateException
            java.lang.String r11 = "call to 'resume' before 'invoke' with coroutine"
            r10.<init>(r11)
            throw r10
        L2d:
            r10 = 0
            java.lang.Object r1 = r11.L$1
            java.io.FileOutputStream r1 = (java.io.FileOutputStream) r1
            java.lang.Object r2 = r11.L$0
            java.io.Closeable r2 = (java.io.Closeable) r2
            kotlin.ResultKt.throwOnFailure(r0)     // Catch: java.lang.Throwable -> L3a
            goto L6f
        L3a:
            r10 = move-exception
            goto L82
        L3c:
            kotlin.ResultKt.throwOnFailure(r0)
            r2 = r9
            r2.checkNotClosed()
            java.io.FileOutputStream r3 = new java.io.FileOutputStream
            java.io.File r4 = r2.getFile()
            r3.<init>(r4)
            r4 = r3
            java.io.Closeable r4 = (java.io.Closeable) r4
            r3 = r4
            java.io.FileOutputStream r3 = (java.io.FileOutputStream) r3     // Catch: java.lang.Throwable -> L80
            r5 = 0
            androidx.datastore.core.Serializer r6 = r2.getSerializer()     // Catch: java.lang.Throwable -> L80
            androidx.datastore.core.UncloseableOutputStream r7 = new androidx.datastore.core.UncloseableOutputStream     // Catch: java.lang.Throwable -> L80
            r7.<init>(r3)     // Catch: java.lang.Throwable -> L80
            java.io.OutputStream r7 = (java.io.OutputStream) r7     // Catch: java.lang.Throwable -> L80
            r11.L$0 = r4     // Catch: java.lang.Throwable -> L80
            r11.L$1 = r3     // Catch: java.lang.Throwable -> L80
            r8 = 1
            r11.label = r8     // Catch: java.lang.Throwable -> L80
            java.lang.Object r6 = r6.writeTo(r10, r7, r11)     // Catch: java.lang.Throwable -> L80
            if (r6 != r1) goto L6c
            return r1
        L6c:
            r1 = r3
            r2 = r4
            r10 = r5
        L6f:
            java.io.FileDescriptor r3 = r1.getFD()     // Catch: java.lang.Throwable -> L3a
            r3.sync()     // Catch: java.lang.Throwable -> L3a
            kotlin.Unit r10 = kotlin.Unit.INSTANCE     // Catch: java.lang.Throwable -> L3a
            r10 = 0
            kotlin.io.CloseableKt.closeFinally(r2, r10)
            kotlin.Unit r10 = kotlin.Unit.INSTANCE
            return r10
        L80:
            r10 = move-exception
            r2 = r4
        L82:
            throw r10     // Catch: java.lang.Throwable -> L83
        L83:
            r1 = move-exception
            kotlin.io.CloseableKt.closeFinally(r2, r10)
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: androidx.datastore.core.FileWriteScope.writeData(java.lang.Object, kotlin.coroutines.Continuation):java.lang.Object");
    }
}
