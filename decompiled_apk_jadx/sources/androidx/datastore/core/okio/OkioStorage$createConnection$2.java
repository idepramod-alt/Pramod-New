package androidx.datastore.core.okio;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.Set;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Lambda;
import okio.Path;
/* compiled from: OkioStorage.kt */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\u0010\u0000\u001a\u00020\u0001\"\u0004\b\u0000\u0010\u0002H\n¢\u0006\u0002\b\u0003"}, d2 = {"<anonymous>", "", "T", "invoke"}, k = 3, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
final class OkioStorage$createConnection$2 extends Lambda implements Function0<Unit> {
    final /* synthetic */ OkioStorage<T> this$0;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public OkioStorage$createConnection$2(OkioStorage<T> okioStorage) {
        super(0);
        this.this$0 = okioStorage;
    }

    @Override // kotlin.jvm.functions.Function0
    public /* bridge */ /* synthetic */ Unit invoke() {
        invoke2();
        return Unit.INSTANCE;
    }

    /* renamed from: invoke  reason: avoid collision after fix types in other method */
    public final void invoke2() {
        Path canonicalPath;
        Synchronizer this_$iv = OkioStorage.Companion.getActiveFilesLock();
        OkioStorage<T> okioStorage = this.this$0;
        synchronized (this_$iv) {
            Set<String> activeFiles$datastore_core_okio = OkioStorage.Companion.getActiveFiles$datastore_core_okio();
            canonicalPath = okioStorage.getCanonicalPath();
            activeFiles$datastore_core_okio.remove(canonicalPath.toString());
            Unit unit = Unit.INSTANCE;
        }
    }
}
