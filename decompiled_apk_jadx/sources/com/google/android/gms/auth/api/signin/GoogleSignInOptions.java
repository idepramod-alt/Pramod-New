package com.google.android.gms.auth.api.signin;

import android.accounts.Account;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import com.google.android.gms.auth.api.signin.internal.GoogleSignInOptionsExtensionParcelable;
import com.google.android.gms.auth.api.signin.internal.HashAccumulator;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.internal.AccountType;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.internal.ReflectedParcelable;
import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/* compiled from: com.google.android.gms:play-services-base@@18.1.0 */
/* loaded from: classes.dex */
public class GoogleSignInOptions extends AbstractSafeParcelable implements Api.ApiOptions.Optional, ReflectedParcelable {
    public static final Parcelable.Creator<GoogleSignInOptions> CREATOR;
    public static final GoogleSignInOptions DEFAULT_GAMES_SIGN_IN;
    public static final GoogleSignInOptions DEFAULT_SIGN_IN;
    public static final Scope zaa = new Scope(Scopes.PROFILE);
    public static final Scope zab = new Scope("email");
    public static final Scope zac = new Scope(Scopes.OPEN_ID);
    public static final Scope zad = new Scope(Scopes.GAMES_LITE);
    public static final Scope zae = new Scope(Scopes.GAMES);
    private static Comparator zag;
    final int zaf;
    private final ArrayList zah;
    private Account zai;
    private boolean zaj;
    private final boolean zak;
    private final boolean zal;
    private String zam;
    private String zan;
    private ArrayList zao;
    private String zap;
    private Map zaq;

    /* compiled from: com.google.android.gms:play-services-base@@18.1.0 */
    /* loaded from: classes.dex */
    public static final class Builder {
        private Set zaa;
        private boolean zab;
        private boolean zac;
        private boolean zad;
        private String zae;
        private Account zaf;
        private String zag;
        private Map zah;
        private String zai;

        public Builder() {
            this.zaa = new HashSet();
            this.zah = new HashMap();
        }

        private final String zaa(String str) {
            Preconditions.checkNotEmpty(str);
            String str2 = this.zae;
            boolean z = true;
            if (str2 != null && !str2.equals(str)) {
                z = false;
            }
            Preconditions.checkArgument(z, "two different server client ids provided");
            return str;
        }

        public Builder addExtension(GoogleSignInOptionsExtension extension) {
            if (this.zah.containsKey(Integer.valueOf(extension.getExtensionType()))) {
                throw new IllegalStateException("Only one extension per type may be added");
            }
            List<Scope> impliedScopes = extension.getImpliedScopes();
            if (impliedScopes != null) {
                this.zaa.addAll(impliedScopes);
            }
            this.zah.put(Integer.valueOf(extension.getExtensionType()), new GoogleSignInOptionsExtensionParcelable(extension));
            return this;
        }

        public GoogleSignInOptions build() {
            if (this.zaa.contains(GoogleSignInOptions.zae) && this.zaa.contains(GoogleSignInOptions.zad)) {
                this.zaa.remove(GoogleSignInOptions.zad);
            }
            if (this.zad && (this.zaf == null || !this.zaa.isEmpty())) {
                requestId();
            }
            return new GoogleSignInOptions(new ArrayList(this.zaa), this.zaf, this.zad, this.zab, this.zac, this.zae, this.zag, this.zah, this.zai);
        }

        public Builder requestEmail() {
            this.zaa.add(GoogleSignInOptions.zab);
            return this;
        }

        public Builder requestId() {
            this.zaa.add(GoogleSignInOptions.zac);
            return this;
        }

        public Builder requestIdToken(String serverClientId) {
            this.zad = true;
            zaa(serverClientId);
            this.zae = serverClientId;
            return this;
        }

        public Builder requestProfile() {
            this.zaa.add(GoogleSignInOptions.zaa);
            return this;
        }

        public Builder requestScopes(Scope scope, Scope... scopes) {
            this.zaa.add(scope);
            this.zaa.addAll(Arrays.asList(scopes));
            return this;
        }

        public Builder requestServerAuthCode(String serverClientId) {
            requestServerAuthCode(serverClientId, false);
            return this;
        }

        public Builder setAccountName(String accountName) {
            this.zaf = new Account(Preconditions.checkNotEmpty(accountName), AccountType.GOOGLE);
            return this;
        }

        public Builder setHostedDomain(String hostedDomain) {
            this.zag = Preconditions.checkNotEmpty(hostedDomain);
            return this;
        }

        public Builder setLogSessionId(String str) {
            this.zai = str;
            return this;
        }

        public Builder requestServerAuthCode(String serverClientId, boolean forceCodeForRefreshToken) {
            this.zab = true;
            zaa(serverClientId);
            this.zae = serverClientId;
            this.zac = forceCodeForRefreshToken;
            return this;
        }

        public Builder(GoogleSignInOptions googleSignInOptions) {
            this.zaa = new HashSet();
            this.zah = new HashMap();
            Preconditions.checkNotNull(googleSignInOptions);
            this.zaa = new HashSet(googleSignInOptions.zah);
            this.zab = googleSignInOptions.zak;
            this.zac = googleSignInOptions.zal;
            this.zad = googleSignInOptions.zaj;
            this.zae = googleSignInOptions.zam;
            this.zaf = googleSignInOptions.zai;
            this.zag = googleSignInOptions.zan;
            this.zah = GoogleSignInOptions.zam(googleSignInOptions.zao);
            this.zai = googleSignInOptions.zap;
        }
    }

    static {
        Builder builder = new Builder();
        builder.requestId();
        builder.requestProfile();
        DEFAULT_SIGN_IN = builder.build();
        Builder builder2 = new Builder();
        builder2.requestScopes(zad, new Scope[0]);
        DEFAULT_GAMES_SIGN_IN = builder2.build();
        CREATOR = new zae();
        zag = new zac();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public GoogleSignInOptions(int i, ArrayList arrayList, Account account, boolean z, boolean z2, boolean z3, String str, String str2, ArrayList arrayList2, String str3) {
        this(i, arrayList, account, z, z2, z3, str, str2, zam(arrayList2), str3);
    }

    public static GoogleSignInOptions zab(String str) throws JSONException {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        JSONObject jSONObject = new JSONObject(str);
        HashSet hashSet = new HashSet();
        JSONArray jSONArray = jSONObject.getJSONArray("scopes");
        int length = jSONArray.length();
        for (int i = 0; i < length; i++) {
            hashSet.add(new Scope(jSONArray.getString(i)));
        }
        String optString = jSONObject.has("accountName") ? jSONObject.optString("accountName") : null;
        return new GoogleSignInOptions(3, new ArrayList(hashSet), !TextUtils.isEmpty(optString) ? new Account(optString, AccountType.GOOGLE) : null, jSONObject.getBoolean("idTokenRequested"), jSONObject.getBoolean("serverAuthRequested"), jSONObject.getBoolean("forceCodeForRefreshToken"), jSONObject.has("serverClientId") ? jSONObject.optString("serverClientId") : null, jSONObject.has("hostedDomain") ? jSONObject.optString("hostedDomain") : null, new HashMap(), (String) null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Map zam(List list) {
        HashMap hashMap = new HashMap();
        if (list == null) {
            return hashMap;
        }
        Iterator it = list.iterator();
        while (it.hasNext()) {
            GoogleSignInOptionsExtensionParcelable googleSignInOptionsExtensionParcelable = (GoogleSignInOptionsExtensionParcelable) it.next();
            hashMap.put(Integer.valueOf(googleSignInOptionsExtensionParcelable.getType()), googleSignInOptionsExtensionParcelable);
        }
        return hashMap;
    }

    /* JADX WARN: Code restructure failed: missing block: B:18:0x003e, code lost:
        if (r4.getAccount() == null) goto L20;
     */
    /* JADX WARN: Removed duplicated region for block: B:39:0x0090 A[RETURN] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public boolean equals(java.lang.Object r4) {
        /*
            r3 = this;
            r0 = 0
            if (r4 != 0) goto L5
            return r0
        L5:
            com.google.android.gms.auth.api.signin.GoogleSignInOptions r4 = (com.google.android.gms.auth.api.signin.GoogleSignInOptions) r4     // Catch: java.lang.ClassCastException -> L95
            java.util.ArrayList r1 = r3.zao     // Catch: java.lang.ClassCastException -> L95
            int r1 = r1.size()     // Catch: java.lang.ClassCastException -> L95
            if (r1 > 0) goto L94
            java.util.ArrayList r1 = r4.zao     // Catch: java.lang.ClassCastException -> L95
            int r1 = r1.size()     // Catch: java.lang.ClassCastException -> L95
            if (r1 <= 0) goto L19
            goto L94
        L19:
            java.util.ArrayList r1 = r3.zah     // Catch: java.lang.ClassCastException -> L95
            int r1 = r1.size()     // Catch: java.lang.ClassCastException -> L95
            java.util.ArrayList r2 = r4.getScopes()     // Catch: java.lang.ClassCastException -> L95
            int r2 = r2.size()     // Catch: java.lang.ClassCastException -> L95
            if (r1 != r2) goto L93
            java.util.ArrayList r1 = r3.zah     // Catch: java.lang.ClassCastException -> L95
            java.util.ArrayList r2 = r4.getScopes()     // Catch: java.lang.ClassCastException -> L95
            boolean r1 = r1.containsAll(r2)     // Catch: java.lang.ClassCastException -> L95
            if (r1 != 0) goto L36
            goto L93
        L36:
            android.accounts.Account r1 = r3.zai     // Catch: java.lang.ClassCastException -> L95
            if (r1 != 0) goto L41
            android.accounts.Account r1 = r4.getAccount()     // Catch: java.lang.ClassCastException -> L95
            if (r1 != 0) goto L92
        L40:
            goto L4c
        L41:
            android.accounts.Account r2 = r4.getAccount()     // Catch: java.lang.ClassCastException -> L95
            boolean r1 = r1.equals(r2)     // Catch: java.lang.ClassCastException -> L95
            if (r1 == 0) goto L92
            goto L40
        L4c:
            java.lang.String r1 = r3.zam     // Catch: java.lang.ClassCastException -> L95
            boolean r1 = android.text.TextUtils.isEmpty(r1)     // Catch: java.lang.ClassCastException -> L95
            if (r1 == 0) goto L5f
            java.lang.String r1 = r4.getServerClientId()     // Catch: java.lang.ClassCastException -> L95
            boolean r1 = android.text.TextUtils.isEmpty(r1)     // Catch: java.lang.ClassCastException -> L95
            if (r1 == 0) goto L92
            goto L6c
        L5f:
            java.lang.String r1 = r3.zam     // Catch: java.lang.ClassCastException -> L95
            java.lang.String r2 = r4.getServerClientId()     // Catch: java.lang.ClassCastException -> L95
            boolean r1 = r1.equals(r2)     // Catch: java.lang.ClassCastException -> L95
            if (r1 != 0) goto L6c
            goto L92
        L6c:
            boolean r1 = r3.zal     // Catch: java.lang.ClassCastException -> L95
            boolean r2 = r4.isForceCodeForRefreshToken()     // Catch: java.lang.ClassCastException -> L95
            if (r1 != r2) goto L92
            boolean r1 = r3.zaj     // Catch: java.lang.ClassCastException -> L95
            boolean r2 = r4.isIdTokenRequested()     // Catch: java.lang.ClassCastException -> L95
            if (r1 != r2) goto L92
            boolean r1 = r3.zak     // Catch: java.lang.ClassCastException -> L95
            boolean r2 = r4.isServerAuthCodeRequested()     // Catch: java.lang.ClassCastException -> L95
            if (r1 != r2) goto L92
            java.lang.String r1 = r3.zap     // Catch: java.lang.ClassCastException -> L95
            java.lang.String r4 = r4.getLogSessionId()     // Catch: java.lang.ClassCastException -> L95
            boolean r4 = android.text.TextUtils.equals(r1, r4)     // Catch: java.lang.ClassCastException -> L95
            if (r4 == 0) goto L92
            r4 = 1
            return r4
        L92:
            return r0
        L93:
            return r0
        L94:
            return r0
        L95:
            r4 = move-exception
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.gms.auth.api.signin.GoogleSignInOptions.equals(java.lang.Object):boolean");
    }

    public Account getAccount() {
        return this.zai;
    }

    public ArrayList<GoogleSignInOptionsExtensionParcelable> getExtensions() {
        return this.zao;
    }

    public String getLogSessionId() {
        return this.zap;
    }

    public Scope[] getScopeArray() {
        ArrayList arrayList = this.zah;
        return (Scope[]) arrayList.toArray(new Scope[arrayList.size()]);
    }

    public ArrayList<Scope> getScopes() {
        return new ArrayList<>(this.zah);
    }

    public String getServerClientId() {
        return this.zam;
    }

    public int hashCode() {
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = this.zah;
        int size = arrayList2.size();
        for (int i = 0; i < size; i++) {
            arrayList.add(((Scope) arrayList2.get(i)).getScopeUri());
        }
        Collections.sort(arrayList);
        HashAccumulator hashAccumulator = new HashAccumulator();
        hashAccumulator.addObject(arrayList);
        hashAccumulator.addObject(this.zai);
        hashAccumulator.addObject(this.zam);
        hashAccumulator.zaa(this.zal);
        hashAccumulator.zaa(this.zaj);
        hashAccumulator.zaa(this.zak);
        hashAccumulator.addObject(this.zap);
        return hashAccumulator.hash();
    }

    public boolean isForceCodeForRefreshToken() {
        return this.zal;
    }

    public boolean isIdTokenRequested() {
        return this.zaj;
    }

    public boolean isServerAuthCodeRequested() {
        return this.zak;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel out, int flags) {
        int beginObjectHeader = SafeParcelWriter.beginObjectHeader(out);
        SafeParcelWriter.writeInt(out, 1, this.zaf);
        SafeParcelWriter.writeTypedList(out, 2, getScopes(), false);
        SafeParcelWriter.writeParcelable(out, 3, getAccount(), flags, false);
        SafeParcelWriter.writeBoolean(out, 4, isIdTokenRequested());
        SafeParcelWriter.writeBoolean(out, 5, isServerAuthCodeRequested());
        SafeParcelWriter.writeBoolean(out, 6, isForceCodeForRefreshToken());
        SafeParcelWriter.writeString(out, 7, getServerClientId(), false);
        SafeParcelWriter.writeString(out, 8, this.zan, false);
        SafeParcelWriter.writeTypedList(out, 9, getExtensions(), false);
        SafeParcelWriter.writeString(out, 10, getLogSessionId(), false);
        SafeParcelWriter.finishObjectHeader(out, beginObjectHeader);
    }

    public final String zaf() {
        JSONObject jSONObject = new JSONObject();
        try {
            JSONArray jSONArray = new JSONArray();
            Collections.sort(this.zah, zag);
            Iterator it = this.zah.iterator();
            while (it.hasNext()) {
                jSONArray.put(((Scope) it.next()).getScopeUri());
            }
            jSONObject.put("scopes", jSONArray);
            Account account = this.zai;
            if (account != null) {
                jSONObject.put("accountName", account.name);
            }
            jSONObject.put("idTokenRequested", this.zaj);
            jSONObject.put("forceCodeForRefreshToken", this.zal);
            jSONObject.put("serverAuthRequested", this.zak);
            if (!TextUtils.isEmpty(this.zam)) {
                jSONObject.put("serverClientId", this.zam);
            }
            if (!TextUtils.isEmpty(this.zan)) {
                jSONObject.put("hostedDomain", this.zan);
            }
            return jSONObject.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private GoogleSignInOptions(int i, ArrayList arrayList, Account account, boolean z, boolean z2, boolean z3, String str, String str2, Map map, String str3) {
        this.zaf = i;
        this.zah = arrayList;
        this.zai = account;
        this.zaj = z;
        this.zak = z2;
        this.zal = z3;
        this.zam = str;
        this.zan = str2;
        this.zao = new ArrayList(map.values());
        this.zaq = map;
        this.zap = str3;
    }
}
