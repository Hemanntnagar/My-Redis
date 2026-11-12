package com.myredis.expiration;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.StorageException;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundExpirationCleanupTest {

    @Test
    void runOnceRemovesOnlyExpiredKeys() throws StorageException {
        MutableExpirationClock clock = new MutableExpirationClock(1_000L);
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore store =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration, clock);
        BackgroundExpirationCleanup cleanup =
                new BackgroundExpirationCleanup(
                        store, expiration, ExpirationCleanupPolicy.defaults(), delay -> {});

        byte[] expired = "gone".getBytes(StandardCharsets.UTF_8);
        byte[] live = "stay".getBytes(StandardCharsets.UTF_8);
        store.put(expired, new StringRedisValue("1".getBytes(StandardCharsets.UTF_8)));
        store.put(live, new StringRedisValue("2".getBytes(StandardCharsets.UTF_8)));
        expiration.register(expired, 900L);
        expiration.register(live, 5_000L);

        int removed = cleanup.runOnce();
        assertEquals(1, removed);
        assertFalse(store.exists(expired));
        assertTrue(store.exists(live));
    }

    @Test
    void startAndCloseDoesNotLeaveWorkerRunning() throws StorageException, InterruptedException {
        MutableExpirationClock clock = new MutableExpirationClock(0L);
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore store =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration, clock);
        BackgroundExpirationCleanup cleanup =
                new BackgroundExpirationCleanup(
                        store,
                        expiration,
                        new ExpirationCleanupPolicy(5L, 10L, 16, 8),
                        delay -> Thread.sleep(delay));

        byte[] key = "k".getBytes(StandardCharsets.UTF_8);
        store.put(key, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(key, 1L);
        clock.setNow(10L);

        cleanup.start();
        assertTrue(cleanup.isRunning());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (store.keyCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        cleanup.close();
        assertFalse(cleanup.isRunning());
        assertEquals(0, store.keyCount());
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
