package okio.internal;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.io.IOException;
import kotlin.Metadata;
import kotlin.UByte;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Lambda;
import kotlin.jvm.internal.Ref;
import okio.BufferedSource;
/* compiled from: ZipFiles.kt */
@Metadata(d1 = {"\u0000\u0014\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\n¢\u0006\u0002\b\u0006"}, d2 = {"<anonymous>", "", "headerId", "", "dataSize", "", "invoke"}, k = 3, mv = {1, 8, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
final class ZipFilesKt$readOrSkipLocalHeader$1 extends Lambda implements Function2<Integer, Long, Unit> {
    final /* synthetic */ Ref.ObjectRef<Long> $createdAtMillis;
    final /* synthetic */ Ref.ObjectRef<Long> $lastAccessedAtMillis;
    final /* synthetic */ Ref.ObjectRef<Long> $lastModifiedAtMillis;
    final /* synthetic */ BufferedSource $this_readOrSkipLocalHeader;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public ZipFilesKt$readOrSkipLocalHeader$1(BufferedSource bufferedSource, Ref.ObjectRef<Long> objectRef, Ref.ObjectRef<Long> objectRef2, Ref.ObjectRef<Long> objectRef3) {
        super(2);
        this.$this_readOrSkipLocalHeader = bufferedSource;
        this.$lastModifiedAtMillis = objectRef;
        this.$lastAccessedAtMillis = objectRef2;
        this.$createdAtMillis = objectRef3;
    }

    @Override // kotlin.jvm.functions.Function2
    public /* bridge */ /* synthetic */ Unit invoke(Integer num, Long l) {
        invoke(num.intValue(), l.longValue());
        return Unit.INSTANCE;
    }

    /* JADX WARN: Type inference failed for: r4v12, types: [T, java.lang.Long] */
    /* JADX WARN: Type inference failed for: r4v6, types: [T, java.lang.Long] */
    /* JADX WARN: Type inference failed for: r4v9, types: [T, java.lang.Long] */
    public final void invoke(int headerId, long dataSize) {
        if (headerId == 21589) {
            if (dataSize < 1) {
                throw new IOException("bad zip: extended timestamp extra too short");
            }
            int flags = this.$this_readOrSkipLocalHeader.readByte() & UByte.MAX_VALUE;
            boolean hasLastModifiedAtMillis = (flags & 1) == 1;
            boolean hasLastAccessedAtMillis = (flags & 2) == 2;
            boolean hasCreatedAtMillis = (flags & 4) == 4;
            BufferedSource bufferedSource = this.$this_readOrSkipLocalHeader;
            long result = hasLastModifiedAtMillis ? 1 + 4 : 1L;
            if (hasLastAccessedAtMillis) {
                result += 4;
            }
            if (hasCreatedAtMillis) {
                result += 4;
            }
            long requiredSize = result;
            if (dataSize < requiredSize) {
                throw new IOException("bad zip: extended timestamp extra too short");
            }
            if (hasLastModifiedAtMillis) {
                this.$lastModifiedAtMillis.element = Long.valueOf(this.$this_readOrSkipLocalHeader.readIntLe() * 1000);
            }
            if (hasLastAccessedAtMillis) {
                this.$lastAccessedAtMillis.element = Long.valueOf(this.$this_readOrSkipLocalHeader.readIntLe() * 1000);
            }
            if (hasCreatedAtMillis) {
                this.$createdAtMillis.element = Long.valueOf(this.$this_readOrSkipLocalHeader.readIntLe() * 1000);
            }
        }
    }
}
