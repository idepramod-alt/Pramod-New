package com.google.android.gms.common.util;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public class MurmurHash3 {
    private MurmurHash3() {
    }

    public static int murmurhash3_x86_32(byte[] data, int offset, int len, int seed) {
        int i;
        int i2 = offset;
        while (true) {
            i = (len & (-4)) + offset;
            if (i2 >= i) {
                break;
            }
            int i3 = ((data[i2] & 255) | ((data[i2 + 1] & 255) << 8) | ((data[i2 + 2] & 255) << 16) | (data[i2 + 3] << 24)) * (-862048943);
            int i4 = seed ^ (((i3 >>> 17) | (i3 << 15)) * 461845907);
            seed = (((i4 >>> 19) | (i4 << 13)) * 5) - 430675100;
            i2 += 4;
        }
        int i5 = 0;
        switch (len & 3) {
            case 3:
                i5 = (data[i + 2] & 255) << 16;
            case 2:
                i5 |= (data[i + 1] & 255) << 8;
            case 1:
                int i6 = ((data[i] & 255) | i5) * (-862048943);
                seed ^= ((i6 >>> 17) | (i6 << 15)) * 461845907;
                break;
        }
        int i7 = seed ^ len;
        int i8 = (i7 ^ (i7 >>> 16)) * (-2048144789);
        int i9 = (i8 ^ (i8 >>> 13)) * (-1028477387);
        return i9 ^ (i9 >>> 16);
    }
}
