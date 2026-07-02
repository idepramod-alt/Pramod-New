package com.google.firebase;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.time.Instant;
import java.util.Date;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.comparisons.ComparisonsKt;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.time.DurationKt;
/* compiled from: Timestamp.kt */
@Metadata(d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u000f\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010\u000b\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 #2\b\u0012\u0004\u0012\u00020\u00000\u00012\u00020\u0002:\u0001#B\u0019\b\u0016\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006¢\u0006\u0004\b\u0007\u0010\bB\u0011\b\u0016\u0012\u0006\u0010\t\u001a\u00020\n¢\u0006\u0004\b\u0007\u0010\u000bB\u0011\b\u0017\u0012\u0006\u0010\f\u001a\u00020\r¢\u0006\u0004\b\u0007\u0010\u000eJ\u0006\u0010\u0013\u001a\u00020\nJ\b\u0010\u0014\u001a\u00020\rH\u0007J\u0011\u0010\u0015\u001a\u00020\u00062\u0006\u0010\u0016\u001a\u00020\u0000H\u0096\u0002J\u0013\u0010\u0017\u001a\u00020\u00182\b\u0010\u0016\u001a\u0004\u0018\u00010\u0019H\u0096\u0002J\b\u0010\u001a\u001a\u00020\u0006H\u0016J\b\u0010\u001b\u001a\u00020\u001cH\u0016J\b\u0010\u001d\u001a\u00020\u0006H\u0016J\u0018\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020\u0006H\u0016R\u0011\u0010\u0003\u001a\u00020\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0005\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012¨\u0006$"}, d2 = {"Lcom/google/firebase/Timestamp;", "", "Landroid/os/Parcelable;", "seconds", "", "nanoseconds", "", "<init>", "(JI)V", "date", "Ljava/util/Date;", "(Ljava/util/Date;)V", "time", "Ljava/time/Instant;", "(Ljava/time/Instant;)V", "getSeconds", "()J", "getNanoseconds", "()I", "toDate", "toInstant", "compareTo", "other", "equals", "", "", "hashCode", "toString", "", "describeContents", "writeToParcel", "", "dest", "Landroid/os/Parcel;", "flags", "Companion", "com.google.firebase-firebase-common"}, k = 1, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes.dex */
public final class Timestamp implements Comparable<Timestamp>, Parcelable {
    private final int nanoseconds;
    private final long seconds;
    public static final Companion Companion = new Companion(null);
    public static final Parcelable.Creator<Timestamp> CREATOR = new Parcelable.Creator<Timestamp>() { // from class: com.google.firebase.Timestamp$Companion$CREATOR$1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Timestamp createFromParcel(Parcel source) {
            Intrinsics.checkNotNullParameter(source, "source");
            return new Timestamp(source.readLong(), source.readInt());
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Timestamp[] newArray(int size) {
            return new Timestamp[size];
        }
    };

    public final long getSeconds() {
        return this.seconds;
    }

    public final int getNanoseconds() {
        return this.nanoseconds;
    }

    public Timestamp(long seconds, int nanoseconds) {
        Companion.validateRange(seconds, nanoseconds);
        this.seconds = seconds;
        this.nanoseconds = nanoseconds;
    }

    public Timestamp(Date date) {
        Intrinsics.checkNotNullParameter(date, "date");
        Pair preciseTime = Companion.toPreciseTime(date);
        long seconds = ((Number) preciseTime.component1()).longValue();
        int nanoseconds = ((Number) preciseTime.component2()).intValue();
        Companion.validateRange(seconds, nanoseconds);
        this.seconds = seconds;
        this.nanoseconds = nanoseconds;
    }

    /* JADX WARN: 'this' call moved to the top of the method (can break code semantics) */
    public Timestamp(Instant time) {
        this(time.getEpochSecond(), time.getNano());
        Intrinsics.checkNotNullParameter(time, "time");
    }

    public final Date toDate() {
        return new Date((this.seconds * 1000) + (this.nanoseconds / DurationKt.NANOS_IN_MILLIS));
    }

    public final Instant toInstant() {
        Instant ofEpochSecond = Instant.ofEpochSecond(this.seconds, this.nanoseconds);
        Intrinsics.checkNotNullExpressionValue(ofEpochSecond, "ofEpochSecond(...)");
        return ofEpochSecond;
    }

    @Override // java.lang.Comparable
    public int compareTo(Timestamp other) {
        Intrinsics.checkNotNullParameter(other, "other");
        return ComparisonsKt.compareValuesBy(this, other, Timestamp$compareTo$1.INSTANCE, Timestamp$compareTo$2.INSTANCE);
    }

    public boolean equals(Object other) {
        return other == this || ((other instanceof Timestamp) && compareTo((Timestamp) other) == 0);
    }

    public int hashCode() {
        int initialHash = ((int) this.seconds) * 37;
        int withHighOrderBits = (37 * initialHash) + ((int) (this.seconds >> 32));
        return (37 * withHighOrderBits) + this.nanoseconds;
    }

    public String toString() {
        return "Timestamp(seconds=" + this.seconds + ", nanoseconds=" + this.nanoseconds + ')';
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        Intrinsics.checkNotNullParameter(dest, "dest");
        dest.writeLong(this.seconds);
        dest.writeInt(this.nanoseconds);
    }

    /* compiled from: Timestamp.kt */
    @Metadata(d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\b\u0010\u0007\u001a\u00020\u0006H\u0007J\u0018\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t*\u00020\fH\u0002J\u0018\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\n2\u0006\u0010\u0010\u001a\u00020\u000bH\u0002R\u0016\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u00058\u0006X\u0087\u0004¢\u0006\u0002\n\u0000¨\u0006\u0011"}, d2 = {"Lcom/google/firebase/Timestamp$Companion;", "", "<init>", "()V", "CREATOR", "Landroid/os/Parcelable$Creator;", "Lcom/google/firebase/Timestamp;", "now", "toPreciseTime", "Lkotlin/Pair;", "", "", "Ljava/util/Date;", "validateRange", "", "seconds", "nanoseconds", "com.google.firebase-firebase-common"}, k = 1, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
    /* loaded from: classes.dex */
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        @JvmStatic
        public final Timestamp now() {
            return new Timestamp(new Date());
        }

        /* JADX INFO: Access modifiers changed from: private */
        public final Pair<Long, Integer> toPreciseTime(Date $this$toPreciseTime) {
            long j = 1000;
            long seconds = $this$toPreciseTime.getTime() / j;
            int nanoseconds = (int) (($this$toPreciseTime.getTime() % j) * ((long) DurationKt.NANOS_IN_MILLIS));
            return nanoseconds < 0 ? TuplesKt.to(Long.valueOf(seconds - 1), Integer.valueOf(1000000000 + nanoseconds)) : TuplesKt.to(Long.valueOf(seconds), Integer.valueOf(nanoseconds));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public final void validateRange(long seconds, int nanoseconds) {
            boolean z = true;
            if (!(nanoseconds >= 0 && nanoseconds < 1000000000)) {
                throw new IllegalArgumentException(("Timestamp nanoseconds out of range: " + nanoseconds).toString());
            }
            if (-62135596800L > seconds || seconds >= 253402300800L) {
                z = false;
            }
            if (!z) {
                throw new IllegalArgumentException(("Timestamp seconds out of range: " + seconds).toString());
            }
        }
    }

    @JvmStatic
    public static final Timestamp now() {
        return Companion.now();
    }
}
