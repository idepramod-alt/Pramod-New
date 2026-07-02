package kotlinx.coroutines.channels;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.concurrent.CancellationException;
import kotlin.Deprecated;
import kotlin.DeprecationLevel;
import kotlin.Metadata;
import kotlin.ReplaceWith;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.selects.SelectClause2;
/* compiled from: BroadcastChannel.kt */
@Deprecated(level = DeprecationLevel.ERROR, message = "ConflatedBroadcastChannel is deprecated in the favour of SharedFlow and is no longer supported")
@Metadata(d1 = {"\u0000T\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0003\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u0000*\u0004\b\u0000\u0010\u00012\b\u0012\u0004\u0012\u0002H\u00010\u0002B\u0017\b\u0002\u0012\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00028\u00000\u0004¢\u0006\u0004\b\u0005\u0010\u0006B\t\b\u0016¢\u0006\u0004\b\u0005\u0010\u0007B\u0011\b\u0016\u0012\u0006\u0010\b\u001a\u00028\u0000¢\u0006\u0004\b\u0005\u0010\tJ\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u0097\u0001J\u0019\u0010\u000e\u001a\u00020\u00122\u000e\u0010\u0010\u001a\n\u0018\u00010\u0014j\u0004\u0018\u0001`\u0013H\u0096\u0001J\u0013\u0010\u0015\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0011H\u0096\u0001J.\u0010\u0016\u001a\u00020\u00122#\u0010\u0017\u001a\u001f\u0012\u0015\u0012\u0013\u0018\u00010\u0011¢\u0006\f\b\u0019\u0012\b\b\u001a\u0012\u0004\b\b(\u0010\u0012\u0004\u0012\u00020\u00120\u0018H\u0096\u0001J\u0011\u0010\u001b\u001a\u00020\u000f2\u0006\u0010\u001c\u001a\u00028\u0000H\u0097\u0001J\u000f\u0010\u001d\u001a\b\u0012\u0004\u0012\u00028\u00000\u001eH\u0096\u0001J\u0011\u0010\u001f\u001a\u00020\u00122\u0006\u0010\u001c\u001a\u00028\u0000H\u0096AJ\u0017\u0010 \u001a\b\u0012\u0004\u0012\u00020\u00120!2\u0006\u0010\u001c\u001a\u00028\u0000H\u0096\u0001R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00028\u00000\u0004X\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\b\u001a\u00028\u00008F¢\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\f\u001a\u0004\u0018\u00018\u00008F¢\u0006\u0006\u001a\u0004\b\r\u0010\u000bR\u000b\u0010\"\u001a\u00020\u000f8\u0016X\u0097\u0005R\u001b\u0010#\u001a\u0014\u0012\u0004\u0012\u00028\u0000\u0012\n\u0012\b\u0012\u0004\u0012\u00028\u00000%0$X\u0096\u0005¨\u0006&"}, d2 = {"Lkotlinx/coroutines/channels/ConflatedBroadcastChannel;", "E", "Lkotlinx/coroutines/channels/BroadcastChannel;", "broadcast", "Lkotlinx/coroutines/channels/BroadcastChannelImpl;", "<init>", "(Lkotlinx/coroutines/channels/BroadcastChannelImpl;)V", "()V", "value", "(Ljava/lang/Object;)V", "getValue", "()Ljava/lang/Object;", "valueOrNull", "getValueOrNull", "cancel", "", "cause", "", "", "Lkotlinx/coroutines/CancellationException;", "Ljava/util/concurrent/CancellationException;", "close", "invokeOnClose", "handler", "Lkotlin/Function1;", "Lkotlin/ParameterName;", "name", "offer", "element", "openSubscription", "Lkotlinx/coroutines/channels/ReceiveChannel;", "send", "trySend", "Lkotlinx/coroutines/channels/ChannelResult;", "isClosedForSend", "onSend", "Lkotlinx/coroutines/selects/SelectClause2;", "Lkotlinx/coroutines/channels/SendChannel;", "kotlinx-coroutines-core"}, k = 1, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public final class ConflatedBroadcastChannel<E> implements BroadcastChannel<E> {
    private final BroadcastChannelImpl<E> broadcast;

    @Override // kotlinx.coroutines.channels.BroadcastChannel
    public void cancel(CancellationException cancellationException) {
        this.broadcast.cancel(cancellationException);
    }

    @Override // kotlinx.coroutines.channels.BroadcastChannel
    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Binary compatibility only")
    public /* synthetic */ boolean cancel(Throwable th) {
        return this.broadcast.cancel(th);
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    public boolean close(Throwable th) {
        return this.broadcast.close(th);
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    public SelectClause2<E, SendChannel<E>> getOnSend() {
        return this.broadcast.getOnSend();
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    public void invokeOnClose(Function1<? super Throwable, Unit> function1) {
        this.broadcast.invokeOnClose(function1);
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    public boolean isClosedForSend() {
        return this.broadcast.isClosedForSend();
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    @Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in the favour of 'trySend' method", replaceWith = @ReplaceWith(expression = "trySend(element).isSuccess", imports = {}))
    public boolean offer(E e) {
        return this.broadcast.offer(e);
    }

    @Override // kotlinx.coroutines.channels.BroadcastChannel
    public ReceiveChannel<E> openSubscription() {
        return this.broadcast.openSubscription();
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    public Object send(E e, Continuation<? super Unit> continuation) {
        return this.broadcast.send(e, continuation);
    }

    @Override // kotlinx.coroutines.channels.SendChannel
    /* renamed from: trySend-JP2dKIU */
    public Object mo1732trySendJP2dKIU(E e) {
        return this.broadcast.mo1732trySendJP2dKIU(e);
    }

    private ConflatedBroadcastChannel(BroadcastChannelImpl<E> broadcastChannelImpl) {
        this.broadcast = broadcastChannelImpl;
    }

    public ConflatedBroadcastChannel() {
        this(new BroadcastChannelImpl(-1));
    }

    public ConflatedBroadcastChannel(E e) {
        this();
        mo1732trySendJP2dKIU(e);
    }

    public final E getValue() {
        return this.broadcast.getValue();
    }

    public final E getValueOrNull() {
        return this.broadcast.getValueOrNull();
    }
}
