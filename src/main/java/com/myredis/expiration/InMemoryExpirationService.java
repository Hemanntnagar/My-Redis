package com.myredis.expiration;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

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
