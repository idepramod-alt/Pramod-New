package kotlinx.coroutines.scheduling;

import androidx.constraintlayout.widget.ConstraintLayout;
import kotlin.Metadata;
import kotlinx.coroutines.DebugStringsKt;
/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: Tasks.kt */
@Metadata(d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0002\u0018\u00002\u00020\u0001B'\u0012\n\u0010\u0002\u001a\u00060\u0004j\u0002`\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\n\u0010\u0007\u001a\u00060\tj\u0002`\b¢\u0006\u0004\b\n\u0010\u000bJ\b\u0010\r\u001a\u00020\u000eH\u0016J\b\u0010\u000f\u001a\u00020\u0010H\u0016R\u0016\u0010\u0002\u001a\u00060\u0004j\u0002`\u00038\u0006X\u0087\u0004¢\u0006\u0004\n\u0002\u0010\f¨\u0006\u0011"}, d2 = {"Lkotlinx/coroutines/scheduling/TaskImpl;", "Lkotlinx/coroutines/scheduling/Task;", "block", "Lkotlinx/coroutines/Runnable;", "Ljava/lang/Runnable;", "submissionTime", "", "taskContext", "Lkotlinx/coroutines/scheduling/TaskContext;", "", "<init>", "(Ljava/lang/Runnable;JZ)V", "Ljava/lang/Runnable;", "run", "", "toString", "", "kotlinx-coroutines-core"}, k = 1, mv = {2, 0, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
/* loaded from: classes4.dex */
public final class TaskImpl extends Task {
    public final Runnable block;

    public TaskImpl(Runnable block, long submissionTime, boolean taskContext) {
        super(submissionTime, taskContext);
        this.block = block;
    }

    @Override // java.lang.Runnable
    public void run() {
        this.block.run();
    }

    public String toString() {
        String taskContextString;
        StringBuilder append = new StringBuilder().append("Task[").append(DebugStringsKt.getClassSimpleName(this.block)).append('@').append(DebugStringsKt.getHexAddress(this.block)).append(", ").append(this.submissionTime).append(", ");
        taskContextString = TasksKt.taskContextString(this.taskContext);
        return append.append(taskContextString).append(']').toString();
    }
}
