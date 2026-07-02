package com.pramod.loopmidi;

import android.content.Context;
import android.net.Uri;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
/* loaded from: classes3.dex */
public class KitManager {
    public static final String[] DEFAULT_WAV_NAMES = {"crash.wav", "tom.wav", "rim.wav", "clap.wav", "kick.wav", "snare.wav", "ohat.wav", "chat.wav"};
    public static final int PAD_COUNT = 8;

    public static void saveMachineCfgToUri(Context context, Uri cfgUri) throws IOException {
        try {
            OutputStream os = context.getContentResolver().openOutputStream(cfgUri);
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(new String[8]);
            oos.writeObject(new float[8]);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
