package com.google.android.gms.common.util;

import com.google.android.gms.common.internal.Objects;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public final class ArrayUtils {
    private ArrayUtils() {
    }

    public static <T> T[] concat(T[]... tArr) {
        if (tArr.length != 0) {
            int i = 0;
            for (T[] tArr2 : tArr) {
                i += tArr2.length;
            }
            T[] tArr3 = (T[]) Arrays.copyOf(tArr[0], i);
            int length = tArr[0].length;
            for (int i2 = 1; i2 < tArr.length; i2++) {
                T[] tArr4 = tArr[i2];
                int length2 = tArr4.length;
                System.arraycopy(tArr4, 0, tArr3, length, length2);
                length += length2;
            }
            return tArr3;
        }
        return (T[]) ((Object[]) Array.newInstance(tArr.getClass(), 0));
    }

    public static byte[] concatByteArrays(byte[]... arrays) {
        if (arrays.length != 0) {
            int i = 0;
            for (byte[] bArr : arrays) {
                i += bArr.length;
            }
            byte[] copyOf = Arrays.copyOf(arrays[0], i);
            int length = arrays[0].length;
            for (int i2 = 1; i2 < arrays.length; i2++) {
                byte[] bArr2 = arrays[i2];
                int length2 = bArr2.length;
                System.arraycopy(bArr2, 0, copyOf, length, length2);
                length += length2;
            }
            return copyOf;
        }
        return new byte[0];
    }

    public static boolean contains(int[] array, int value) {
        if (array != null) {
            for (int i : array) {
                if (i == value) {
                    return true;
                }
            }
        }
        return false;
    }

    public static <T> ArrayList<T> newArrayList() {
        return new ArrayList<>();
    }

    public static <T> T[] removeAll(T[] tArr, T... tArr2) {
        int length;
        int i;
        if (tArr == null) {
            return null;
        }
        if (tArr2 == null || (length = tArr2.length) == 0) {
            Object[] array = Arrays.copyOf(tArr, tArr.length);
            return (T[]) array;
        }
        Class<?> cls = tArr2.getClass();
        T[] tArr3 = (T[]) ((Object[]) Array.newInstance(cls.getComponentType(), tArr.length));
        if (length == 1) {
            i = 0;
            for (T t : tArr) {
                if (!Objects.equal(tArr2[0], t)) {
                    tArr3[i] = t;
                    i++;
                }
            }
        } else {
            int i2 = 0;
            for (T t2 : tArr) {
                if (!contains(tArr2, t2)) {
                    tArr3[i2] = t2;
                    i2++;
                }
            }
            i = i2;
        }
        if (tArr3 == null) {
            return null;
        }
        if (i == tArr3.length) {
            return tArr3;
        }
        Object[] array2 = Arrays.copyOf(tArr3, i);
        return (T[]) array2;
    }

    public static <T> ArrayList<T> toArrayList(T[] tArr) {
        ArrayList<T> arrayList = new ArrayList<>(tArr.length);
        for (T t : tArr) {
            arrayList.add(t);
        }
        return arrayList;
    }

    public static int[] toPrimitiveArray(Collection<Integer> collection) {
        int i = 0;
        if (collection == null || collection.isEmpty()) {
            return new int[0];
        }
        int[] iArr = new int[collection.size()];
        for (Integer num : collection) {
            iArr[i] = num.intValue();
            i++;
        }
        return iArr;
    }

    public static Integer[] toWrapperArray(int[] array) {
        if (array == null) {
            return null;
        }
        int length = array.length;
        Integer[] numArr = new Integer[length];
        for (int i = 0; i < length; i++) {
            numArr[i] = Integer.valueOf(array[i]);
        }
        return numArr;
    }

    public static void writeArray(StringBuilder sb, double[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(value[i]);
        }
    }

    public static void writeStringArray(StringBuilder sb, String[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("\"");
            sb.append(value[i]);
            sb.append("\"");
        }
    }

    public static <T> boolean contains(T[] tArr, T t) {
        int length = tArr != null ? tArr.length : 0;
        int i = 0;
        while (true) {
            if (i >= length) {
                break;
            } else if (!Objects.equal(tArr[i], t)) {
                i++;
            } else if (i >= 0) {
                return true;
            }
        }
        return false;
    }

    public static void writeArray(StringBuilder sb, float[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(value[i]);
        }
    }

    public static void writeArray(StringBuilder sb, int[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(value[i]);
        }
    }

    public static void writeArray(StringBuilder sb, long[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(value[i]);
        }
    }

    public static <T> void writeArray(StringBuilder sb, T[] tArr) {
        int length = tArr.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(tArr[i]);
        }
    }

    public static void writeArray(StringBuilder sb, boolean[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(value[i]);
        }
    }
}
