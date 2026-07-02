package com.google.firebase.database.core.utilities;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.StandardComparator;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/* loaded from: classes.dex */
public final class ImmutableTree<T> implements Iterable<Map.Entry<Path, T>> {
    private final ImmutableSortedMap<ChildKey, ImmutableTree<T>> children;
    private final T value;
    private static final ImmutableSortedMap EMPTY_CHILDREN = ImmutableSortedMap.Builder.emptyMap(StandardComparator.getComparator(ChildKey.class));
    private static final ImmutableTree EMPTY = new ImmutableTree(null, EMPTY_CHILDREN);

    /* loaded from: classes.dex */
    public interface TreeVisitor<T, R> {
        R onNodeValue(Path path, T t, R r);
    }

    public static <V> ImmutableTree<V> emptyInstance() {
        return EMPTY;
    }

    public ImmutableTree(T value, ImmutableSortedMap<ChildKey, ImmutableTree<T>> children) {
        this.value = value;
        this.children = children;
    }

    public ImmutableTree(T value) {
        this(value, EMPTY_CHILDREN);
    }

    public T getValue() {
        return this.value;
    }

    public ImmutableSortedMap<ChildKey, ImmutableTree<T>> getChildren() {
        return this.children;
    }

    public boolean isEmpty() {
        return this.value == null && this.children.isEmpty();
    }

    public Path findRootMostMatchingPath(Path relativePath, Predicate<? super T> predicate) {
        ChildKey front;
        ImmutableTree<T> child;
        Path path;
        if (this.value != null && predicate.evaluate((T) this.value)) {
            return Path.getEmptyPath();
        }
        if (relativePath.isEmpty() || (child = this.children.get((front = relativePath.getFront()))) == null || (path = child.findRootMostMatchingPath(relativePath.popFront(), predicate)) == null) {
            return null;
        }
        return new Path(front).child(path);
    }

    public Path findRootMostPathWithValue(Path relativePath) {
        return findRootMostMatchingPath(relativePath, Predicate.TRUE);
    }

    public T rootMostValue(Path relativePath) {
        return rootMostValueMatching(relativePath, Predicate.TRUE);
    }

    public T rootMostValueMatching(Path relativePath, Predicate<? super T> predicate) {
        if (this.value != null && predicate.evaluate((T) this.value)) {
            return this.value;
        }
        ImmutableTree<T> currentTree = this;
        Iterator<ChildKey> it = relativePath.iterator();
        while (it.hasNext()) {
            ChildKey key = it.next();
            currentTree = currentTree.children.get(key);
            if (currentTree == null) {
                return null;
            }
            if (currentTree.value != null && predicate.evaluate((T) currentTree.value)) {
                return currentTree.value;
            }
        }
        return null;
    }

    public T leafMostValue(Path relativePath) {
        return leafMostValueMatching(relativePath, Predicate.TRUE);
    }

    public T leafMostValueMatching(Path path, Predicate<? super T> predicate) {
        T currentValue = (this.value == null || !predicate.evaluate((T) this.value)) ? null : this.value;
        ImmutableTree<T> currentTree = this;
        Iterator<ChildKey> it = path.iterator();
        while (it.hasNext()) {
            ChildKey key = it.next();
            currentTree = currentTree.children.get(key);
            if (currentTree == null) {
                return currentValue;
            }
            if (currentTree.value != null && predicate.evaluate((T) currentTree.value)) {
                currentValue = currentTree.value;
            }
        }
        return currentValue;
    }

    public boolean containsMatchingValue(Predicate<? super T> predicate) {
        if (this.value == null || !predicate.evaluate((T) this.value)) {
            Iterator<Map.Entry<ChildKey, ImmutableTree<T>>> it = this.children.iterator();
            while (it.hasNext()) {
                Map.Entry<ChildKey, ImmutableTree<T>> subtree = it.next();
                if (subtree.getValue().containsMatchingValue(predicate)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public ImmutableTree<T> getChild(ChildKey child) {
        ImmutableTree<T> childTree = this.children.get(child);
        if (childTree != null) {
            return childTree;
        }
        return emptyInstance();
    }

    public ImmutableTree<T> subtree(Path relativePath) {
        if (relativePath.isEmpty()) {
            return this;
        }
        ChildKey front = relativePath.getFront();
        ImmutableTree<T> childTree = this.children.get(front);
        if (childTree != null) {
            return childTree.subtree(relativePath.popFront());
        }
        return emptyInstance();
    }

    public ImmutableTree<T> set(Path relativePath, T value) {
        if (relativePath.isEmpty()) {
            return new ImmutableTree<>(value, this.children);
        }
        ChildKey front = relativePath.getFront();
        ImmutableTree<T> child = this.children.get(front);
        if (child == null) {
            child = emptyInstance();
        }
        ImmutableTree<T> newChild = child.set(relativePath.popFront(), value);
        ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren = this.children.insert(front, newChild);
        return new ImmutableTree<>(this.value, newChildren);
    }

    public ImmutableTree<T> remove(Path relativePath) {
        ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren;
        if (relativePath.isEmpty()) {
            if (this.children.isEmpty()) {
                return emptyInstance();
            }
            return new ImmutableTree<>(null, this.children);
        }
        ChildKey front = relativePath.getFront();
        ImmutableTree<T> child = this.children.get(front);
        if (child != null) {
            ImmutableTree<T> newChild = child.remove(relativePath.popFront());
            if (newChild.isEmpty()) {
                newChildren = this.children.remove(front);
            } else {
                ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren2 = this.children;
                newChildren = newChildren2.insert(front, newChild);
            }
            if (this.value == null && newChildren.isEmpty()) {
                return emptyInstance();
            }
            return new ImmutableTree<>(this.value, newChildren);
        }
        return this;
    }

    public T get(Path relativePath) {
        if (relativePath.isEmpty()) {
            return this.value;
        }
        ChildKey front = relativePath.getFront();
        ImmutableTree<T> child = this.children.get(front);
        if (child != null) {
            return child.get(relativePath.popFront());
        }
        return null;
    }

    public ImmutableTree<T> setTree(Path relativePath, ImmutableTree<T> newTree) {
        ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren;
        if (relativePath.isEmpty()) {
            return newTree;
        }
        ChildKey front = relativePath.getFront();
        ImmutableTree<T> child = this.children.get(front);
        if (child == null) {
            child = emptyInstance();
        }
        ImmutableTree<T> newChild = child.setTree(relativePath.popFront(), newTree);
        if (newChild.isEmpty()) {
            newChildren = this.children.remove(front);
        } else {
            ImmutableSortedMap<ChildKey, ImmutableTree<T>> newChildren2 = this.children;
            newChildren = newChildren2.insert(front, newChild);
        }
        return new ImmutableTree<>(this.value, newChildren);
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void foreach(TreeVisitor<T, Void> visitor) {
        fold(Path.getEmptyPath(), visitor, null);
    }

    public <R> R fold(R accum, TreeVisitor<? super T, R> visitor) {
        return (R) fold(Path.getEmptyPath(), visitor, accum);
    }

    private <R> R fold(Path relativePath, TreeVisitor<? super T, R> visitor, R accum) {
        Iterator<Map.Entry<ChildKey, ImmutableTree<T>>> it = this.children.iterator();
        while (it.hasNext()) {
            Map.Entry<ChildKey, ImmutableTree<T>> subtree = it.next();
            accum = (R) subtree.getValue().fold(relativePath.child(subtree.getKey()), visitor, accum);
        }
        return this.value != null ? visitor.onNodeValue(relativePath, this.value, accum) : accum;
    }

    public Collection<T> values() {
        final ArrayList<T> list = new ArrayList<>();
        foreach(new TreeVisitor<T, Void>() { // from class: com.google.firebase.database.core.utilities.ImmutableTree.1
            @Override // com.google.firebase.database.core.utilities.ImmutableTree.TreeVisitor
            public /* bridge */ /* synthetic */ Void onNodeValue(Path path, Object obj, Void r3) {
                return onNodeValue2(path, (Path) obj, r3);
            }

            /* renamed from: onNodeValue  reason: avoid collision after fix types in other method */
            public Void onNodeValue2(Path relativePath, T value, Void accum) {
                list.add(value);
                return null;
            }
        });
        return list;
    }

    @Override // java.lang.Iterable
    public Iterator<Map.Entry<Path, T>> iterator() {
        final List<Map.Entry<Path, T>> list = new ArrayList<>();
        foreach(new TreeVisitor<T, Void>() { // from class: com.google.firebase.database.core.utilities.ImmutableTree.2
            @Override // com.google.firebase.database.core.utilities.ImmutableTree.TreeVisitor
            public /* bridge */ /* synthetic */ Void onNodeValue(Path path, Object obj, Void r3) {
                return onNodeValue2(path, (Path) obj, r3);
            }

            /* renamed from: onNodeValue  reason: avoid collision after fix types in other method */
            public Void onNodeValue2(Path relativePath, T value, Void accum) {
                list.add(new AbstractMap.SimpleImmutableEntry(relativePath, value));
                return null;
            }
        });
        return list.iterator();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ImmutableTree { value=");
        builder.append(getValue());
        builder.append(", children={");
        Iterator<Map.Entry<ChildKey, ImmutableTree<T>>> it = this.children.iterator();
        while (it.hasNext()) {
            Map.Entry<ChildKey, ImmutableTree<T>> child = it.next();
            builder.append(child.getKey().asString());
            builder.append("=");
            builder.append(child.getValue());
        }
        builder.append("} }");
        return builder.toString();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImmutableTree that = (ImmutableTree) o;
        if (this.children == null ? that.children != null : !this.children.equals(that.children)) {
            return false;
        }
        if (this.value == null ? that.value == null : this.value.equals(that.value)) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        int result = this.value != null ? this.value.hashCode() : 0;
        return (result * 31) + (this.children != null ? this.children.hashCode() : 0);
    }
}
