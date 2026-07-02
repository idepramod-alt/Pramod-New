package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.ChildrenNode;
import com.google.firebase.database.snapshot.Node;
import java.util.HashMap;
import java.util.Map;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class SparseSnapshotTree {
    private Node value = null;
    private Map<ChildKey, SparseSnapshotTree> children = null;

    /* loaded from: classes.dex */
    public interface SparseSnapshotChildVisitor {
        void visitChild(ChildKey childKey, SparseSnapshotTree sparseSnapshotTree);
    }

    /* loaded from: classes.dex */
    public interface SparseSnapshotTreeVisitor {
        void visitTree(Path path, Node node);
    }

    public void remember(Path path, Node data) {
        if (path.isEmpty()) {
            this.value = data;
            this.children = null;
        } else if (this.value != null) {
            this.value = this.value.updateChild(path, data);
        } else {
            if (this.children == null) {
                this.children = new HashMap();
            }
            ChildKey childKey = path.getFront();
            if (!this.children.containsKey(childKey)) {
                this.children.put(childKey, new SparseSnapshotTree());
            }
            SparseSnapshotTree child = this.children.get(childKey);
            child.remember(path.popFront(), data);
        }
    }

    public boolean forget(final Path path) {
        if (path.isEmpty()) {
            this.value = null;
            this.children = null;
            return true;
        } else if (this.value != null) {
            if (this.value.isLeafNode()) {
                return false;
            }
            ChildrenNode childrenNode = (ChildrenNode) this.value;
            this.value = null;
            childrenNode.forEachChild(new ChildrenNode.ChildVisitor() { // from class: com.google.firebase.database.core.SparseSnapshotTree.1
                @Override // com.google.firebase.database.snapshot.ChildrenNode.ChildVisitor
                public void visitChild(ChildKey name, Node child) {
                    SparseSnapshotTree.this.remember(path.child(name), child);
                }
            });
            return forget(path);
        } else if (this.children != null) {
            ChildKey childKey = path.getFront();
            Path childPath = path.popFront();
            if (this.children.containsKey(childKey)) {
                SparseSnapshotTree child = this.children.get(childKey);
                boolean safeToRemove = child.forget(childPath);
                if (safeToRemove) {
                    this.children.remove(childKey);
                }
            }
            if (this.children.isEmpty()) {
                this.children = null;
                return true;
            }
            return false;
        } else {
            return true;
        }
    }

    public void forEachTree(final Path prefixPath, final SparseSnapshotTreeVisitor visitor) {
        if (this.value != null) {
            visitor.visitTree(prefixPath, this.value);
        } else {
            forEachChild(new SparseSnapshotChildVisitor() { // from class: com.google.firebase.database.core.SparseSnapshotTree.2
                @Override // com.google.firebase.database.core.SparseSnapshotTree.SparseSnapshotChildVisitor
                public void visitChild(ChildKey key, SparseSnapshotTree tree) {
                    tree.forEachTree(prefixPath.child(key), visitor);
                }
            });
        }
    }

    public void forEachChild(SparseSnapshotChildVisitor visitor) {
        if (this.children != null) {
            for (Map.Entry<ChildKey, SparseSnapshotTree> entry : this.children.entrySet()) {
                visitor.visitChild(entry.getKey(), entry.getValue());
            }
        }
    }
}
