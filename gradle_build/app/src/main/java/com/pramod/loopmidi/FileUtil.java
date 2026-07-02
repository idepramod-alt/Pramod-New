package com.pramod.loopmidi;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
/* loaded from: classes3.dex */
public class FileUtil {
    public static void copyUriToUri(Context context, Uri src, Uri dest) throws IOException {
        try {
            InputStream in = context.getContentResolver().openInputStream(src);
            OutputStream out = context.getContentResolver().openOutputStream(dest);
            byte[] buf = new byte[1024];
            while (true) {
                int len = in.read(buf);
                if (len <= 0) {
                    in.close();
                    out.close();
                    return;
                }
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyRawToUri(Context context, int rawResId, Uri dest) throws Resources.NotFoundException, IOException {
        try {
            InputStream in = context.getResources().openRawResource(rawResId);
            OutputStream out = context.getContentResolver().openOutputStream(dest);
            byte[] buf = new byte[1024];
            while (true) {
                int len = in.read(buf);
                if (len <= 0) {
                    in.close();
                    out.close();
                    return;
                }
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
