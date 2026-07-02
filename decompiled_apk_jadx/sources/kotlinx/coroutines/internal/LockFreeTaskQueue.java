package kotlinx.coroutines.internal;

import androidx.concurrent.futures.AbstractResolvableFuture$SafeAtomicHelper$$ExternalSyntheticBackportWithForwarding0;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
/* compiled from: LockFreeTaskQueue.kt */
@Metadata(d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0010\u0018\u0000*\b\b\u0000\u0010\u0001*\u00020\u00022\u00020\u0002B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0004¢\u0006\u0004\b\u0005\u0010\u0006J\u0006\u0010\u0010\u001a\u00020\u0011J\u0013\u0010\u0012\u001a\u00020\u00042\u0006\u0010\u0013\u001a\u00028\u0000¢\u0006\u0002\u0010\u0014J\r\u0010\u0015\u001a\u0004\u0018\u00018\u0000¢\u0006\u0002\u0010\u0016J&\u0010\u0017\u001a\b\u0012\u0004\u0012\u0002H\u00190\u0018\"\u0004\b\u0001\u0010\u00192\u0012\u0010\u001a\u001a\u000e\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u0002H\u00190\u001bJ\u0006\u0010\u001c\u001a\u00020\u0004R\u0015\u0010\u0007\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000\t0\bX\u0082\u0004R\u0011\u0010\n\u001a\u00020\u00048F¢\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\f\u001a\u00020\r8F¢\u0006\u0006\u001a\u0004\b\u000e\u0010\u000f¨\u0006\u001d"}, d2 = {"Lkotlinx/coroutines/internal/LockFreeTaskQueue;", "E", "", "singleConsumer", "", "<init>", "(Z)V", "_cur", "Lkotlinx/atomicfu/AtomicRef;", "Lkotlinx/coroutines/internal/LockFreeTaskQueueCore;", "isEmpty", "()Z", "size", "", "getSize", "()I", "close", "", "addLast", "element", "(Ljava/lang/Object;)Z", "removeFirstOrNull", "()Ljava/lang/Object;", "map", "", "R", "transform", "Lkotlin/Function1;", "isClosed", "kotlinx-coroutines-core"}, k = 1, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public class LockFreeTaskQueue<E> {
    private static final /* synthetic */ AtomicReferenceFieldUpdater _cur$volatile$FU = AtomicReferenceFieldUpdater.newUpdater(LockFreeTaskQueue.class, Object.class, "_cur$volatile");
    private volatile /* synthetic */ Object _cur$volatile;

    private final /* synthetic */ Object get_cur$volatile() {
        return this._cur$volatile;
    }

    private final /* synthetic */ void loop$atomicfu(Object obj, AtomicReferenceFieldUpdater atomicReferenceFieldUpdater, Function1<Object, Unit> function1) {
        while (true) {
            function1.invoke(atomicReferenceFieldUpdater.get(obj));
        }
    }

    private final /* synthetic */ void set_cur$volatile(Object obj) {
        this._cur$volatile = obj;
    }

    public LockFreeTaskQueue(boolean singleConsumer) {
        this._cur$volatile = new LockFreeTaskQueueCore(8, singleConsumer);
    }

    public final boolean isEmpty() {
        return ((LockFreeTaskQueueCore) _cur$volatile$FU.get(this)).isEmpty();
    }

    public final int getSize() {
        return ((LockFreeTaskQueueCore) _cur$volatile$FU.get(this)).getSize();
    }

    public final void close() {
        AtomicReferenceFieldUpdater handler$atomicfu$iv = _cur$volatile$FU;
        while (true) {
            LockFreeTaskQueueCore cur = (LockFreeTaskQueueCore) handler$atomicfu$iv.get(this);
            if (cur.close()) {
                return;
            }
            AbstractResolvableFuture$SafeAtomicHelper$$ExternalSyntheticBackportWithForwarding0.m(_cur$volatile$FU, this, cur, cur.next());
        }
    }

    public final boolean addLast(E e) {
        AtomicReferenceFieldUpdater handler$atomicfu$iv = _cur$volatile$FU;
        while (true) {
            LockFreeTaskQueueCore cur = (LockFreeTaskQueueCore) handler$atomicfu$iv.get(this);
            switch (cur.addLast(e)) {
                case 0:
                    return true;
                case 1:
                    AbstractResolvableFuture$SafeAtomicHelper$$ExternalSyntheticBackportWithForwarding0.m(_cur$volatile$FU, this, cur, cur.next());
                    break;
                case 2:
                    return false;
            }
        }
    }

    public final E removeFirstOrNull() {
        AtomicReferenceFieldUpdater handler$atomicfu$iv = _cur$volatile$FU;
        while (true) {
            LockFreeTaskQueueCore cur = (LockFreeTaskQueueCore) handler$atomicfu$iv.get(this);
            E e = (E) cur.removeFirstOrNull();
            if (e != LockFreeTaskQueueCore.REMOVE_FROZEN) {
                return e;
            }
            AbstractResolvableFuture$SafeAtomicHelper$$ExternalSyntheticBackportWithForwarding0.m(_cur$volatile$FU, this, cur, cur.next());
        }
    }

    public final <R> List<R> map(Function1<? super E, ? extends R> function1) {
        return ((LockFreeTaskQueueCore) _cur$volatile$FU.get(this)).map(function1);
    }

    public final boolean isClosed() {
        return ((LockFreeTaskQueueCore) _cur$volatile$FU.get(this)).isClosed();
    }
}
