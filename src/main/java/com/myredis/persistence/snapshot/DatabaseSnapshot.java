package com.myredis.persistence.snapshot;

import java.util.List;
import java.util.Objects;

public record DatabaseSnapshot(List<SnapshotEntry> entries) {

    public DatabaseSnapshot {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
