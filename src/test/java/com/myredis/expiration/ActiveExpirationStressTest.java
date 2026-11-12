package com.myredis.expiration;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.StorageException;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveExpirationStressTest {

    private static final int KEY_COUNT = 4_000;
    private static final int THREADS = 32;

    @Test
    void concurrentLazyAndActiveExpirationClearsKeyspace() throws Exception {
        MutableExpirationClock clock = new MutableExpirationClock(0L);
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore store =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration, clock);
        ExpirationCleanupPolicy policy = new ExpirationCleanupPolicy(1L, 1L, 256, 128);
        BackgroundExpirationCleanup cleanup =
                new BackgroundExpirationCleanup(
                        store,
                        expiration,
                        policy,
                        delay -> {
                            if (delay > 0L) {
                                Thread.sleep(Math.min(delay, 5L));
                            }
                        });

        long expireAt = 1_000L;
        List<byte[]> keys = new ArrayList<>(KEY_COUNT);
        for (int index = 0; index < KEY_COUNT; index++) {
            byte[] key = ("stress:" + index).getBytes(StandardCharsets.UTF_8);
            keys.add(key);
            store.put(key, new StringRedisValue(key));
            expiration.register(key, expireAt);
        }
        assertEquals(KEY_COUNT, store.keyCount());

        clock.setNow(expireAt);
        AtomicInteger lazyTouches = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            java.util.concurrent.atomic.AtomicBoolean readersRunning = new java.util.concurrent.atomic.AtomicBoolean(true);
            for (int worker = 0; worker < THREADS; worker++) {
                tasks.add(
                        pool.submit(
                                () -> {
                                    while (readersRunning.get()) {
                                        byte[] key = keys.get((int) (Math.random() * keys.size()));
                                        if (store.get(key).isPresent()) {
                                            lazyTouches.incrementAndGet();
                                        }
                                        if (store.exists(key)) {
                                            lazyTouches.incrementAndGet();
                                        }
                                        Thread.onSpinWait();
                                    }
                                }));
            }

            cleanup.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (store.keyCount() > 0 && System.nanoTime() < deadline) {
                cleanup.runOnce();
            }
            readersRunning.set(false);
            for (Future<?> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
            cleanup.close();

            assertEquals(0, store.keyCount(), "values should be purged");
            assertEquals(0, expiration.trackedKeyCount(), "metadata should be purged");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void tryRemoveIfExpiredIsSafeUnderRenewal() throws Exception {
        MutableExpirationClock clock = new MutableExpirationClock(100L);
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore store =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration, clock);
        byte[] key = "renew".getBytes(StandardCharsets.UTF_8);
        store.put(key, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(key, clock.currentTimeMillis() + 60_000L);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                tasks.add(pool.submit(() -> store.activeExpireCycle(64, 32)));
                tasks.add(pool.submit(() -> store.get(key)));
                tasks.add(
                        pool.submit(
                                () -> {
                                    if (Math.random() > 0.5) {
                                        expiration.register(key, clock.currentTimeMillis() + 60_000L);
                                    } else {
                                        expiration.cancel(key);
                                        expiration.register(key, clock.currentTimeMillis() + 60_000L);
                                    }
                                }));
            }
            for (Future<?> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
            assertTrue(store.exists(key), "renewal should prevent stale active deletion");
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class MutableExpirationClock implements ExpirationClock {

        private volatile long nowMillis;

        private MutableExpirationClock(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        void setNow(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        @Override
        public long currentTimeMillis() {
            return nowMillis;
        }
    }
}
