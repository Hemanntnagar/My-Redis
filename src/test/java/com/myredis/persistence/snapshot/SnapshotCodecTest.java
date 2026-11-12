package com.myredis.persistence.snapshot;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.PersistenceException;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCodecTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripAllTypesWithExpiration() throws Exception {
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore store =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration);

        byte[] keyString = "s".getBytes(StandardCharsets.UTF_8);
        store.put(keyString, new StringRedisValue("hello".getBytes(StandardCharsets.UTF_8)));
        expiration.register(keyString, System.currentTimeMillis() + 86_400_000L);

        byte[] keyList = "list".getBytes(StandardCharsets.UTF_8);
        ListRedisValue list = new ListRedisValue();
        list.pushHead(new byte[][] {"a".getBytes(StandardCharsets.UTF_8)});
        list.pushTail(new byte[][] {"b".getBytes(StandardCharsets.UTF_8)});
        store.put(keyList, list);

        byte[] keySet = "set".getBytes(StandardCharsets.UTF_8);
        SetRedisValue set = new SetRedisValue();
        set.add("x".getBytes(StandardCharsets.UTF_8), "y".getBytes(StandardCharsets.UTF_8));
        store.put(keySet, set);

        byte[] keyHash = "hash".getBytes(StandardCharsets.UTF_8);
        HashRedisValue hash = new HashRedisValue();
        hash.set("f".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));
        store.put(keyHash, hash);

        byte[] keyZset = "z".getBytes(StandardCharsets.UTF_8);
        SortedSetRedisValue zset = new SortedSetRedisValue();
        zset.add(2.5, "m".getBytes(StandardCharsets.UTF_8));
        store.put(keyZset, zset);

        DatabaseSnapshot captured = KeyspaceSnapshotter.capture(store, expiration);
        byte[] encoded = SnapshotWriter.encode(captured);
        DatabaseSnapshot decoded = SnapshotReader.decode(encoded);
        assertEquals(captured.entries().size(), decoded.entries().size());

        ExpirationAwareKeyValueStore restored =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), new InMemoryExpirationService());
        InMemoryExpirationService restoredExpiration = restored.expirationService();
        KeyspaceSnapshotter.restore(restored, restoredExpiration, decoded);

        assertEquals(5, restored.keyCount());
        assertArrayEquals(
                "hello".getBytes(StandardCharsets.UTF_8),
                ((StringRedisValue) restored.get(keyString).orElseThrow()).bytes());
        assertTrue(restoredExpiration.expireAtMillis(keyString).orElseThrow() > System.currentTimeMillis());
        assertEquals(2, ((ListRedisValue) restored.get(keyList).orElseThrow()).length());
        assertEquals(2, ((SetRedisValue) restored.get(keySet).orElseThrow()).size());
        assertEquals(1, ((HashRedisValue) restored.get(keyHash).orElseThrow()).size());
        assertEquals(1, ((SortedSetRedisValue) restored.get(keyZset).orElseThrow()).size());
    }

    @Test
    void atomicWriteAndLoadFromDisk() throws Exception {
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore store =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration);
        byte[] key = "persisted".getBytes(StandardCharsets.UTF_8);
        store.put(key, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));

        DatabaseSnapshot snapshot = KeyspaceSnapshotter.capture(store, expiration);
        byte[] encoded = SnapshotWriter.encode(snapshot);
        AtomicSnapshotFiles.writeAtomically(tempDir, encoded);

        byte[] fromDisk = AtomicSnapshotFiles.readIfPresent(tempDir);
        DatabaseSnapshot loaded = SnapshotReader.decode(fromDisk);
        assertEquals(1, loaded.entries().size());
    }

    @Test
    void detectsCorruptedChecksum() throws Exception {
        DatabaseSnapshot snapshot =
                new DatabaseSnapshot(
                        List.of(
                                new SnapshotEntry(
                                        "k".getBytes(StandardCharsets.UTF_8),
                                        new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)),
                                        SnapshotFormat.NO_EXPIRE)));
        byte[] encoded = SnapshotWriter.encode(snapshot);
        encoded[encoded.length - 1] ^= 0xFF;
        assertThrows(PersistenceException.class, () -> SnapshotReader.decode(encoded));
    }

    @Test
    void detectsInvalidMagic() throws Exception {
        byte[] garbage = Arrays.copyOf(SnapshotFormat.MAGIC, 32);
        Arrays.fill(garbage, 4, garbage.length, (byte) 0);
        assertThrows(PersistenceException.class, () -> SnapshotReader.decode(garbage));
    }
}
