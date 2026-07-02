package kotlinx.coroutines;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.CoroutineStackFrame;
import kotlinx.coroutines.internal.StackTraceRecoveryKt;
/* compiled from: CompletionState.kt */
@Metadata(d1 = {"\u0000 \n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a\u001f\u0010\u0000\u001a\u0004\u0018\u00010\u0001\"\u0004\b\u0000\u0010\u0002*\b\u0012\u0004\u0012\u0002H\u00020\u0003H\u0000¢\u0006\u0002\u0010\u0004\u001a+\u0010\u0000\u001a\u0004\u0018\u00010\u0001\"\u0004\b\u0000\u0010\u0002*\b\u0012\u0004\u0012\u0002H\u00020\u00032\n\u0010\u0005\u001a\u0006\u0012\u0002\b\u00030\u0006H\u0000¢\u0006\u0002\u0010\u0007\u001a1\u0010\b\u001a\b\u0012\u0004\u0012\u0002H\u00020\u0003\"\u0004\b\u0000\u0010\u00022\b\u0010\t\u001a\u0004\u0018\u00010\u00012\f\u0010\n\u001a\b\u0012\u0004\u0012\u0002H\u00020\u000bH\u0000¢\u0006\u0002\u0010\f¨\u0006\r"}, d2 = {"toState", "", "T", "Lkotlin/Result;", "(Ljava/lang/Object;)Ljava/lang/Object;", "caller", "Lkotlinx/coroutines/CancellableContinuation;", "(Ljava/lang/Object;Lkotlinx/coroutines/CancellableContinuation;)Ljava/lang/Object;", "recoverResult", "state", "uCont", "Lkotlin/coroutines/Continuation;", "(Ljava/lang/Object;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "kotlinx-coroutines-core"}, k = 2, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public final class CompletionStateKt {
    public static final <T> Object toState(Object $this$toState) {
        Throwable it = Result.m230exceptionOrNullimpl($this$toState);
        return it == null ? $this$toState : new CompletedExceptionally(it, false, 2, null);
    }

    public static final <T> Object toState(Object $this$toState, CancellableContinuation<?> cancellableContinuation) {
        Throwable th;
        Throwable it = Result.m230exceptionOrNullimpl($this$toState);
        if (it == null) {
            return $this$toState;
        }
        if (DebugKt.getRECOVER_STACK_TRACES() && (cancellableContinuation instanceof CoroutineStackFrame)) {
            th = StackTraceRecoveryKt.recoverFromStackFrame(it, (CoroutineStackFrame) cancellableContinuation);
        } else {
            th = it;
        }
        return new CompletedExceptionally(th, false, 2, null);
    }

    public static final <T> Object recoverResult(Object state, Continuation<? super T> continuation) {
        if (state instanceof CompletedExceptionally) {
            Result.Companion companion = Result.Companion;
            Throwable exception$iv = ((CompletedExceptionally) state).cause;
            if (DebugKt.getRECOVER_STACK_TRACES() && (continuation instanceof CoroutineStackFrame)) {
                exception$iv = StackTraceRecoveryKt.recoverFromStackFrame(exception$iv, (CoroutineStackFrame) continuation);
            }
            return Result.m227constructorimpl(ResultKt.createFailure(exception$iv));
        }
        Result.Companion companion2 = Result.Companion;
        return Result.m227constructorimpl(state);
    }
}
