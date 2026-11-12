package com.myredis.persistence;

import com.myredis.datatype.RedisValue;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.zip.CRC32;

/**
 * Deterministic CRC32 over sorted keys, types, and expiration metadata for post-recovery validation.
 */
public final class KeyspaceIntegrityDigest {

    private KeyspaceIntegrityDigest() {}

    public static long checksum(ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration) {
        List<byte[]> keys = new ArrayList<>(store.snapshotKeys());
        keys.sort(KeyspaceIntegrityDigest::compareUnsigned);

        CRC32 crc = new CRC32();
        for (byte[] key : keys) {
            crc.update(key);
            OptionalLong expireAt = expiration.expireAtMillis(key);
            crc.update(expireAt.isPresent() ? 1 : 0);
            if (expireAt.isPresent()) {
                long at = expireAt.getAsLong();
                crc.update((int) (at >>> 32));
                crc.update((int) at);
            }
            RedisValue value = store.get(key).orElseThrow();
            crc.update(value.type().ordinal());
            crc.update(typePayloadChecksum(value));
        }
        return crc.getValue();
    }

    private static int typePayloadChecksum(RedisValue value) {
        CRC32 inner = new CRC32();
        switch (value.type()) {
            case STRING -> inner.update(((com.myredis.datatype.StringRedisValue) value).bytes());
            case LIST -> {
                for (byte[] element :
                        ((com.myredis.datatype.ListRedisValue) value).snapshotElementsInOrder()) {
                    inner.update(element);
                }
            }
            case SET -> {
                for (byte[] member : ((com.myredis.datatype.SetRedisValue) value).membersSorted()) {
                    inner.update(member);
                }
            }
            case HASH -> {
                for (byte[] flat : ((com.myredis.datatype.HashRedisValue) value).getAllFlat()) {
                    inner.update(flat);
                }
            }
            case SORTED_SET -> {
                for (var member :
                        ((com.myredis.datatype.SortedSetRedisValue) value).snapshotByRank()) {
                    inner.update(member.member());
                    long bits = Double.doubleToLongBits(member.score());
                    inner.update((int) (bits >>> 32));
                    inner.update((int) bits);
                }
            }
            default -> throw new IllegalStateException("unsupported type: " + value.type());
        }
        return (int) inner.getValue();
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
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
}
