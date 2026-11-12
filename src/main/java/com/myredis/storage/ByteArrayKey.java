package com.myredis.storage;

import java.util.Arrays;
import java.util.Objects;

/**
 * Map key over {@code byte[]} with content-based equality. {@link #owned(byte[])} clones payload for
 * storage; {@link #probe(byte[])} references caller bytes and is only safe for lookups.
 */
public final class ByteArrayKey {

    private final byte[] bytes;

    private ByteArrayKey(byte[] bytes) {
        this.bytes = bytes;
    }

    public static ByteArrayKey owned(byte[] key) {
        return new ByteArrayKey(key.clone());
    }

    public static ByteArrayKey probe(byte[] key) {
        return new ByteArrayKey(Objects.requireNonNull(key, "key"));
    }

    public byte[] bytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ByteArrayKey that && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
