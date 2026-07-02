package kotlin;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.CharsKt;
/* compiled from: UnsignedJVM.kt */
@Metadata(d1 = {"\u00006\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\n\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\r\u001a\u0015\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u0001¢\u0006\u0002\u0010\u0004\u001a\u0015\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0002\u001a\u00020\u0003H\u0001¢\u0006\u0002\u0010\u0007\u001a\u0016\u0010\b\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\tH\u0081\b¢\u0006\u0002\u0010\n\u001a\u0016\u0010\u000b\u001a\u00020\u00062\u0006\u0010\u0002\u001a\u00020\tH\u0081\b¢\u0006\u0002\u0010\f\u001a\u0018\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u000e2\u0006\u0010\u0010\u001a\u00020\u000eH\u0001\u001a\u001f\u0010\u0011\u001a\u00020\u00012\u0006\u0010\u000f\u001a\u00020\u00012\u0006\u0010\u0010\u001a\u00020\u0001H\u0001¢\u0006\u0004\b\u0012\u0010\u0013\u001a\u001f\u0010\u0014\u001a\u00020\u00012\u0006\u0010\u000f\u001a\u00020\u00012\u0006\u0010\u0010\u001a\u00020\u0001H\u0001¢\u0006\u0004\b\u0015\u0010\u0013\u001a\u0010\u0010\u0016\u001a\u00020\u00032\u0006\u0010\u0002\u001a\u00020\u000eH\u0001\u001a\u0011\u0010\u0017\u001a\u00020\t2\u0006\u0010\u0002\u001a\u00020\u000eH\u0081\b\u001a\u0011\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u0002\u001a\u00020\u000eH\u0081\b\u001a\u0011\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u0002\u001a\u00020\u000eH\u0081\b\u001a\u0019\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u0002\u001a\u00020\u000e2\u0006\u0010\u001c\u001a\u00020\u000eH\u0081\b\u001a\u0016\u0010\u001d\u001a\u00020\u00062\u0006\u0010\u0002\u001a\u00020\u000eH\u0081\b¢\u0006\u0002\u0010\u001e\u001a\u0018\u0010\u001f\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00192\u0006\u0010\u0010\u001a\u00020\u0019H\u0001\u001a\u001f\u0010 \u001a\u00020\u00062\u0006\u0010\u000f\u001a\u00020\u00062\u0006\u0010\u0010\u001a\u00020\u0006H\u0001¢\u0006\u0004\b!\u0010\"\u001a\u001f\u0010#\u001a\u00020\u00062\u0006\u0010\u000f\u001a\u00020\u00062\u0006\u0010\u0010\u001a\u00020\u0006H\u0001¢\u0006\u0004\b$\u0010\"\u001a\u0010\u0010%\u001a\u00020\u00032\u0006\u0010\u0002\u001a\u00020\u0019H\u0001\u001a\u0011\u0010&\u001a\u00020\t2\u0006\u0010\u0002\u001a\u00020\u0019H\u0081\b\u001a\u0011\u0010'\u001a\u00020\u001b2\u0006\u0010\u0002\u001a\u00020\u0019H\u0081\b\u001a\u0018\u0010'\u001a\u00020\u001b2\u0006\u0010\u0002\u001a\u00020\u00192\u0006\u0010\u001c\u001a\u00020\u000eH\u0000¨\u0006("}, d2 = {"doubleToUInt", "Lkotlin/UInt;", "value", "", "(D)I", "doubleToULong", "Lkotlin/ULong;", "(D)J", "floatToUInt", "", "(F)I", "floatToULong", "(F)J", "uintCompare", "", "v1", "v2", "uintDivide", "uintDivide-J1ME1BU", "(II)I", "uintRemainder", "uintRemainder-J1ME1BU", "uintToDouble", "uintToFloat", "uintToLong", "", "uintToString", "", "base", "uintToULong", "(I)J", "ulongCompare", "ulongDivide", "ulongDivide-eb3DHEI", "(JJ)J", "ulongRemainder", "ulongRemainder-eb3DHEI", "ulongToDouble", "ulongToFloat", "ulongToString", "kotlin-stdlib"}, k = 2, mv = {1, 9, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class UnsignedKt {
    /* renamed from: uintRemainder-J1ME1BU  reason: not valid java name */
    public static final int m579uintRemainderJ1ME1BU(int v1, int v2) {
        return UInt.m322constructorimpl((int) ((v1 & 4294967295L) % (4294967295L & v2)));
    }

    /* renamed from: uintDivide-J1ME1BU  reason: not valid java name */
    public static final int m578uintDivideJ1ME1BU(int v1, int v2) {
        return UInt.m322constructorimpl((int) ((v1 & 4294967295L) / (4294967295L & v2)));
    }

    /* renamed from: ulongDivide-eb3DHEI  reason: not valid java name */
    public static final long m580ulongDivideeb3DHEI(long v1, long v2) {
        int compare;
        int compare2;
        if (v2 < 0) {
            compare2 = Long.compare(v1 ^ Long.MIN_VALUE, v2 ^ Long.MIN_VALUE);
            return ULong.m401constructorimpl(compare2 >= 0 ? 1L : 0L);
        } else if (v1 >= 0) {
            return ULong.m401constructorimpl(v1 / v2);
        } else {
            long quotient = ((v1 >>> 1) / v2) << 1;
            long rem = v1 - (quotient * v2);
            compare = Long.compare(ULong.m401constructorimpl(rem) ^ Long.MIN_VALUE, ULong.m401constructorimpl(v2) ^ Long.MIN_VALUE);
            return ULong.m401constructorimpl((compare < 0 ? 0 : 1) + quotient);
        }
    }

    /* renamed from: ulongRemainder-eb3DHEI  reason: not valid java name */
    public static final long m581ulongRemaindereb3DHEI(long v1, long v2) {
        int compare;
        int compare2;
        long j = 0;
        if (v2 < 0) {
            compare2 = Long.compare(v1 ^ Long.MIN_VALUE, v2 ^ Long.MIN_VALUE);
            if (compare2 < 0) {
                return v1;
            }
            return ULong.m401constructorimpl(v1 - v2);
        } else if (v1 >= 0) {
            return ULong.m401constructorimpl(v1 % v2);
        } else {
            long quotient = ((v1 >>> 1) / v2) << 1;
            long rem = v1 - (quotient * v2);
            compare = Long.compare(ULong.m401constructorimpl(rem) ^ Long.MIN_VALUE, ULong.m401constructorimpl(v2) ^ Long.MIN_VALUE);
            if (compare >= 0) {
                j = v2;
            }
            return ULong.m401constructorimpl(rem - j);
        }
    }

    public static final int uintCompare(int v1, int v2) {
        return Intrinsics.compare(v1 ^ Integer.MIN_VALUE, Integer.MIN_VALUE ^ v2);
    }

    public static final int ulongCompare(long v1, long v2) {
        return Intrinsics.compare(v1 ^ Long.MIN_VALUE, Long.MIN_VALUE ^ v2);
    }

    private static final long uintToULong(int value) {
        return ULong.m401constructorimpl(value & 4294967295L);
    }

    private static final long uintToLong(int value) {
        return value & 4294967295L;
    }

    private static final float uintToFloat(int value) {
        return (float) uintToDouble(value);
    }

    private static final int floatToUInt(float value) {
        return doubleToUInt(value);
    }

    public static final double uintToDouble(int value) {
        return (Integer.MAX_VALUE & value) + (((value >>> 31) << 30) * 2);
    }

    public static final int doubleToUInt(double value) {
        if (!Double.isNaN(value) && value > uintToDouble(0)) {
            if (value >= uintToDouble(-1)) {
                return -1;
            }
            return value <= 2.147483647E9d ? UInt.m322constructorimpl((int) value) : UInt.m322constructorimpl(UInt.m322constructorimpl((int) (value - Integer.MAX_VALUE)) + UInt.m322constructorimpl(Integer.MAX_VALUE));
        }
        return 0;
    }

    private static final float ulongToFloat(long value) {
        return (float) ulongToDouble(value);
    }

    private static final long floatToULong(float value) {
        return doubleToULong(value);
    }

    public static final double ulongToDouble(long value) {
        return ((value >>> 11) * 2048) + (2047 & value);
    }

    public static final long doubleToULong(double value) {
        if (!Double.isNaN(value) && value > ulongToDouble(0L)) {
            if (value >= ulongToDouble(-1L)) {
                return -1L;
            }
            return value < 9.223372036854776E18d ? ULong.m401constructorimpl((long) value) : ULong.m401constructorimpl(ULong.m401constructorimpl((long) (value - 9.223372036854776E18d)) - Long.MIN_VALUE);
        }
        return 0L;
    }

    private static final String uintToString(int value) {
        return String.valueOf(value & 4294967295L);
    }

    private static final String uintToString(int value, int base) {
        return ulongToString(value & 4294967295L, base);
    }

    private static final String ulongToString(long value) {
        return ulongToString(value, 10);
    }

    public static final String ulongToString(long value, int base) {
        if (value >= 0) {
            String l = Long.toString(value, CharsKt.checkRadix(base));
            Intrinsics.checkNotNullExpressionValue(l, "toString(...)");
            return l;
        }
        long quotient = ((value >>> 1) / base) << 1;
        long rem = value - (base * quotient);
        if (rem >= base) {
            rem -= base;
            quotient++;
        }
        StringBuilder sb = new StringBuilder();
        String l2 = Long.toString(quotient, CharsKt.checkRadix(base));
        Intrinsics.checkNotNullExpressionValue(l2, "toString(...)");
        StringBuilder append = sb.append(l2);
        String l3 = Long.toString(rem, CharsKt.checkRadix(base));
        Intrinsics.checkNotNullExpressionValue(l3, "toString(...)");
        return append.append(l3).toString();
    }
}
