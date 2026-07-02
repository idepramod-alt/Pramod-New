package com.google.android.gms.common.util;

import com.google.errorprone.annotations.ResultIgnorabilityUnspecified;
import kotlin.UByte;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public final class HexDumpUtils {
    @ResultIgnorabilityUnspecified
    public static String dump(byte[] data, int offset, int length, boolean outputText) {
        int length2;
        if (data == null || (length2 = data.length) == 0 || offset < 0 || length <= 0 || offset + length > length2) {
            return null;
        }
        StringBuilder sb = new StringBuilder((outputText ? 75 : 57) * ((length + 15) / 16));
        int i = length;
        int i2 = 0;
        int i3 = 0;
        while (i > 0) {
            if (i2 == 0) {
                if (length < 65536) {
                    sb.append(String.format("%04X:", Integer.valueOf(offset)));
                } else {
                    sb.append(String.format("%08X:", Integer.valueOf(offset)));
                }
                i3 = offset;
            } else if (i2 == 8) {
                sb.append(" -");
            }
            sb.append(String.format(" %02X", Integer.valueOf(data[offset] & UByte.MAX_VALUE)));
            i--;
            i2++;
            if (outputText && (i2 == 16 || i == 0)) {
                int i4 = 16 - i2;
                if (i4 > 0) {
                    for (int i5 = 0; i5 < i4; i5++) {
                        sb.append("   ");
                    }
                }
                if (i4 >= 8) {
                    sb.append("  ");
                }
                sb.append("  ");
                for (int i6 = 0; i6 < i2; i6++) {
                    char c = (char) data[i3 + i6];
                    if (c < ' ') {
                        c = '.';
                    } else if (c > '~') {
                        c = '.';
                    }
                    sb.append(c);
                }
            }
            if (i2 == 16 || i == 0) {
                sb.append('\n');
                i2 = 0;
            }
            offset++;
        }
        return sb.toString();
    }
}
