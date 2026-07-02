package kotlinx.coroutines.channels;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.jvm.functions.Function4;
/* compiled from: BufferedChannel.kt */
@Metadata(k = 3, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public final class BufferedChannel$sendImpl$1 implements Function4 {
    public static final BufferedChannel$sendImpl$1 INSTANCE = new BufferedChannel$sendImpl$1();

    @Override // kotlin.jvm.functions.Function4
    public /* bridge */ /* synthetic */ Object invoke(Object p1, Object p2, Object p3, Object p4) {
        return invoke((ChannelSegment<int>) p1, ((Number) p2).intValue(), (int) p3, ((Number) p4).longValue());
    }

    public final Void invoke(ChannelSegment<E> channelSegment, int i, E e, long j) {
        throw new IllegalStateException("unexpected".toString());
    }
}
