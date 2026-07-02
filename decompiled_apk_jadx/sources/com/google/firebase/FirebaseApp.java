package com.google.firebase;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.Log;
import androidx.collection.ArrayMap;
import androidx.core.os.UserManagerCompat;
import androidx.lifecycle.LifecycleKt$$ExternalSyntheticBackportWithForwarding0;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.Base64Utils;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.android.gms.common.util.ProcessUtils;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentDiscovery;
import com.google.firebase.components.ComponentDiscoveryService;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.components.Lazy;
import com.google.firebase.concurrent.ExecutorsRegistrar;
import com.google.firebase.concurrent.UiExecutor;
import com.google.firebase.events.Publisher;
import com.google.firebase.heartbeatinfo.DefaultHeartBeatController;
import com.google.firebase.inject.Provider;
import com.google.firebase.internal.DataCollectionConfigStorage;
import com.google.firebase.provider.FirebaseInitProvider;
import com.google.firebase.tracing.ComponentMonitor;
import com.google.firebase.tracing.FirebaseTrace;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
/* loaded from: classes.dex */
public class FirebaseApp {
    public static final String DEFAULT_APP_NAME = "[DEFAULT]";
    private static final String LOG_TAG = "FirebaseApp";
    private final Context applicationContext;
    private final ComponentRuntime componentRuntime;
    private final Lazy<DataCollectionConfigStorage> dataCollectionConfigStorage;
    private final Provider<DefaultHeartBeatController> defaultHeartBeatController;
    private final String name;
    private final FirebaseOptions options;
    private static final Object LOCK = new Object();
    static final Map<String, FirebaseApp> INSTANCES = new ArrayMap();
    private final AtomicBoolean automaticResourceManagementEnabled = new AtomicBoolean(false);
    private final AtomicBoolean deleted = new AtomicBoolean();
    private final List<BackgroundStateChangeListener> backgroundStateChangeListeners = new CopyOnWriteArrayList();
    private final List<FirebaseAppLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList();

    /* loaded from: classes.dex */
    public interface BackgroundStateChangeListener {
        void onBackgroundStateChanged(boolean z);
    }

    public Context getApplicationContext() {
        checkNotDeleted();
        return this.applicationContext;
    }

    public String getName() {
        checkNotDeleted();
        return this.name;
    }

    public FirebaseOptions getOptions() {
        checkNotDeleted();
        return this.options;
    }

    public boolean equals(Object o) {
        if (!(o instanceof FirebaseApp)) {
            return false;
        }
        return this.name.equals(((FirebaseApp) o).getName());
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public String toString() {
        return Objects.toStringHelper(this).add("name", this.name).add("options", this.options).toString();
    }

    public static List<FirebaseApp> getApps(Context context) {
        ArrayList arrayList;
        synchronized (LOCK) {
            arrayList = new ArrayList(INSTANCES.values());
        }
        return arrayList;
    }

    public static FirebaseApp getInstance() {
        FirebaseApp defaultApp;
        synchronized (LOCK) {
            defaultApp = INSTANCES.get(DEFAULT_APP_NAME);
            if (defaultApp == null) {
                throw new IllegalStateException("Default FirebaseApp is not initialized in this process " + ProcessUtils.getMyProcessName() + ". Make sure to call FirebaseApp.initializeApp(Context) first.");
            }
            defaultApp.defaultHeartBeatController.get().registerHeartBeat();
        }
        return defaultApp;
    }

    public static FirebaseApp getInstance(String name) {
        FirebaseApp firebaseApp;
        String availableAppNamesMessage;
        synchronized (LOCK) {
            firebaseApp = INSTANCES.get(normalize(name));
            if (firebaseApp != null) {
                firebaseApp.defaultHeartBeatController.get().registerHeartBeat();
            } else {
                List<String> availableAppNames = getAllAppNames();
                if (availableAppNames.isEmpty()) {
                    availableAppNamesMessage = "";
                } else {
                    availableAppNamesMessage = "Available app names: " + TextUtils.join(", ", availableAppNames);
                }
                String errorMessage = String.format("FirebaseApp with name %s doesn't exist. %s", name, availableAppNamesMessage);
                throw new IllegalStateException(errorMessage);
            }
        }
        return firebaseApp;
    }

    public static FirebaseApp initializeApp(Context context) {
        synchronized (LOCK) {
            if (INSTANCES.containsKey(DEFAULT_APP_NAME)) {
                return getInstance();
            }
            FirebaseOptions firebaseOptions = FirebaseOptions.fromResource(context);
            if (firebaseOptions == null) {
                Log.w(LOG_TAG, "Default FirebaseApp failed to initialize because no default options were found. This usually means that com.google.gms:google-services was not applied to your gradle project.");
                return null;
            }
            return initializeApp(context, firebaseOptions);
        }
    }

    public static FirebaseApp initializeApp(Context context, FirebaseOptions options) {
        return initializeApp(context, options, DEFAULT_APP_NAME);
    }

    public static FirebaseApp initializeApp(Context context, FirebaseOptions options, String name) {
        Context applicationContext;
        FirebaseApp firebaseApp;
        GlobalBackgroundStateListener.ensureBackgroundStateListenerRegistered(context);
        String normalizedName = normalize(name);
        if (context.getApplicationContext() == null) {
            applicationContext = context;
        } else {
            applicationContext = context.getApplicationContext();
        }
        synchronized (LOCK) {
            Preconditions.checkState(!INSTANCES.containsKey(normalizedName), "FirebaseApp name " + normalizedName + " already exists!");
            Preconditions.checkNotNull(applicationContext, "Application context cannot be null.");
            firebaseApp = new FirebaseApp(applicationContext, normalizedName, options);
            INSTANCES.put(normalizedName, firebaseApp);
        }
        firebaseApp.initializeAllApis();
        return firebaseApp;
    }

    public void delete() {
        boolean valueChanged = this.deleted.compareAndSet(false, true);
        if (!valueChanged) {
            return;
        }
        synchronized (LOCK) {
            INSTANCES.remove(this.name);
        }
        notifyOnAppDeleted();
    }

    public <T> T get(Class<T> anInterface) {
        checkNotDeleted();
        return (T) this.componentRuntime.get(anInterface);
    }

    public void setAutomaticResourceManagementEnabled(boolean enabled) {
        checkNotDeleted();
        boolean updated = this.automaticResourceManagementEnabled.compareAndSet(!enabled, enabled);
        if (updated) {
            boolean inBackground = BackgroundDetector.getInstance().isInBackground();
            if (enabled && inBackground) {
                notifyBackgroundStateChangeListeners(true);
            } else if (!enabled && inBackground) {
                notifyBackgroundStateChangeListeners(false);
            }
        }
    }

    public boolean isDataCollectionDefaultEnabled() {
        checkNotDeleted();
        return this.dataCollectionConfigStorage.get().isEnabled();
    }

    public void setDataCollectionDefaultEnabled(Boolean enabled) {
        checkNotDeleted();
        this.dataCollectionConfigStorage.get().setEnabled(enabled);
    }

    @Deprecated
    public void setDataCollectionDefaultEnabled(boolean enabled) {
        setDataCollectionDefaultEnabled(Boolean.valueOf(enabled));
    }

    protected FirebaseApp(final Context applicationContext, String name, FirebaseOptions options) {
        this.applicationContext = (Context) Preconditions.checkNotNull(applicationContext);
        this.name = Preconditions.checkNotEmpty(name);
        this.options = (FirebaseOptions) Preconditions.checkNotNull(options);
        StartupTime startupTime = FirebaseInitProvider.getStartupTime();
        FirebaseTrace.pushTrace("Firebase");
        FirebaseTrace.pushTrace("ComponentDiscovery");
        List<Provider<ComponentRegistrar>> registrars = ComponentDiscovery.forContext(applicationContext, ComponentDiscoveryService.class).discoverLazy();
        FirebaseTrace.popTrace();
        FirebaseTrace.pushTrace("Runtime");
        ComponentRuntime.Builder builder = ComponentRuntime.builder(UiExecutor.INSTANCE).addLazyComponentRegistrars(registrars).addComponentRegistrar(new FirebaseCommonRegistrar()).addComponentRegistrar(new ExecutorsRegistrar()).addComponent(Component.of(applicationContext, Context.class, new Class[0])).addComponent(Component.of(this, FirebaseApp.class, new Class[0])).addComponent(Component.of(options, FirebaseOptions.class, new Class[0])).setProcessor(new ComponentMonitor());
        if (UserManagerCompat.isUserUnlocked(applicationContext) && FirebaseInitProvider.isCurrentlyInitializing()) {
            builder.addComponent(Component.of(startupTime, StartupTime.class, new Class[0]));
        }
        this.componentRuntime = builder.build();
        FirebaseTrace.popTrace();
        this.dataCollectionConfigStorage = new Lazy<>(new Provider() { // from class: com.google.firebase.FirebaseApp$$ExternalSyntheticLambda0
            @Override // com.google.firebase.inject.Provider
            public final Object get() {
                return FirebaseApp.this.m109lambda$new$0$comgooglefirebaseFirebaseApp(applicationContext);
            }
        });
        this.defaultHeartBeatController = this.componentRuntime.getProvider(DefaultHeartBeatController.class);
        addBackgroundStateChangeListener(new BackgroundStateChangeListener() { // from class: com.google.firebase.FirebaseApp$$ExternalSyntheticLambda1
            @Override // com.google.firebase.FirebaseApp.BackgroundStateChangeListener
            public final void onBackgroundStateChanged(boolean z) {
                FirebaseApp.this.m110lambda$new$1$comgooglefirebaseFirebaseApp(z);
            }
        });
        FirebaseTrace.popTrace();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$new$0$com-google-firebase-FirebaseApp  reason: not valid java name */
    public /* synthetic */ DataCollectionConfigStorage m109lambda$new$0$comgooglefirebaseFirebaseApp(Context applicationContext) {
        return new DataCollectionConfigStorage(applicationContext, getPersistenceKey(), (Publisher) this.componentRuntime.get(Publisher.class));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$new$1$com-google-firebase-FirebaseApp  reason: not valid java name */
    public /* synthetic */ void m110lambda$new$1$comgooglefirebaseFirebaseApp(boolean background) {
        if (!background) {
            this.defaultHeartBeatController.get().registerHeartBeat();
        }
    }

    private void checkNotDeleted() {
        Preconditions.checkState(!this.deleted.get(), "FirebaseApp was deleted");
    }

    public boolean isDefaultApp() {
        return DEFAULT_APP_NAME.equals(getName());
    }

    void initializeAllComponents() {
        this.componentRuntime.initializeAllComponentsForTests();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyBackgroundStateChangeListeners(boolean background) {
        Log.d(LOG_TAG, "Notifying background state change listeners.");
        for (BackgroundStateChangeListener listener : this.backgroundStateChangeListeners) {
            listener.onBackgroundStateChanged(background);
        }
    }

    public void addBackgroundStateChangeListener(BackgroundStateChangeListener listener) {
        checkNotDeleted();
        if (this.automaticResourceManagementEnabled.get() && BackgroundDetector.getInstance().isInBackground()) {
            listener.onBackgroundStateChanged(true);
        }
        this.backgroundStateChangeListeners.add(listener);
    }

    public void removeBackgroundStateChangeListener(BackgroundStateChangeListener listener) {
        checkNotDeleted();
        this.backgroundStateChangeListeners.remove(listener);
    }

    public String getPersistenceKey() {
        return Base64Utils.encodeUrlSafeNoPadding(getName().getBytes(Charset.defaultCharset())) + "+" + Base64Utils.encodeUrlSafeNoPadding(getOptions().getApplicationId().getBytes(Charset.defaultCharset()));
    }

    public void addLifecycleEventListener(FirebaseAppLifecycleListener listener) {
        checkNotDeleted();
        Preconditions.checkNotNull(listener);
        this.lifecycleListeners.add(listener);
    }

    public void removeLifecycleEventListener(FirebaseAppLifecycleListener listener) {
        checkNotDeleted();
        Preconditions.checkNotNull(listener);
        this.lifecycleListeners.remove(listener);
    }

    private void notifyOnAppDeleted() {
        for (FirebaseAppLifecycleListener listener : this.lifecycleListeners) {
            listener.onDeleted(this.name, this.options);
        }
    }

    public static void clearInstancesForTest() {
        synchronized (LOCK) {
            INSTANCES.clear();
        }
    }

    public static String getPersistenceKey(String name, FirebaseOptions options) {
        return Base64Utils.encodeUrlSafeNoPadding(name.getBytes(Charset.defaultCharset())) + "+" + Base64Utils.encodeUrlSafeNoPadding(options.getApplicationId().getBytes(Charset.defaultCharset()));
    }

    private static List<String> getAllAppNames() {
        List<String> allAppNames = new ArrayList<>();
        synchronized (LOCK) {
            for (FirebaseApp app : INSTANCES.values()) {
                allAppNames.add(app.getName());
            }
        }
        Collections.sort(allAppNames);
        return allAppNames;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initializeAllApis() {
        boolean inDirectBoot = !UserManagerCompat.isUserUnlocked(this.applicationContext);
        if (!inDirectBoot) {
            Log.i(LOG_TAG, "Device unlocked: initializing all Firebase APIs for app " + getName());
            this.componentRuntime.initializeEagerComponents(isDefaultApp());
            this.defaultHeartBeatController.get().registerHeartBeat();
            return;
        }
        Log.i(LOG_TAG, "Device in Direct Boot Mode: postponing initialization of Firebase APIs for app " + getName());
        UserUnlockReceiver.ensureReceiverRegistered(this.applicationContext);
    }

    private static String normalize(String name) {
        return name.trim();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class UserUnlockReceiver extends BroadcastReceiver {
        private static AtomicReference<UserUnlockReceiver> INSTANCE = new AtomicReference<>();
        private final Context applicationContext;

        public UserUnlockReceiver(Context applicationContext) {
            this.applicationContext = applicationContext;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void ensureReceiverRegistered(Context applicationContext) {
            if (INSTANCE.get() == null) {
                UserUnlockReceiver receiver = new UserUnlockReceiver(applicationContext);
                if (LifecycleKt$$ExternalSyntheticBackportWithForwarding0.m(INSTANCE, null, receiver)) {
                    IntentFilter intentFilter = new IntentFilter("android.intent.action.USER_UNLOCKED");
                    applicationContext.registerReceiver(receiver, intentFilter);
                }
            }
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            synchronized (FirebaseApp.LOCK) {
                for (FirebaseApp app : FirebaseApp.INSTANCES.values()) {
                    app.initializeAllApis();
                }
            }
            unregister();
        }

        public void unregister() {
            this.applicationContext.unregisterReceiver(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class GlobalBackgroundStateListener implements BackgroundDetector.BackgroundStateChangeListener {
        private static AtomicReference<GlobalBackgroundStateListener> INSTANCE = new AtomicReference<>();

        private GlobalBackgroundStateListener() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void ensureBackgroundStateListenerRegistered(Context context) {
            if (!PlatformVersion.isAtLeastIceCreamSandwich() || !(context.getApplicationContext() instanceof Application)) {
                return;
            }
            Application application = (Application) context.getApplicationContext();
            if (INSTANCE.get() == null) {
                GlobalBackgroundStateListener listener = new GlobalBackgroundStateListener();
                if (LifecycleKt$$ExternalSyntheticBackportWithForwarding0.m(INSTANCE, null, listener)) {
                    BackgroundDetector.initialize(application);
                    BackgroundDetector.getInstance().addListener(listener);
                }
            }
        }

        @Override // com.google.android.gms.common.api.internal.BackgroundDetector.BackgroundStateChangeListener
        public void onBackgroundStateChanged(boolean background) {
            synchronized (FirebaseApp.LOCK) {
                Iterator it = new ArrayList(FirebaseApp.INSTANCES.values()).iterator();
                while (it.hasNext()) {
                    FirebaseApp app = (FirebaseApp) it.next();
                    if (app.automaticResourceManagementEnabled.get()) {
                        app.notifyBackgroundStateChangeListeners(background);
                    }
                }
            }
        }
    }
}
