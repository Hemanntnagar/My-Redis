package com.myredis.expiration;

import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Daemon thread that actively removes expired keys in bounded batches to limit CPU usage.
 */
public final class BackgroundExpirationCleanup implements AutoCloseable {

    private final ExpirationAwareKeyValueStore store;
    private final InMemoryExpirationService expiration;
    private final ExpirationCleanupPolicy policy;
    private final Sleeper sleeper;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread worker;

    public BackgroundExpirationCleanup(
            ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration, ExpirationCleanupPolicy policy) {
        this(store, expiration, policy, Thread::sleep);
    }

    BackgroundExpirationCleanup(
            ExpirationAwareKeyValueStore store,
            InMemoryExpirationService expiration,
            ExpirationCleanupPolicy policy,
            Sleeper sleeper) {
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker =
                new Thread(
                        () -> {
                            try {
                                runLoop();
                            } finally {
                                running.set(false);
                            }
                        },
                        "myredis-expiration-janitor");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void close() {
        running.set(false);
        Thread active = worker;
        if (active != null) {
            active.interrupt();
            try {
                active.join(5_000L);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
    }

    /**
     * Performs one active expiration cycle; useful for tests without waiting on the background thread.
     */
    public int runOnce() {
        return store.activeExpireCycle(policy.maxKeysScannedPerTick(), policy.maxKeysDeletedPerTick());
    }

    boolean isRunning() {
        return running.get();
    }

    private void runLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            int deleted = runOnce();
            long delay = sleepDelayMillis(deleted);
            if (!sleep(delay)) {
                break;
            }
        }
    }

    private long sleepDelayMillis(int deletedKeys) {
        if (deletedKeys > 0) {
            return policy.tickMillis();
        }
        if (expiration.trackedKeyCount() == 0) {
            return policy.idleBackoffMillis();
        }
        return policy.tickMillis();
    }

    private boolean sleep(long delayMillis) {
        if (delayMillis <= 0L) {
            return running.get() && !Thread.currentThread().isInterrupted();
        }
        try {
            sleeper.sleep(delayMillis);
            return true;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long delayMillis) throws InterruptedException;
    }
}
