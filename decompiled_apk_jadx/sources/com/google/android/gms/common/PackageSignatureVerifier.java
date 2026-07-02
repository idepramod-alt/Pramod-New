package com.google.android.gms.common;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.common.internal.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.RestrictedInheritance;
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
@CheckReturnValue
@RestrictedInheritance(allowedOnPath = ".*javatests.*/com/google/android/gms/common/.*", explanation = "Sub classing of GMS Core's APIs are restricted to testing fakes.", link = "go/gmscore-restrictedinheritance")
/* loaded from: classes.dex */
public class PackageSignatureVerifier {
    static volatile zzac zza;
    private static zzad zzb;

    private static zzad zza(Context context) {
        zzad zzadVar;
        synchronized (PackageSignatureVerifier.class) {
            if (zzb == null) {
                zzb = new zzad(context);
            }
            zzadVar = zzb;
        }
        return zzadVar;
    }

    public PackageVerificationResult queryPackageSignatureVerified(Context context, String callingPackage) {
        PackageVerificationResult packageVerificationResult;
        String str;
        PackageVerificationResult packageVerificationResult2;
        boolean honorsDebugCertificates = GooglePlayServicesUtilLight.honorsDebugCertificates(context);
        zza(context);
        if (zzn.zzf()) {
            String concat = String.valueOf(callingPackage).concat(true != honorsDebugCertificates ? "-0" : "-1");
            if (zza != null) {
                str = zza.zza;
                if (str.equals(concat)) {
                    packageVerificationResult2 = zza.zzb;
                    return packageVerificationResult2;
                }
            }
            zza(context);
            zzx zzc = zzn.zzc(callingPackage, honorsDebugCertificates, false, false);
            if (!zzc.zza) {
                Preconditions.checkNotNull(zzc.zzb);
                return PackageVerificationResult.zza(callingPackage, zzc.zzb, zzc.zzc);
            }
            zza = new zzac(concat, PackageVerificationResult.zzd(callingPackage, zzc.zzd));
            packageVerificationResult = zza.zzb;
            return packageVerificationResult;
        }
        throw new zzae();
    }

    public PackageVerificationResult queryPackageSignatureVerifiedWithRetry(Context context, String callingPackage) {
        try {
            PackageVerificationResult queryPackageSignatureVerified = queryPackageSignatureVerified(context, callingPackage);
            queryPackageSignatureVerified.zzb();
            return queryPackageSignatureVerified;
        } catch (SecurityException e) {
            PackageVerificationResult queryPackageSignatureVerified2 = queryPackageSignatureVerified(context, callingPackage);
            if (!queryPackageSignatureVerified2.zzc()) {
                return queryPackageSignatureVerified2;
            }
            Log.e("PkgSignatureVerifier", "Got flaky result during package signature verification", e);
            return queryPackageSignatureVerified2;
        }
    }
}
