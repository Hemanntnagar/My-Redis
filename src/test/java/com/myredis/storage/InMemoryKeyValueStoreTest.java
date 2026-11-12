package com.myredis.storage;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKeyValueStoreTest {

    private static final byte[] KEY = "mykey".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
    }

    @Test
    void getReturnsEmptyWhenKeyMissing() {
        assertTrue(store.get(KEY).isEmpty());
    }

    @Test
    void putAndGetRoundTripStringValue() throws StorageException {
        byte[] valueBytes = "hello".getBytes(StandardCharsets.UTF_8);
        store.put(KEY, new StringRedisValue(valueBytes));

        Optional<com.myredis.datatype.RedisValue> stored = store.get(KEY);
        assertTrue(stored.isPresent());
        assertInstanceOf(StringRedisValue.class, stored.get());
        assertArrayEquals(valueBytes, ((StringRedisValue) stored.get()).bytes());
    }

    @Test
    void existsReflectsPresence() throws StorageException {
        assertFalse(store.exists(KEY));
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        assertTrue(store.exists(KEY));
    }

    @Test
    void deleteRemovesKeyAndReportsWhetherPresent() throws StorageException {
        assertFalse(store.delete(KEY));
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        assertTrue(store.delete(KEY));
        assertFalse(store.exists(KEY));
        assertTrue(store.get(KEY).isEmpty());
    }

    @Test
    void putRejectsNullKeyOrValue() {
        byte[] value = "x".getBytes(StandardCharsets.UTF_8);
        assertThrows(StorageException.class, () -> store.put(null, new StringRedisValue(value)));
        assertThrows(StorageException.class, () -> store.put(KEY, null));
    }

    @Test
    void getDeleteAndExistRejectNullKey() {
        assertThrows(NullPointerException.class, () -> store.get(null));
        assertThrows(NullPointerException.class, () -> store.delete(null));
        assertThrows(NullPointerException.class, () -> store.exists(null));
    }

    @Test
    void keysMatchByContentNotReference() throws StorageException {
        byte[] keyCopy = KEY.clone();
        store.put(keyCopy, new StringRedisValue("a".getBytes(StandardCharsets.UTF_8)));
        assertTrue(store.exists(KEY));
        assertTrue(store.get(KEY).isPresent());
    }

    @Test
    void concurrentPutsRemainConsistent() throws InterruptedException {
        int threads = 8;
        int keysPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < keysPerThread; i++) {
                                byte[] key = ("k-" + threadId + "-" + i).getBytes(StandardCharsets.UTF_8);
                                store.put(
                                        key,
                                        new StringRedisValue(("v-" + threadId + "-" + i).getBytes(StandardCharsets.UTF_8)));
                            }
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(0, failures.get());

        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < keysPerThread; i++) {
                byte[] key = ("k-" + t + "-" + i).getBytes(StandardCharsets.UTF_8);
                byte[] expected = ("v-" + t + "-" + i).getBytes(StandardCharsets.UTF_8);
                StringRedisValue value = (StringRedisValue) store.get(key).orElseThrow();
                assertArrayEquals(expected, value.bytes());
            }
        }
    }

    @Test
    void putOverwritesExistingValue() throws StorageException {
        store.put(KEY, new StringRedisValue("first".getBytes(StandardCharsets.UTF_8)));
        store.put(KEY, new StringRedisValue("second".getBytes(StandardCharsets.UTF_8)));
        StringRedisValue current = (StringRedisValue) store.get(KEY).orElseThrow();
        assertArrayEquals("second".getBytes(StandardCharsets.UTF_8), current.bytes());
    }

    @Test
    void keyCountClearAndSnapshotKeysSupportKeyspaceCommands() throws StorageException {
        assertEquals(0L, store.keyCount());
        store.put(KEY, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertEquals(1L, store.keyCount());
        assertArrayEquals(KEY, store.snapshotKeys().getFirst());
        store.clear();
        assertEquals(0L, store.keyCount());
        assertTrue(store.snapshotKeys().isEmpty());
    }

    @Test
    void returnedValueBytesAreDefensiveCopies() throws StorageException {
        byte[] original = "data".getBytes(StandardCharsets.UTF_8);
        store.put(KEY, new StringRedisValue(original));
        StringRedisValue stored = (StringRedisValue) store.get(KEY).orElseThrow();
        byte[] returned = stored.bytes();
        returned[0] = 'X';
        assertArrayEquals(original, ((StringRedisValue) store.get(KEY).orElseThrow()).bytes());
    }
}
