package kotlin.io.path;

import androidx.constraintlayout.widget.ConstraintLayout;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import kotlin.ExceptionsKt;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.io.CloseableKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.internal.Intrinsics;
/* JADX INFO: Access modifiers changed from: package-private */
/* compiled from: PathRecursiveFunctions.kt */
@Metadata(d1 = {"\u0000v\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a$\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00010\u0005H\u0082\b¢\u0006\u0002\b\u0006\u001a\u001d\u0010\u0007\u001a\u00020\u00012\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u0002\u001a\u00020\u0003H\u0002¢\u0006\u0002\b\n\u001a'\u0010\u000b\u001a\u00020\u00012\u0006\u0010\f\u001a\u00020\t2\b\u0010\r\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0002\u001a\u00020\u0003H\u0002¢\u0006\u0002\b\u000e\u001a&\u0010\u000f\u001a\u0004\u0018\u0001H\u0010\"\u0004\b\u0000\u0010\u00102\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u0002H\u00100\u0005H\u0082\b¢\u0006\u0004\b\u0011\u0010\u0012\u001a\f\u0010\u0013\u001a\u00020\u0001*\u00020\tH\u0000\u001a\u0019\u0010\u0014\u001a\u00020\u0001*\u00020\t2\u0006\u0010\r\u001a\u00020\tH\u0002¢\u0006\u0002\b\u0015\u001aw\u0010\u0016\u001a\u00020\t*\u00020\t2\u0006\u0010\u0017\u001a\u00020\t2Q\b\u0002\u0010\u0018\u001aK\u0012\u0013\u0012\u00110\t¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u001c\u0012\u0013\u0012\u00110\t¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u0017\u0012\u0017\u0012\u00150\u001dj\u0002`\u001e¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u001f\u0012\u0004\u0012\u00020 0\u00192\u0006\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020\"H\u0007\u001a´\u0001\u0010\u0016\u001a\u00020\t*\u00020\t2\u0006\u0010\u0017\u001a\u00020\t2Q\b\u0002\u0010\u0018\u001aK\u0012\u0013\u0012\u00110\t¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u001c\u0012\u0013\u0012\u00110\t¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u0017\u0012\u0017\u0012\u00150\u001dj\u0002`\u001e¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u001f\u0012\u0004\u0012\u00020 0\u00192\u0006\u0010!\u001a\u00020\"2C\b\u0002\u0010$\u001a=\u0012\u0004\u0012\u00020%\u0012\u0013\u0012\u00110\t¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u001c\u0012\u0013\u0012\u00110\t¢\u0006\f\b\u001a\u0012\b\b\u001b\u0012\u0004\b\b(\u0017\u0012\u0004\u0012\u00020&0\u0019¢\u0006\u0002\b'H\u0007\u001a\f\u0010(\u001a\u00020\u0001*\u00020\tH\u0007\u001a\u001b\u0010)\u001a\f\u0012\b\u0012\u00060\u001dj\u0002`\u001e0**\u00020\tH\u0002¢\u0006\u0002\b+\u001a'\u0010,\u001a\u00020\u0001*\b\u0012\u0004\u0012\u00020\t0-2\u0006\u0010\u001b\u001a\u00020\t2\u0006\u0010\u0002\u001a\u00020\u0003H\u0002¢\u0006\u0002\b.\u001a1\u0010/\u001a\u00020\u0001*\b\u0012\u0004\u0012\u00020\t0-2\u0006\u0010\u001b\u001a\u00020\t2\b\u0010\r\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0002\u001a\u00020\u0003H\u0002¢\u0006\u0002\b0\u001a5\u00101\u001a\u00020\"*\b\u0012\u0004\u0012\u00020\t0-2\u0006\u00102\u001a\u00020\t2\u0012\u00103\u001a\n\u0012\u0006\b\u0001\u0012\u00020504\"\u000205H\u0002¢\u0006\u0004\b6\u00107\u001a\u0011\u00108\u001a\u000209*\u00020&H\u0003¢\u0006\u0002\b:\u001a\u0011\u00108\u001a\u000209*\u00020 H\u0003¢\u0006\u0002\b:¨\u0006;"}, d2 = {"collectIfThrows", "", "collector", "Lkotlin/io/path/ExceptionsCollector;", "function", "Lkotlin/Function0;", "collectIfThrows$PathsKt__PathRecursiveFunctionsKt", "insecureEnterDirectory", "path", "Ljava/nio/file/Path;", "insecureEnterDirectory$PathsKt__PathRecursiveFunctionsKt", "insecureHandleEntry", "entry", "parent", "insecureHandleEntry$PathsKt__PathRecursiveFunctionsKt", "tryIgnoreNoSuchFileException", "R", "tryIgnoreNoSuchFileException$PathsKt__PathRecursiveFunctionsKt", "(Lkotlin/jvm/functions/Function0;)Ljava/lang/Object;", "checkFileName", "checkNotSameAs", "checkNotSameAs$PathsKt__PathRecursiveFunctionsKt", "copyToRecursively", "target", "onError", "Lkotlin/Function3;", "Lkotlin/ParameterName;", "name", "source", "Ljava/lang/Exception;", "Lkotlin/Exception;", "exception", "Lkotlin/io/path/OnErrorResult;", "followLinks", "", "overwrite", "copyAction", "Lkotlin/io/path/CopyActionContext;", "Lkotlin/io/path/CopyActionResult;", "Lkotlin/ExtensionFunctionType;", "deleteRecursively", "deleteRecursivelyImpl", "", "deleteRecursivelyImpl$PathsKt__PathRecursiveFunctionsKt", "enterDirectory", "Ljava/nio/file/SecureDirectoryStream;", "enterDirectory$PathsKt__PathRecursiveFunctionsKt", "handleEntry", "handleEntry$PathsKt__PathRecursiveFunctionsKt", "isDirectory", "entryName", "options", "", "Ljava/nio/file/LinkOption;", "isDirectory$PathsKt__PathRecursiveFunctionsKt", "(Ljava/nio/file/SecureDirectoryStream;Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z", "toFileVisitResult", "Ljava/nio/file/FileVisitResult;", "toFileVisitResult$PathsKt__PathRecursiveFunctionsKt", "kotlin-stdlib-jdk7"}, k = 5, mv = {1, 9, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_EDITOR_ABSOLUTEX, xs = "kotlin/io/path/PathsKt")
/* loaded from: classes4.dex */
public class PathsKt__PathRecursiveFunctionsKt extends PathsKt__PathReadWriteKt {

    /* compiled from: PathRecursiveFunctions.kt */
    @Metadata(k = 3, mv = {1, 9, 0}, xi = ConstraintLayout.LayoutParams.Table.LAYOUT_CONSTRAINT_VERTICAL_CHAINSTYLE)
    /* loaded from: classes4.dex */
    public /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;
        public static final /* synthetic */ int[] $EnumSwitchMapping$1;

        static {
            int[] iArr = new int[CopyActionResult.values().length];
            try {
                iArr[CopyActionResult.CONTINUE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[CopyActionResult.TERMINATE.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[CopyActionResult.SKIP_SUBTREE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            $EnumSwitchMapping$0 = iArr;
            int[] iArr2 = new int[OnErrorResult.values().length];
            try {
                iArr2[OnErrorResult.TERMINATE.ordinal()] = 1;
            } catch (NoSuchFieldError e4) {
            }
            try {
                iArr2[OnErrorResult.SKIP_SUBTREE.ordinal()] = 2;
            } catch (NoSuchFieldError e5) {
            }
            $EnumSwitchMapping$1 = iArr2;
        }
    }

    public static /* synthetic */ Path copyToRecursively$default(Path path, Path path2, Function3 function3, boolean z, boolean z2, int i, Object obj) {
        if ((i & 2) != 0) {
            function3 = PathsKt__PathRecursiveFunctionsKt$copyToRecursively$1.INSTANCE;
        }
        return PathsKt.copyToRecursively(path, path2, function3, z, z2);
    }

    public static final Path copyToRecursively(Path $this$copyToRecursively, Path target, Function3<? super Path, ? super Path, ? super Exception, ? extends OnErrorResult> onError, boolean followLinks, boolean overwrite) {
        Intrinsics.checkNotNullParameter($this$copyToRecursively, "<this>");
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(onError, "onError");
        if (overwrite) {
            return PathsKt.copyToRecursively($this$copyToRecursively, target, onError, followLinks, new PathsKt__PathRecursiveFunctionsKt$copyToRecursively$2(followLinks));
        }
        return PathsKt.copyToRecursively$default($this$copyToRecursively, target, onError, followLinks, (Function3) null, 8, (Object) null);
    }

    public static /* synthetic */ Path copyToRecursively$default(Path path, Path path2, Function3 function3, boolean z, Function3 function32, int i, Object obj) {
        if ((i & 2) != 0) {
            function3 = PathsKt__PathRecursiveFunctionsKt$copyToRecursively$3.INSTANCE;
        }
        if ((i & 8) != 0) {
            function32 = new PathsKt__PathRecursiveFunctionsKt$copyToRecursively$4(z);
        }
        return PathsKt.copyToRecursively(path, path2, function3, z, function32);
    }

    public static final Path copyToRecursively(Path $this$copyToRecursively, Path target, Function3<? super Path, ? super Path, ? super Exception, ? extends OnErrorResult> onError, boolean followLinks, Function3<? super CopyActionContext, ? super Path, ? super Path, ? extends CopyActionResult> copyAction) {
        Intrinsics.checkNotNullParameter($this$copyToRecursively, "<this>");
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(onError, "onError");
        Intrinsics.checkNotNullParameter(copyAction, "copyAction");
        LinkOption[] linkOptions = LinkFollowing.INSTANCE.toLinkOptions(followLinks);
        LinkOption[] linkOptionArr = (LinkOption[]) Arrays.copyOf(linkOptions, linkOptions.length);
        if (!Files.exists($this$copyToRecursively, (LinkOption[]) Arrays.copyOf(linkOptionArr, linkOptionArr.length))) {
            throw new NoSuchFileException($this$copyToRecursively.toString(), target.toString(), "The source file doesn't exist.");
        }
        boolean isSubdirectory = false;
        if (Files.exists($this$copyToRecursively, (LinkOption[]) Arrays.copyOf(new LinkOption[0], 0)) && (followLinks || !Files.isSymbolicLink($this$copyToRecursively))) {
            boolean targetExistsAndNotSymlink = Files.exists(target, (LinkOption[]) Arrays.copyOf(new LinkOption[0], 0)) && !Files.isSymbolicLink(target);
            if (!targetExistsAndNotSymlink || !Files.isSameFile($this$copyToRecursively, target)) {
                if (Intrinsics.areEqual($this$copyToRecursively.getFileSystem(), target.getFileSystem())) {
                    if (targetExistsAndNotSymlink) {
                        isSubdirectory = target.toRealPath(new LinkOption[0]).startsWith($this$copyToRecursively.toRealPath(new LinkOption[0]));
                    } else {
                        Path it = target.getParent();
                        if (it != null && Files.exists(it, (LinkOption[]) Arrays.copyOf(new LinkOption[0], 0)) && it.toRealPath(new LinkOption[0]).startsWith($this$copyToRecursively.toRealPath(new LinkOption[0]))) {
                            isSubdirectory = true;
                        }
                    }
                }
                if (isSubdirectory) {
                    throw new FileSystemException($this$copyToRecursively.toString(), target.toString(), "Recursively copying a directory into its subdirectory is prohibited.");
                }
            }
        }
        Path normalizedTarget = target.normalize();
        ArrayList stack = new ArrayList();
        PathsKt.visitFileTree$default($this$copyToRecursively, 0, followLinks, new PathsKt__PathRecursiveFunctionsKt$copyToRecursively$5(stack, copyAction, $this$copyToRecursively, target, normalizedTarget, onError), 1, (Object) null);
        return target;
    }

    private static final Path copyToRecursively$destination$PathsKt__PathRecursiveFunctionsKt(Path $this_copyToRecursively, Path $target, Path normalizedTarget, Path source) {
        Path relativePath = PathsKt.relativeTo(source, $this_copyToRecursively);
        Path destination = $target.resolve(relativePath.toString());
        if (!destination.normalize().startsWith(normalizedTarget)) {
            throw new IllegalFileNameException(source, destination, "Copying files to outside the specified target directory is prohibited. The directory being recursively copied might contain an entry with an illegal name.");
        }
        Intrinsics.checkNotNull(destination);
        return destination;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final FileVisitResult copyToRecursively$error$PathsKt__PathRecursiveFunctionsKt(Function3<? super Path, ? super Path, ? super Exception, ? extends OnErrorResult> function3, Path $this_copyToRecursively, Path $target, Path normalizedTarget, Path source, Exception exception) {
        return toFileVisitResult$PathsKt__PathRecursiveFunctionsKt(function3.invoke(source, copyToRecursively$destination$PathsKt__PathRecursiveFunctionsKt($this_copyToRecursively, $target, normalizedTarget, source), exception));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final FileVisitResult copyToRecursively$copy$PathsKt__PathRecursiveFunctionsKt(ArrayList<Path> arrayList, Function3<? super CopyActionContext, ? super Path, ? super Path, ? extends CopyActionResult> function3, Path $this_copyToRecursively, Path $target, Path normalizedTarget, Function3<? super Path, ? super Path, ? super Exception, ? extends OnErrorResult> function32, Path source, BasicFileAttributes attributes) {
        try {
            if (!arrayList.isEmpty()) {
                PathsKt.checkFileName(source);
                checkNotSameAs$PathsKt__PathRecursiveFunctionsKt(source, (Path) CollectionsKt.last((List<? extends Object>) arrayList));
            }
            return toFileVisitResult$PathsKt__PathRecursiveFunctionsKt(function3.invoke(DefaultCopyActionContext.INSTANCE, source, copyToRecursively$destination$PathsKt__PathRecursiveFunctionsKt($this_copyToRecursively, $target, normalizedTarget, source)));
        } catch (Exception exception) {
            return copyToRecursively$error$PathsKt__PathRecursiveFunctionsKt(function32, $this_copyToRecursively, $target, normalizedTarget, source, exception);
        }
    }

    private static final FileVisitResult toFileVisitResult$PathsKt__PathRecursiveFunctionsKt(CopyActionResult $this$toFileVisitResult) {
        switch (WhenMappings.$EnumSwitchMapping$0[$this$toFileVisitResult.ordinal()]) {
            case 1:
                return FileVisitResult.CONTINUE;
            case 2:
                return FileVisitResult.TERMINATE;
            case 3:
                return FileVisitResult.SKIP_SUBTREE;
            default:
                throw new NoWhenBranchMatchedException();
        }
    }

    private static final FileVisitResult toFileVisitResult$PathsKt__PathRecursiveFunctionsKt(OnErrorResult $this$toFileVisitResult) {
        switch (WhenMappings.$EnumSwitchMapping$1[$this$toFileVisitResult.ordinal()]) {
            case 1:
                return FileVisitResult.TERMINATE;
            case 2:
                return FileVisitResult.SKIP_SUBTREE;
            default:
                throw new NoWhenBranchMatchedException();
        }
    }

    public static final void deleteRecursively(Path $this$deleteRecursively) {
        Intrinsics.checkNotNullParameter($this$deleteRecursively, "<this>");
        Iterable suppressedExceptions = deleteRecursivelyImpl$PathsKt__PathRecursiveFunctionsKt($this$deleteRecursively);
        if (!((Collection) suppressedExceptions).isEmpty()) {
            FileSystemException $this$deleteRecursively_u24lambda_u242 = new FileSystemException("Failed to delete one or more files. See suppressed exceptions for details.");
            Iterable $this$forEach$iv = suppressedExceptions;
            for (Object element$iv : $this$forEach$iv) {
                Exception it = (Exception) element$iv;
                ExceptionsKt.addSuppressed($this$deleteRecursively_u24lambda_u242, it);
            }
            throw $this$deleteRecursively_u24lambda_u242;
        }
    }

    private static final List<Exception> deleteRecursivelyImpl$PathsKt__PathRecursiveFunctionsKt(Path $this$deleteRecursivelyImpl) {
        DirectoryStream directoryStream;
        ExceptionsCollector collector = new ExceptionsCollector(0, 1, null);
        boolean useInsecure = true;
        Path parent = $this$deleteRecursivelyImpl.getParent();
        if (parent != null) {
            try {
                directoryStream = Files.newDirectoryStream(parent);
            } catch (Throwable th) {
                directoryStream = null;
            }
            if (directoryStream != null) {
                DirectoryStream directoryStream2 = directoryStream;
                try {
                    DirectoryStream stream = directoryStream2;
                    if (stream instanceof SecureDirectoryStream) {
                        useInsecure = false;
                        collector.setPath(parent);
                        Path fileName = $this$deleteRecursivelyImpl.getFileName();
                        Intrinsics.checkNotNullExpressionValue(fileName, "getFileName(...)");
                        handleEntry$PathsKt__PathRecursiveFunctionsKt((SecureDirectoryStream) stream, fileName, null, collector);
                    }
                    Unit unit = Unit.INSTANCE;
                    CloseableKt.closeFinally(directoryStream2, null);
                } finally {
                }
            }
        }
        if (useInsecure) {
            insecureHandleEntry$PathsKt__PathRecursiveFunctionsKt($this$deleteRecursivelyImpl, null, collector);
        }
        return collector.getCollectedExceptions();
    }

    private static final void collectIfThrows$PathsKt__PathRecursiveFunctionsKt(ExceptionsCollector collector, Function0<Unit> function0) {
        try {
            function0.invoke();
        } catch (Exception exception) {
            collector.collect(exception);
        }
    }

    private static final <R> R tryIgnoreNoSuchFileException$PathsKt__PathRecursiveFunctionsKt(Function0<? extends R> function0) {
        try {
            return function0.invoke();
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    private static final void handleEntry$PathsKt__PathRecursiveFunctionsKt(SecureDirectoryStream<Path> secureDirectoryStream, Path name, Path parent, ExceptionsCollector collector) {
        collector.enterEntry(name);
        if (parent != null) {
            try {
                Path entry = collector.getPath();
                Intrinsics.checkNotNull(entry);
                PathsKt.checkFileName(entry);
                checkNotSameAs$PathsKt__PathRecursiveFunctionsKt(entry, parent);
            } catch (Exception exception$iv) {
                collector.collect(exception$iv);
            }
        }
        if (isDirectory$PathsKt__PathRecursiveFunctionsKt(secureDirectoryStream, name, LinkOption.NOFOLLOW_LINKS)) {
            int preEnterTotalExceptions = collector.getTotalExceptions();
            enterDirectory$PathsKt__PathRecursiveFunctionsKt(secureDirectoryStream, name, collector);
            if (preEnterTotalExceptions == collector.getTotalExceptions()) {
                try {
                    secureDirectoryStream.deleteDirectory(name);
                    Unit unit = Unit.INSTANCE;
                } catch (NoSuchFileException e) {
                }
            }
        } else {
            try {
                secureDirectoryStream.deleteFile(name);
                Unit unit2 = Unit.INSTANCE;
            } catch (NoSuchFileException e2) {
            }
        }
        collector.exitEntry(name);
    }

    private static final void enterDirectory$PathsKt__PathRecursiveFunctionsKt(SecureDirectoryStream<Path> secureDirectoryStream, Path name, ExceptionsCollector collector) {
        SecureDirectoryStream secureDirectoryStream2;
        try {
            try {
                secureDirectoryStream2 = secureDirectoryStream.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
            } catch (Exception exception$iv) {
                collector.collect(exception$iv);
                return;
            }
        } catch (NoSuchFileException e) {
            secureDirectoryStream2 = null;
        }
        if (secureDirectoryStream2 != null) {
            SecureDirectoryStream secureDirectoryStream3 = secureDirectoryStream2;
            SecureDirectoryStream directoryStream = secureDirectoryStream3;
            for (Path entry : directoryStream) {
                Path fileName = entry.getFileName();
                Intrinsics.checkNotNullExpressionValue(fileName, "getFileName(...)");
                handleEntry$PathsKt__PathRecursiveFunctionsKt(directoryStream, fileName, collector.getPath(), collector);
            }
            Unit unit = Unit.INSTANCE;
            CloseableKt.closeFinally(secureDirectoryStream3, null);
        }
    }

    private static final boolean isDirectory$PathsKt__PathRecursiveFunctionsKt(SecureDirectoryStream<Path> secureDirectoryStream, Path entryName, LinkOption... options) {
        Boolean bool;
        try {
            bool = Boolean.valueOf(((BasicFileAttributeView) secureDirectoryStream.getFileAttributeView(entryName, BasicFileAttributeView.class, (LinkOption[]) Arrays.copyOf(options, options.length))).readAttributes().isDirectory());
        } catch (NoSuchFileException e) {
            bool = null;
        }
        if (bool != null) {
            return bool.booleanValue();
        }
        return false;
    }

    private static final void insecureHandleEntry$PathsKt__PathRecursiveFunctionsKt(Path entry, Path parent, ExceptionsCollector collector) {
        if (parent != null) {
            try {
                PathsKt.checkFileName(entry);
                checkNotSameAs$PathsKt__PathRecursiveFunctionsKt(entry, parent);
            } catch (Exception exception$iv) {
                collector.collect(exception$iv);
                return;
            }
        }
        if (Files.isDirectory(entry, (LinkOption[]) Arrays.copyOf(new LinkOption[]{LinkOption.NOFOLLOW_LINKS}, 1))) {
            int preEnterTotalExceptions = collector.getTotalExceptions();
            insecureEnterDirectory$PathsKt__PathRecursiveFunctionsKt(entry, collector);
            if (preEnterTotalExceptions == collector.getTotalExceptions()) {
                Files.deleteIfExists(entry);
                return;
            }
            return;
        }
        Files.deleteIfExists(entry);
    }

    private static final void insecureEnterDirectory$PathsKt__PathRecursiveFunctionsKt(Path path, ExceptionsCollector collector) {
        DirectoryStream directoryStream;
        try {
            try {
                directoryStream = Files.newDirectoryStream(path);
            } catch (Exception exception$iv) {
                collector.collect(exception$iv);
                return;
            }
        } catch (NoSuchFileException e) {
            directoryStream = null;
        }
        if (directoryStream != null) {
            DirectoryStream directoryStream2 = directoryStream;
            DirectoryStream directoryStream3 = directoryStream2;
            for (Path entry : directoryStream3) {
                Intrinsics.checkNotNull(entry);
                insecureHandleEntry$PathsKt__PathRecursiveFunctionsKt(entry, path, collector);
            }
            Unit unit = Unit.INSTANCE;
            CloseableKt.closeFinally(directoryStream2, null);
        }
    }

    public static final void checkFileName(Path $this$checkFileName) {
        Intrinsics.checkNotNullParameter($this$checkFileName, "<this>");
        String fileName = PathsKt.getName($this$checkFileName);
        switch (fileName.hashCode()) {
            case 46:
                if (!fileName.equals(".")) {
                    return;
                }
                break;
            case 1472:
                if (!fileName.equals("..")) {
                    return;
                }
                break;
            case 1473:
                if (!fileName.equals("./")) {
                    return;
                }
                break;
            case 1518:
                if (!fileName.equals(".\\")) {
                    return;
                }
                break;
            case 45679:
                if (!fileName.equals("../")) {
                    return;
                }
                break;
            case 45724:
                if (!fileName.equals("..\\")) {
                    return;
                }
                break;
            default:
                return;
        }
        throw new IllegalFileNameException($this$checkFileName);
    }

    private static final void checkNotSameAs$PathsKt__PathRecursiveFunctionsKt(Path $this$checkNotSameAs, Path parent) {
        if (!Files.isSymbolicLink($this$checkNotSameAs) && Files.isSameFile($this$checkNotSameAs, parent)) {
            throw new FileSystemLoopException($this$checkNotSameAs.toString());
        }
    }
}
