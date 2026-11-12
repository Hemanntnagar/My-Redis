package com.myredis.storage;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-size striped locks keyed by byte array content. Two-key acquisition orders keys by unsigned
 * lexicographic order to avoid deadlocks.
 */
public final class KeyStripedLock {

    private static final int STRIPE_COUNT = 256;

    private final Lock[] stripes = new Lock[STRIPE_COUNT];

    public KeyStripedLock() {
        for (int index = 0; index < STRIPE_COUNT; index++) {
            stripes[index] = new ReentrantLock();
        }
    }

    public void runExclusive(byte[] key, Runnable action) {
        Lock lock = stripeFor(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    public <T> T callExclusive(byte[] key, java.util.concurrent.Callable<T> action) throws Exception {
        Lock lock = stripeFor(key);
        lock.lock();
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    public <T> T callExclusiveReturning(byte[] key, java.util.concurrent.Callable<T> action) {
        try {
            return callExclusive(key, action);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    public void runExclusive(byte[] firstKey, byte[] secondKey, Runnable action) {
        if (unsignedEquals(firstKey, secondKey)) {
            runExclusive(firstKey, action);
            return;
        }
        byte[] primary;
        byte[] secondary;
        if (compareUnsigned(firstKey, secondKey) < 0) {
            primary = firstKey;
            secondary = secondKey;
        } else {
            primary = secondKey;
            secondary = firstKey;
        }
        Lock primaryLock = stripeFor(primary);
        Lock secondaryLock = stripeFor(secondary);
        primaryLock.lock();
        try {
            secondaryLock.lock();
            try {
                action.run();
            } finally {
                secondaryLock.unlock();
            }
        } finally {
            primaryLock.unlock();
        }
    }

    private Lock stripeFor(byte[] key) {
        return stripes[(Arrays.hashCode(key) & 0x7FFF_FFFF) % STRIPE_COUNT];
    }

    static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int l = left[index] & 0xFF;
            int r = right[index] & 0xFF;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static boolean unsignedEquals(byte[] left, byte[] right) {
        return Arrays.equals(left, right);
    }
}
