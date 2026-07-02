package com.google.android.gms.common.internal;

import java.util.concurrent.ConcurrentHashMap;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
@Deprecated
/* loaded from: classes.dex */
public class LibraryVersion {
    private static final GmsLogger zza = new GmsLogger("LibraryVersion", "");
    private static final LibraryVersion zzb = new LibraryVersion();
    private final ConcurrentHashMap zzc = new ConcurrentHashMap();

    protected LibraryVersion() {
    }

    public static LibraryVersion getInstance() {
        return zzb;
    }

    /* JADX WARN: Removed duplicated region for block: B:23:0x0091  */
    /* JADX WARN: Removed duplicated region for block: B:25:0x0096  */
    /* JADX WARN: Removed duplicated region for block: B:30:0x00a8  */
    @java.lang.Deprecated
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public java.lang.String getVersion(java.lang.String r9) {
        /*
            r8 = this;
            java.lang.String r0 = "Failed to get app version for libraryName: "
            java.lang.String r1 = "LibraryVersion"
            java.lang.String r2 = "Please provide a valid libraryName"
            com.google.android.gms.common.internal.Preconditions.checkNotEmpty(r9, r2)
            java.util.concurrent.ConcurrentHashMap r2 = r8.zzc
            boolean r2 = r2.containsKey(r9)
            if (r2 == 0) goto L1a
            java.util.concurrent.ConcurrentHashMap r0 = r8.zzc
            java.lang.Object r9 = r0.get(r9)
            java.lang.String r9 = (java.lang.String) r9
            return r9
        L1a:
            java.util.Properties r2 = new java.util.Properties
            r2.<init>()
            r3 = 0
            java.lang.String r4 = "/%s.properties"
            r5 = 1
            java.lang.Object[] r5 = new java.lang.Object[r5]     // Catch: java.lang.Throwable -> L74 java.io.IOException -> L76
            r6 = 0
            r5[r6] = r9     // Catch: java.lang.Throwable -> L74 java.io.IOException -> L76
            java.lang.String r4 = java.lang.String.format(r4, r5)     // Catch: java.lang.Throwable -> L74 java.io.IOException -> L76
            java.lang.Class<com.google.android.gms.common.internal.LibraryVersion> r5 = com.google.android.gms.common.internal.LibraryVersion.class
            java.io.InputStream r4 = r5.getResourceAsStream(r4)     // Catch: java.lang.Throwable -> L74 java.io.IOException -> L76
            if (r4 == 0) goto L57
            r2.load(r4)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            java.lang.String r5 = "version"
            java.lang.String r3 = r2.getProperty(r5, r3)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            com.google.android.gms.common.internal.GmsLogger r2 = com.google.android.gms.common.internal.LibraryVersion.zza     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r5.<init>()     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r5.append(r9)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            java.lang.String r6 = " version is "
            r5.append(r6)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r5.append(r3)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            java.lang.String r5 = r5.toString()     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r2.v(r1, r5)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            goto L8f
        L57:
            com.google.android.gms.common.internal.GmsLogger r2 = com.google.android.gms.common.internal.LibraryVersion.zza     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r5.<init>()     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r5.append(r0)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r5.append(r9)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            java.lang.String r5 = r5.toString()     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            r2.w(r1, r5)     // Catch: java.lang.Throwable -> L6c java.io.IOException -> L6f
            goto L8f
        L6c:
            r9 = move-exception
            r3 = r4
            goto La6
        L6f:
            r2 = move-exception
            r7 = r4
            r4 = r3
            r3 = r7
            goto L78
        L74:
            r9 = move-exception
            goto La6
        L76:
            r2 = move-exception
            r4 = r3
        L78:
            com.google.android.gms.common.internal.GmsLogger r5 = com.google.android.gms.common.internal.LibraryVersion.zza     // Catch: java.lang.Throwable -> La5
            java.lang.StringBuilder r6 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> La5
            r6.<init>()     // Catch: java.lang.Throwable -> La5
            r6.append(r0)     // Catch: java.lang.Throwable -> La5
            r6.append(r9)     // Catch: java.lang.Throwable -> La5
            java.lang.String r0 = r6.toString()     // Catch: java.lang.Throwable -> La5
            r5.e(r1, r0, r2)     // Catch: java.lang.Throwable -> La5
            r7 = r4
            r4 = r3
            r3 = r7
        L8f:
            if (r4 == 0) goto L94
            com.google.android.gms.common.util.IOUtils.closeQuietly(r4)
        L94:
            if (r3 != 0) goto L9f
            com.google.android.gms.common.internal.GmsLogger r0 = com.google.android.gms.common.internal.LibraryVersion.zza
            java.lang.String r2 = ".properties file is dropped during release process. Failure to read app version is expected during Google internal testing where locally-built libraries are used"
            r0.d(r1, r2)
            java.lang.String r3 = "UNKNOWN"
        L9f:
            java.util.concurrent.ConcurrentHashMap r0 = r8.zzc
            r0.put(r9, r3)
            return r3
        La5:
            r9 = move-exception
        La6:
            if (r3 == 0) goto Lab
            com.google.android.gms.common.util.IOUtils.closeQuietly(r3)
        Lab:
            throw r9
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.gms.common.internal.LibraryVersion.getVersion(java.lang.String):java.lang.String");
    }
}
