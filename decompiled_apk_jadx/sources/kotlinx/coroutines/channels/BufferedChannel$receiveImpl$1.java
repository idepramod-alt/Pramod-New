package kotlinx.coroutines.channels;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.jvm.functions.Function3;
/* compiled from: BufferedChannel.kt */
@Metadata(k = 3, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public final class BufferedChannel$receiveImpl$1 implements Function3 {
    public static final BufferedChannel$receiveImpl$1 INSTANCE = new BufferedChannel$receiveImpl$1();

    @Override // kotlin.jvm.functions.Function3
    public /* bridge */ /* synthetic */ Object invoke(Object p1, Object p2, Object p3) {
        return invoke((ChannelSegment) p1, ((Number) p2).intValue(), ((Number) p3).longValue());
    }

    public final Void invoke(ChannelSegment<E> channelSegment, int i, long j) {
        throw new IllegalStateException("unexpected".toString());
    }
}
