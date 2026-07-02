package com.google.firebase.database;

import kotlin.Metadata;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.jvm.internal.InlineMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowCollector;
/* compiled from: SafeCollector.common.kt */
@Metadata(d1 = {"\u0000\u0019\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004*\u0001\u0000\b\n\u0018\u00002\b\u0012\u0004\u0012\u00028\u00000\u0001J\u001c\u0010\u0002\u001a\u00020\u00032\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00028\u00000\u0005H\u0096@¢\u0006\u0002\u0010\u0006¨\u0006\u0007¸\u0006\t"}, d2 = {"kotlinx/coroutines/flow/internal/SafeCollector_commonKt$unsafeFlow$1", "Lkotlinx/coroutines/flow/Flow;", "collect", "", "collector", "Lkotlinx/coroutines/flow/FlowCollector;", "(Lkotlinx/coroutines/flow/FlowCollector;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "kotlinx-coroutines-core", "kotlinx/coroutines/flow/FlowKt__EmittersKt$unsafeTransform$$inlined$unsafeFlow$1", "kotlinx/coroutines/flow/FlowKt__TransformKt$map$$inlined$unsafeTransform$1"}, k = 1, mv = {2, 0, 0}, xi = 176)
/* loaded from: classes.dex */
public final class DatabaseKt$values$$inlined$map$1 implements Flow<T> {
    final /* synthetic */ Flow $this_unsafeTransform$inlined;

    /* compiled from: Emitters.kt */
    @Metadata(k = 3, mv = {2, 0, 0}, xi = 176)
    /* renamed from: com.google.firebase.database.DatabaseKt$values$$inlined$map$1$2  reason: invalid class name */
    /* loaded from: classes.dex */
    public static final class AnonymousClass2<T> implements FlowCollector {
        final /* synthetic */ FlowCollector $this_unsafeFlow;

        @Metadata(k = 3, mv = {2, 0, 0}, xi = 176)
        @DebugMetadata(c = "com.google.firebase.database.DatabaseKt$values$$inlined$map$1$2", f = "Database.kt", i = {}, l = {50}, m = "emit", n = {}, s = {})
        /* renamed from: com.google.firebase.database.DatabaseKt$values$$inlined$map$1$2$1  reason: invalid class name */
        /* loaded from: classes.dex */
        public static final class AnonymousClass1 extends ContinuationImpl {
            Object L$0;
            int label;
            /* synthetic */ Object result;

            public AnonymousClass1(Continuation continuation) {
                super(continuation);
            }

            @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
            public final Object invokeSuspend(Object obj) {
                this.result = obj;
                this.label |= Integer.MIN_VALUE;
                return AnonymousClass2.this.emit(null, this);
            }
        }

        public AnonymousClass2(FlowCollector flowCollector) {
            this.$this_unsafeFlow = flowCollector;
        }

        /* JADX WARN: Multi-variable type inference failed */
        /* JADX WARN: Removed duplicated region for block: B:10:0x0024  */
        /* JADX WARN: Removed duplicated region for block: B:12:0x002c  */
        /* JADX WARN: Removed duplicated region for block: B:13:0x0031  */
        @Override // kotlinx.coroutines.flow.FlowCollector
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public final java.lang.Object emit(java.lang.Object r9, kotlin.coroutines.Continuation r10) {
            /*
                r8 = this;
                boolean r0 = r10 instanceof com.google.firebase.database.DatabaseKt$values$$inlined$map$1.AnonymousClass2.AnonymousClass1
                if (r0 == 0) goto L14
                r0 = r10
                com.google.firebase.database.DatabaseKt$values$$inlined$map$1$2$1 r0 = (com.google.firebase.database.DatabaseKt$values$$inlined$map$1.AnonymousClass2.AnonymousClass1) r0
                int r1 = r0.label
                r2 = -2147483648(0xffffffff80000000, float:-0.0)
                r1 = r1 & r2
                if (r1 == 0) goto L14
                int r1 = r0.label
                int r1 = r1 - r2
                r0.label = r1
                goto L19
            L14:
                com.google.firebase.database.DatabaseKt$values$$inlined$map$1$2$1 r0 = new com.google.firebase.database.DatabaseKt$values$$inlined$map$1$2$1
                r0.<init>(r10)
            L19:
                java.lang.Object r1 = r0.result
                java.lang.Object r2 = kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()
                int r3 = r0.label
                switch(r3) {
                    case 0: goto L31;
                    case 1: goto L2c;
                    default: goto L24;
                }
            L24:
                java.lang.IllegalStateException r9 = new java.lang.IllegalStateException
                java.lang.String r0 = "call to 'resume' before 'invoke' with coroutine"
                r9.<init>(r0)
                throw r9
            L2c:
                r9 = 0
                kotlin.ResultKt.throwOnFailure(r1)
                goto L58
            L31:
                kotlin.ResultKt.throwOnFailure(r1)
                r3 = r8
                kotlinx.coroutines.flow.FlowCollector r3 = r3.$this_unsafeFlow
                r4 = 0
                r5 = r0
                kotlin.coroutines.Continuation r5 = (kotlin.coroutines.Continuation) r5
                com.google.firebase.database.DataSnapshot r9 = (com.google.firebase.database.DataSnapshot) r9
                r5 = 0
                r6 = 4
                java.lang.String r7 = "T"
                kotlin.jvm.internal.Intrinsics.reifiedOperationMarker(r6, r7)
                java.lang.Class<java.lang.Object> r6 = java.lang.Object.class
                r7 = r6
                java.lang.Class r7 = (java.lang.Class) r7
                java.lang.Object r9 = r9.getValue(r6)
                r5 = 1
                r0.label = r5
                java.lang.Object r9 = r3.emit(r9, r0)
                if (r9 != r2) goto L57
                return r2
            L57:
                r9 = r4
            L58:
                kotlin.Unit r9 = kotlin.Unit.INSTANCE
                return r9
            */
            throw new UnsupportedOperationException("Method not decompiled: com.google.firebase.database.DatabaseKt$values$$inlined$map$1.AnonymousClass2.emit(java.lang.Object, kotlin.coroutines.Continuation):java.lang.Object");
        }

        /* JADX WARN: Multi-variable type inference failed */
        public final Object emit$$forInline(Object value, Continuation $completion) {
            InlineMarker.mark(4);
            new AnonymousClass1($completion);
            InlineMarker.mark(5);
            FlowCollector $this$map_u24lambda_u245 = this.$this_unsafeFlow;
            DataSnapshot it = (DataSnapshot) value;
            Intrinsics.reifiedOperationMarker(4, "T");
            Object value2 = it.getValue(Object.class);
            InlineMarker.mark(0);
            $this$map_u24lambda_u245.emit(value2, $completion);
            InlineMarker.mark(1);
            return Unit.INSTANCE;
        }
    }

    public DatabaseKt$values$$inlined$map$1(Flow flow) {
        this.$this_unsafeTransform$inlined = flow;
    }

    @Override // kotlinx.coroutines.flow.Flow
    public Object collect(FlowCollector collector, Continuation $completion) {
        Flow flow = this.$this_unsafeTransform$inlined;
        Intrinsics.needClassReification();
        Object collect = flow.collect(new AnonymousClass2(collector), $completion);
        return collect == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? collect : Unit.INSTANCE;
    }

    public Object collect$$forInline(FlowCollector collector, Continuation $completion) {
        InlineMarker.mark(4);
        new ContinuationImpl($completion) { // from class: com.google.firebase.database.DatabaseKt$values$$inlined$map$1.1
            int label;
            /* synthetic */ Object result;

            @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
            public final Object invokeSuspend(Object obj) {
                this.result = obj;
                this.label |= Integer.MIN_VALUE;
                return DatabaseKt$values$$inlined$map$1.this.collect(null, this);
            }
        };
        InlineMarker.mark(5);
        Flow flow = this.$this_unsafeTransform$inlined;
        Intrinsics.needClassReification();
        InlineMarker.mark(0);
        flow.collect(new AnonymousClass2(collector), $completion);
        InlineMarker.mark(1);
        return Unit.INSTANCE;
    }
}
