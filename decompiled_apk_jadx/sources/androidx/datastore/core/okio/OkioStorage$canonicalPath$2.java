package androidx.datastore.core.okio;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Lambda;
import okio.Path;
/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: OkioStorage.kt */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0010\u0000\u001a\u00020\u0001\"\u0004\b\u0000\u0010\u0002H\n¢\u0006\u0002\b\u0003"}, d2 = {"<anonymous>", "Lokio/Path;", "T", "invoke"}, k = 3, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class OkioStorage$canonicalPath$2 extends Lambda implements Function0<Path> {
    final /* synthetic */ OkioStorage<T> this$0;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public OkioStorage$canonicalPath$2(OkioStorage<T> okioStorage) {
        super(0);
        this.this$0 = okioStorage;
    }

    @Override // kotlin.jvm.functions.Function0
    public final Path invoke() {
        Function0 function0;
        Function0 function02;
        function0 = ((OkioStorage) this.this$0).producePath;
        Path path = (Path) function0.invoke();
        boolean isAbsolute = path.isAbsolute();
        OkioStorage<T> okioStorage = this.this$0;
        if (isAbsolute) {
            return path.normalized();
        }
        StringBuilder append = new StringBuilder().append("OkioStorage requires absolute paths, but did not get an absolute path from producePath = ");
        function02 = ((OkioStorage) okioStorage).producePath;
        throw new IllegalStateException(append.append(function02).append(", instead got ").append(path).toString().toString());
    }
}
