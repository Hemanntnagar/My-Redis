package com.myredis.storage;

import com.myredis.config.MaxMemoryPolicy;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.StorageException;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.metrics.AtomicMetricsRecorder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEvictionTest {

    @Test
    void noevictionRejectsWritesWhenOverLimit() throws StorageException {
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        MemoryUsageTracker memory = new MemoryUsageTracker();
        EvictingKeyValueStore store =
                new EvictingKeyValueStore(
                        new InMemoryKeyValueStore(),
                        memory,
                        200,
                        MaxMemoryPolicy.NOEVICTION,
                        expiration,
                        new AtomicMetricsRecorder());

        store.put(key("a"), value("aaa"));
        assertThrows(StorageException.class, () -> store.put(key("b"), value("bbbbbbbbbbbbbbbbbbbbbbbb")));
    }

    @Test
    void allkeysLruEvictsLeastRecentlyUsed() throws StorageException {
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        MemoryUsageTracker memory = new MemoryUsageTracker();
        AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
        EvictingKeyValueStore store =
                new EvictingKeyValueStore(
                        new InMemoryKeyValueStore(),
                        memory,
                        220,
                        MaxMemoryPolicy.ALLKEYS_LRU,
                        expiration,
                        metrics);

        store.put(key("old"), value("1"));
        store.put(key("warm"), value("2"));
        store.get(key("warm"));
        store.put(key("new"), value("3"));

        assertTrue(store.get(key("old")).isEmpty());
        assertTrue(store.get(key("warm")).isPresent());
        assertTrue(store.get(key("new")).isPresent());
        assertEquals(1, metrics.getCounter("evicted_keys_total"));
    }

    @Test
    void volatileLruEvictsOnlyKeysWithTtl() throws StorageException {
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        MemoryUsageTracker memory = new MemoryUsageTracker();
        EvictingKeyValueStore store =
                new EvictingKeyValueStore(
                        new InMemoryKeyValueStore(),
                        memory,
                        250,
                        MaxMemoryPolicy.VOLATILE_LRU,
                        expiration,
                        new AtomicMetricsRecorder());

        store.put(key("persistent"), value("p"));
        store.put(key("volatile"), value("v1"));
        expiration.register(key("volatile"), System.currentTimeMillis() + 60_000);
        store.put(key("anotherVolatile"), value("v2"));
        expiration.register(key("anotherVolatile"), System.currentTimeMillis() + 60_000);

        assertTrue(store.get(key("persistent")).isPresent());
        boolean firstVolatile = store.get(key("volatile")).isPresent();
        boolean secondVolatile = store.get(key("anotherVolatile")).isPresent();
        assertTrue(firstVolatile ^ secondVolatile);
    }

    private static byte[] key(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static StringRedisValue value(String text) {
        return new StringRedisValue(text.getBytes(StandardCharsets.UTF_8));
    }
}
