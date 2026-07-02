package com.google.firebase.database.android;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.database.core.TokenProvider;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
/* loaded from: classes.dex */
public class AndroidAppCheckTokenProvider implements TokenProvider {
    private final Deferred<InteropAppCheckTokenProvider> deferredAppCheckProvider;
    private final AtomicReference<InteropAppCheckTokenProvider> internalAppCheck = new AtomicReference<>();

    public AndroidAppCheckTokenProvider(Deferred<InteropAppCheckTokenProvider> deferredAppCheckProvider) {
        this.deferredAppCheckProvider = deferredAppCheckProvider;
        deferredAppCheckProvider.whenAvailable(new Deferred.DeferredHandler() { // from class: com.google.firebase.database.android.AndroidAppCheckTokenProvider$$ExternalSyntheticLambda1
            @Override // com.google.firebase.inject.Deferred.DeferredHandler
            public final void handle(Provider provider) {
                AndroidAppCheckTokenProvider.this.m123x13f0d4b1(provider);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$new$0$com-google-firebase-database-android-AndroidAppCheckTokenProvider  reason: not valid java name */
    public /* synthetic */ void m123x13f0d4b1(Provider authProvider) {
        this.internalAppCheck.set((InteropAppCheckTokenProvider) authProvider.get());
    }

    @Override // com.google.firebase.database.core.TokenProvider
    public void getToken(boolean forceRefresh, final TokenProvider.GetTokenCompletionListener listener) {
        InteropAppCheckTokenProvider appCheckProvider = this.internalAppCheck.get();
        if (appCheckProvider != null) {
            Task<AppCheckTokenResult> getTokenResult = appCheckProvider.getToken(forceRefresh);
            getTokenResult.addOnSuccessListener(new OnSuccessListener() { // from class: com.google.firebase.database.android.AndroidAppCheckTokenProvider$$ExternalSyntheticLambda4
                @Override // com.google.android.gms.tasks.OnSuccessListener
                public final void onSuccess(Object obj) {
                    TokenProvider.GetTokenCompletionListener.this.onSuccess(((AppCheckTokenResult) obj).getToken());
                }
            }).addOnFailureListener(new OnFailureListener() { // from class: com.google.firebase.database.android.AndroidAppCheckTokenProvider$$ExternalSyntheticLambda5
                @Override // com.google.android.gms.tasks.OnFailureListener
                public final void onFailure(Exception exc) {
                    TokenProvider.GetTokenCompletionListener.this.onError(exc.getMessage());
                }
            });
            return;
        }
        listener.onSuccess(null);
    }

    @Override // com.google.firebase.database.core.TokenProvider
    public void addTokenChangeListener(final ExecutorService executorService, final TokenProvider.TokenChangeListener tokenListener) {
        this.deferredAppCheckProvider.whenAvailable(new Deferred.DeferredHandler() { // from class: com.google.firebase.database.android.AndroidAppCheckTokenProvider$$ExternalSyntheticLambda3
            @Override // com.google.firebase.inject.Deferred.DeferredHandler
            public final void handle(Provider provider) {
                ((InteropAppCheckTokenProvider) provider.get()).addAppCheckTokenListener(new AppCheckTokenListener() { // from class: com.google.firebase.database.android.AndroidAppCheckTokenProvider$$ExternalSyntheticLambda2
                    @Override // com.google.firebase.appcheck.interop.AppCheckTokenListener
                    public final void onAppCheckTokenChanged(AppCheckTokenResult appCheckTokenResult) {
                        r1.execute(new Runnable() { // from class: com.google.firebase.database.android.AndroidAppCheckTokenProvider$$ExternalSyntheticLambda0
                            @Override // java.lang.Runnable
                            public final void run() {
                                TokenProvider.TokenChangeListener.this.onTokenChange(appCheckTokenResult.getToken());
                            }
                        });
                    }
                });
            }
        });
    }

    @Override // com.google.firebase.database.core.TokenProvider
    public void removeTokenChangeListener(TokenProvider.TokenChangeListener tokenListener) {
    }
}
