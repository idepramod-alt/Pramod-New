package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.Node;
/* loaded from: classes.dex */
public final class UserWriteRecord {
    private final CompoundWrite merge;
    private final Node overwrite;
    private final Path path;
    private final boolean visible;
    private final long writeId;

    public UserWriteRecord(long writeId, Path path, Node overwrite, boolean visible) {
        this.writeId = writeId;
        this.path = path;
        this.overwrite = overwrite;
        this.merge = null;
        this.visible = visible;
    }

    public UserWriteRecord(long writeId, Path path, CompoundWrite merge) {
        this.writeId = writeId;
        this.path = path;
        this.overwrite = null;
        this.merge = merge;
        this.visible = true;
    }

    public long getWriteId() {
        return this.writeId;
    }

    public Path getPath() {
        return this.path;
    }

    public Node getOverwrite() {
        if (this.overwrite == null) {
            throw new IllegalArgumentException("Can't access overwrite when write is a merge!");
        }
        return this.overwrite;
    }

    public CompoundWrite getMerge() {
        if (this.merge == null) {
            throw new IllegalArgumentException("Can't access merge when write is an overwrite!");
        }
        return this.merge;
    }

    public boolean isMerge() {
        return this.merge != null;
    }

    public boolean isOverwrite() {
        return this.overwrite != null;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserWriteRecord record = (UserWriteRecord) o;
        if (this.writeId != record.writeId || !this.path.equals(record.path) || this.visible != record.visible) {
            return false;
        }
        if (this.overwrite == null ? record.overwrite != null : !this.overwrite.equals(record.overwrite)) {
            return false;
        }
        if (this.merge == null ? record.merge == null : this.merge.equals(record.merge)) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        int result = Long.valueOf(this.writeId).hashCode();
        return (((((((result * 31) + Boolean.valueOf(this.visible).hashCode()) * 31) + this.path.hashCode()) * 31) + (this.overwrite != null ? this.overwrite.hashCode() : 0)) * 31) + (this.merge != null ? this.merge.hashCode() : 0);
    }

    public String toString() {
        return "UserWriteRecord{id=" + this.writeId + " path=" + this.path + " visible=" + this.visible + " overwrite=" + this.overwrite + " merge=" + this.merge + "}";
    }
}
