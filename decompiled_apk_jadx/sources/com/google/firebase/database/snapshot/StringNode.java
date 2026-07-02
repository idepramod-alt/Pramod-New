package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.snapshot.LeafNode;
import com.google.firebase.database.snapshot.Node;
/* loaded from: classes.dex */
public class StringNode extends LeafNode<StringNode> {
    private final String value;

    public StringNode(String value, Node priority) {
        super(priority);
        this.value = value;
    }

    @Override // com.google.firebase.database.snapshot.Node
    public Object getValue() {
        return this.value;
    }

    @Override // com.google.firebase.database.snapshot.Node
    public String getHashRepresentation(Node.HashVersion version) {
        switch (version) {
            case V1:
                return getPriorityHash(version) + "string:" + this.value;
            case V2:
                return getPriorityHash(version) + "string:" + Utilities.stringHashV2Representation(this.value);
            default:
                throw new IllegalArgumentException("Invalid hash version for string node: " + version);
        }
    }

    @Override // com.google.firebase.database.snapshot.Node
    public StringNode updatePriority(Node priority) {
        return new StringNode(this.value, priority);
    }

    @Override // com.google.firebase.database.snapshot.LeafNode
    protected LeafNode.LeafType getLeafType() {
        return LeafNode.LeafType.String;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.google.firebase.database.snapshot.LeafNode
    public int compareLeafValues(StringNode other) {
        return this.value.compareTo(other.value);
    }

    @Override // com.google.firebase.database.snapshot.LeafNode
    public boolean equals(Object other) {
        if (other instanceof StringNode) {
            StringNode otherStringNode = (StringNode) other;
            return this.value.equals(otherStringNode.value) && this.priority.equals(otherStringNode.priority);
        }
        return false;
    }

    @Override // com.google.firebase.database.snapshot.LeafNode
    public int hashCode() {
        return this.value.hashCode() + this.priority.hashCode();
    }
}
