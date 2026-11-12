package com.myredis.storage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Access-order LRU index; not thread-safe — guarded by the eviction store monitor.
 */
final class LruEvictionIndex {

    private final LinkedHashMap<BinaryKey, Boolean> order = new LinkedHashMap<>(16, 0.75f, true);

    void touch(byte[] key) {
        order.put(new BinaryKey(key), Boolean.TRUE);
    }

    void remove(byte[] key) {
        order.remove(new BinaryKey(key));
    }

    void clear() {
        order.clear();
    }

    Optional<byte[]> oldestEligible(Predicate<byte[]> eligible) {
        for (Map.Entry<BinaryKey, Boolean> entry : order.entrySet()) {
            byte[] key = entry.getKey().bytes;
            if (eligible.test(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    private record BinaryKey(byte[] bytes) {

        private BinaryKey {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BinaryKey that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

    }
}
