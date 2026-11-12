package com.myredis.expiration;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Thread-safe in-memory {@link ExpirationService} storing {@link KeyExpirationMetadata} per key.
 */
public final class InMemoryExpirationService implements ExpirationService {

    private final Map<BinaryKey, KeyExpirationMetadata> metadataByKey = new ConcurrentHashMap<>();

    @Override
    public void register(byte[] key, long expireAtEpochMillis) {
        Objects.requireNonNull(key, "key");
        metadataByKey.put(new BinaryKey(key), new KeyExpirationMetadata(expireAtEpochMillis));
    }

    @Override
    public void cancel(byte[] key) {
        Objects.requireNonNull(key, "key");
        metadataByKey.remove(new BinaryKey(key));
    }

    @Override
    public boolean isExpired(byte[] key, long nowEpochMillis) {
        return findMetadata(key).map(meta -> meta.isExpiredAt(nowEpochMillis)).orElse(false);
    }

    @Override
    public Optional<KeyExpirationMetadata> findMetadata(byte[] key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(metadataByKey.get(new BinaryKey(key)));
    }

    public OptionalLong expireAtMillis(byte[] key) {
        Optional<KeyExpirationMetadata> metadata = findMetadata(key);
        if (metadata.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(metadata.get().expireAtEpochMillis());
    }

    public void transfer(byte[] source, byte[] destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (Arrays.equals(source, destination)) {
            return;
        }
        KeyExpirationMetadata metadata = metadataByKey.remove(new BinaryKey(source));
        if (metadata != null) {
            metadataByKey.put(new BinaryKey(destination), metadata);
        }
    }

    public void clearAll() {
        metadataByKey.clear();
    }

    public int trackedKeyCount() {
        return metadataByKey.size();
    }

    /**
     * Removes expiration metadata when the key is expired at {@code now}. Uses compare-and-remove so a
     * concurrent {@code EXPIRE} or {@code PERSIST} is not overwritten.
     */
    public boolean tryRemoveIfExpired(byte[] key, long nowEpochMillis) {
        Objects.requireNonNull(key, "key");
        BinaryKey binaryKey = new BinaryKey(key);
        KeyExpirationMetadata current = metadataByKey.get(binaryKey);
        if (current == null || !current.isExpiredAt(nowEpochMillis)) {
            return false;
        }
        return metadataByKey.remove(binaryKey, current);
    }

    /**
     * Visits at most {@code maxScans} metadata entries in arbitrary order without holding a global lock.
     */
    public void scanMetadata(int maxScans, BiConsumer<byte[], KeyExpirationMetadata> visitor) {
        if (maxScans < 1) {
            return;
        }
        int scanned = 0;
        for (Map.Entry<BinaryKey, KeyExpirationMetadata> entry : metadataByKey.entrySet()) {
            if (scanned >= maxScans) {
                return;
            }
            scanned++;
            visitor.accept(entry.getKey().bytes().clone(), entry.getValue());
        }
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
