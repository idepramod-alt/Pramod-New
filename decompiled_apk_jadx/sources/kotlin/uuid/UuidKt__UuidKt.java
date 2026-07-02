package kotlin.uuid;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.jvm.internal.ByteCompanionObject;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.HexExtensionsKt;
import okio.Utf8;
/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: Uuid.kt */
@Metadata(d1 = {"\u0000(\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\n\u001a\u0010\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0001\u001a\u0019\u0010\u0004\u001a\u00020\u0005*\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0002¢\u0006\u0002\b\t\u001a)\u0010\n\u001a\u00020\u0005*\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\r\u001a\u00020\b2\u0006\u0010\u000e\u001a\u00020\bH\u0002¢\u0006\u0002\b\u000f\u001a!\u0010\u0010\u001a\u00020\u0005*\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\r\u001a\u00020\bH\u0002¢\u0006\u0002\b\u0011\u001a\u0019\u0010\u0012\u001a\u00020\u000b*\u00020\u00032\u0006\u0010\u0013\u001a\u00020\bH\u0002¢\u0006\u0002\b\u0014¨\u0006\u0015"}, d2 = {"uuidFromRandomBytes", "Lkotlin/uuid/Uuid;", "randomBytes", "", "checkHyphenAt", "", "", "index", "", "checkHyphenAt$UuidKt__UuidKt", "formatBytesInto", "", "dst", "dstOffset", "count", "formatBytesInto$UuidKt__UuidKt", "toByteArray", "toByteArray$UuidKt__UuidKt", "toLong", "startIndex", "toLong$UuidKt__UuidKt", "kotlin-stdlib"}, k = 5, mv = {1, 9, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_EDITOR_ABSOLUTEX, xs = "kotlin/uuid/UuidKt")
/* loaded from: classes4.dex */
public class UuidKt__UuidKt extends UuidKt__UuidJVMKt {
    public static final Uuid uuidFromRandomBytes(byte[] randomBytes) {
        Intrinsics.checkNotNullParameter(randomBytes, "randomBytes");
        randomBytes[6] = (byte) (randomBytes[6] & 15);
        randomBytes[6] = (byte) (randomBytes[6] | 64);
        randomBytes[8] = (byte) (randomBytes[8] & Utf8.REPLACEMENT_BYTE);
        randomBytes[8] = (byte) (randomBytes[8] | ByteCompanionObject.MIN_VALUE);
        return Uuid.Companion.fromByteArray(randomBytes);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final long toLong$UuidKt__UuidKt(byte[] $this$toLong, int startIndex) {
        return (($this$toLong[startIndex + 0] & 255) << 56) | (($this$toLong[startIndex + 1] & 255) << 48) | (($this$toLong[startIndex + 2] & 255) << 40) | (($this$toLong[startIndex + 3] & 255) << 32) | (($this$toLong[startIndex + 4] & 255) << 24) | (($this$toLong[startIndex + 5] & 255) << 16) | (($this$toLong[startIndex + 6] & 255) << 8) | (255 & $this$toLong[startIndex + 7]);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void formatBytesInto$UuidKt__UuidKt(long $this$formatBytesInto, byte[] dst, int dstOffset, int count) {
        long j = $this$formatBytesInto;
        int dstIndex = (count * 2) + dstOffset;
        for (int dstIndex2 = 0; dstIndex2 < count; dstIndex2++) {
            int byteDigits = HexExtensionsKt.getBYTE_TO_LOWER_CASE_HEX_DIGITS()[(int) (255 & j)];
            int dstIndex3 = dstIndex - 1;
            dst[dstIndex3] = (byte) byteDigits;
            dstIndex = dstIndex3 - 1;
            dst[dstIndex] = (byte) (byteDigits >> 8);
            j >>= 8;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkHyphenAt$UuidKt__UuidKt(String $this$checkHyphenAt, int index) {
        if (!($this$checkHyphenAt.charAt(index) == '-')) {
            throw new IllegalArgumentException(("Expected '-' (hyphen) at index 8, but was " + $this$checkHyphenAt.charAt(index)).toString());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void toByteArray$UuidKt__UuidKt(long $this$toByteArray, byte[] dst, int dstOffset) {
        for (int index = 0; index < 8; index++) {
            int shift = (7 - index) * 8;
            dst[dstOffset + index] = (byte) ($this$toByteArray >>> shift);
        }
    }
}
