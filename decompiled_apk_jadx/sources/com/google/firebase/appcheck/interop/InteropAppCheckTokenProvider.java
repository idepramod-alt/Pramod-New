package com.google.firebase.appcheck.interop;

import com.google.android.gms.tasks.Task;
import com.google.firebase.appcheck.AppCheckTokenResult;
/* loaded from: classes.dex */
public interface InteropAppCheckTokenProvider {
    void addAppCheckTokenListener(AppCheckTokenListener appCheckTokenListener);

    Task<AppCheckTokenResult> getLimitedUseToken();

    Task<AppCheckTokenResult> getToken(boolean z);

    void removeAppCheckTokenListener(AppCheckTokenListener appCheckTokenListener);
}
