package androidx.datastore.core;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.datastore.core.Message;
import java.util.concurrent.CancellationException;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Lambda;
/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: DataStoreImpl.kt */
@Metadata(d1 = {"\u0000\u0016\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0000\u0010\u0000\u001a\u00020\u0001\"\u0004\b\u0000\u0010\u00022\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u0002H\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\n¢\u0006\u0002\b\u0007"}, d2 = {"<anonymous>", "", "T", NotificationCompat.CATEGORY_MESSAGE, "Landroidx/datastore/core/Message$Update;", "ex", "", "invoke"}, k = 3, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class DataStoreImpl$writeActor$2 extends Lambda implements Function2<Message.Update<T>, Throwable, Unit> {
    public static final DataStoreImpl$writeActor$2 INSTANCE = new DataStoreImpl$writeActor$2();

    DataStoreImpl$writeActor$2() {
        super(2);
    }

    @Override // kotlin.jvm.functions.Function2
    public /* bridge */ /* synthetic */ Unit invoke(Object p1, Throwable th) {
        invoke((Message.Update) p1, th);
        return Unit.INSTANCE;
    }

    public final void invoke(Message.Update<T> msg, Throwable ex) {
        Intrinsics.checkNotNullParameter(msg, "msg");
        msg.getAck().completeExceptionally(ex == null ? new CancellationException("DataStore scope was cancelled before updateData could complete") : ex);
    }
}
