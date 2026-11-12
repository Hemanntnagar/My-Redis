package com.myredis.persistence.snapshot;

import com.myredis.datatype.RedisValue;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Captures and restores database state including expiration metadata. */
public final class KeyspaceSnapshotter {

    private KeyspaceSnapshotter() {}

    public static DatabaseSnapshot capture(ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration) {
        List<byte[]> keys = new ArrayList<>(store.snapshotKeys());
        keys.sort(Arrays::compareUnsigned);
        List<SnapshotEntry> entries = new ArrayList<>(keys.size());
        for (byte[] key : keys) {
            if (!store.exists(key)) {
                continue;
            }
            Optional<RedisValue> value = store.get(key);
            if (value.isEmpty()) {
                continue;
            }
            long expireAt = expiration.expireAtMillis(key).orElse(SnapshotFormat.NO_EXPIRE);
            entries.add(new SnapshotEntry(key, value.get(), expireAt));
        }
        return new DatabaseSnapshot(entries);
    }

    public static void restore(
            ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration, DatabaseSnapshot snapshot) {
        store.clear();
        for (SnapshotEntry entry : snapshot.entries()) {
            try {
                store.put(entry.key(), entry.value());
            } catch (com.myredis.exception.StorageException failure) {
                throw new IllegalStateException("failed to restore snapshot entry", failure);
            }
            if (entry.expireAtEpochMillis() != SnapshotFormat.NO_EXPIRE) {
                expiration.register(entry.key(), entry.expireAtEpochMillis());
            }
        }
    }
}
