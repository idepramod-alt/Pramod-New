package androidx.datastore.core;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.io.File;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Lambda;
import kotlinx.coroutines.channels.ChannelsKt;
import kotlinx.coroutines.channels.ProducerScope;
/* compiled from: MulticastFileObserver.android.kt */
@Metadata(d1 = {"\u0000\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\u0010\u0000\u001a\u00020\u00012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\n¢\u0006\u0002\b\u0004"}, d2 = {"<anonymous>", "", "fileName", "", "invoke"}, k = 3, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
final class MulticastFileObserver$Companion$observe$1$flowObserver$1 extends Lambda implements Function1<String, Unit> {
    final /* synthetic */ ProducerScope<Unit> $$this$channelFlow;
    final /* synthetic */ File $file;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    /* JADX WARN: Multi-variable type inference failed */
    public MulticastFileObserver$Companion$observe$1$flowObserver$1(File file, ProducerScope<? super Unit> producerScope) {
        super(1);
        this.$file = file;
        this.$$this$channelFlow = producerScope;
    }

    @Override // kotlin.jvm.functions.Function1
    public /* bridge */ /* synthetic */ Unit invoke(String str) {
        invoke2(str);
        return Unit.INSTANCE;
    }

    /* renamed from: invoke  reason: avoid collision after fix types in other method */
    public final void invoke2(String fileName) {
        if (Intrinsics.areEqual(fileName, this.$file.getName())) {
            ChannelsKt.trySendBlocking(this.$$this$channelFlow, Unit.INSTANCE);
        }
    }
}
