package com.google.android.gms.common.internal;

import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.common.stats.ConnectionTracker;
import java.util.HashMap;
import java.util.concurrent.Executor;
/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: com.google.android.gms:play-services-basement@@18.3.0 */
/* loaded from: classes.dex */
public final class zzs extends GmsClientSupervisor {
    private final Context zzc;
    private volatile Handler zzd;
    private volatile Executor zzi;
    private final HashMap zzb = new HashMap();
    private final zzr zze = new zzr(this, null);
    private final ConnectionTracker zzf = ConnectionTracker.getInstance();
    private final long zzg = 5000;
    private final long zzh = 300000;

    /* JADX INFO: Access modifiers changed from: package-private */
    public zzs(Context context, Looper looper, Executor executor) {
        this.zzc = context.getApplicationContext();
        this.zzd = new com.google.android.gms.internal.common.zzi(looper, this.zze);
        this.zzi = executor;
    }

    @Override // com.google.android.gms.common.internal.GmsClientSupervisor
    protected final void zza(zzo zzoVar, ServiceConnection serviceConnection, String str) {
        Preconditions.checkNotNull(serviceConnection, "ServiceConnection must not be null");
        synchronized (this.zzb) {
            zzp zzpVar = (zzp) this.zzb.get(zzoVar);
            if (zzpVar == null) {
                String obj = zzoVar.toString();
                throw new IllegalStateException("Nonexistent connection status for service config: " + obj);
            } else if (!zzpVar.zzh(serviceConnection)) {
                String obj2 = zzoVar.toString();
                throw new IllegalStateException("Trying to unbind a GmsServiceConnection  that was not bound before.  config=" + obj2);
            } else {
                zzpVar.zzf(serviceConnection, str);
                if (zzpVar.zzi()) {
                    this.zzd.sendMessageDelayed(this.zzd.obtainMessage(0, zzoVar), this.zzg);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.google.android.gms.common.internal.GmsClientSupervisor
    public final boolean zzc(zzo zzoVar, ServiceConnection serviceConnection, String str, Executor executor) {
        boolean zzj;
        Preconditions.checkNotNull(serviceConnection, "ServiceConnection must not be null");
        synchronized (this.zzb) {
            zzp zzpVar = (zzp) this.zzb.get(zzoVar);
            if (executor == null) {
                executor = this.zzi;
            }
            if (zzpVar == null) {
                zzpVar = new zzp(this, zzoVar);
                zzpVar.zzd(serviceConnection, serviceConnection, str);
                zzpVar.zze(str, executor);
                this.zzb.put(zzoVar, zzpVar);
            } else {
                this.zzd.removeMessages(0, zzoVar);
                if (zzpVar.zzh(serviceConnection)) {
                    String obj = zzoVar.toString();
                    throw new IllegalStateException("Trying to bind a GmsServiceConnection that was already connected before.  config=" + obj);
                }
                zzpVar.zzd(serviceConnection, serviceConnection, str);
                switch (zzpVar.zza()) {
                    case 1:
                        serviceConnection.onServiceConnected(zzpVar.zzb(), zzpVar.zzc());
                        break;
                    case 2:
                        zzpVar.zze(str, executor);
                        break;
                }
            }
            zzj = zzpVar.zzj();
        }
        return zzj;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public final void zzi(Executor executor) {
        synchronized (this.zzb) {
            this.zzi = executor;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public final void zzj(Looper looper) {
        synchronized (this.zzb) {
            this.zzd = new com.google.android.gms.internal.common.zzi(looper, this.zze);
        }
    }
}
