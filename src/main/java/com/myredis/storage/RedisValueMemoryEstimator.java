package com.myredis.storage;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.datatype.StringRedisValue;

import java.util.List;

/**
 * Approximate in-memory footprint for eviction accounting (not RSS-precise).
 */
public final class RedisValueMemoryEstimator {

    private static final int ENTRY_OVERHEAD = 64;

    private RedisValueMemoryEstimator() {}

    public static long estimateKeyAndValue(byte[] key, RedisValue value) {
        int keyBytes = key == null ? 0 : key.length;
        return ENTRY_OVERHEAD + keyBytes + estimateValue(value);
    }

    public static long estimateValue(RedisValue value) {
        if (value == null) {
            return 0;
        }
        return switch (value.type()) {
            case STRING -> ((StringRedisValue) value).bytes().length + 32;
            case LIST -> listBytes((ListRedisValue) value);
            case SET -> setBytes((SetRedisValue) value);
            case HASH -> hashBytes((HashRedisValue) value);
            case SORTED_SET -> zsetBytes((SortedSetRedisValue) value);
            case STREAM -> 128;
        };
    }

    private static long listBytes(ListRedisValue value) {
        long total = 48;
        for (byte[] element : value.snapshotElementsInOrder()) {
            total += element.length + 16;
        }
        return total;
    }

    private static long setBytes(SetRedisValue value) {
        long total = 48;
        for (byte[] member : value.snapshotMembers()) {
            total += member.length + 16;
        }
        return total;
    }

    private static long hashBytes(HashRedisValue value) {
        long total = 48;
        List<byte[]> flat = value.getAllFlat();
        for (int i = 0; i < flat.size(); i += 2) {
            total += flat.get(i).length + flat.get(i + 1).length + 24;
        }
        return total;
    }

    private static long zsetBytes(SortedSetRedisValue value) {
        long total = 64;
        for (SortedSetRedisValue.ScoredMemberSnapshot entry : value.snapshotByRank()) {
            total += entry.member().length + 24;
        }
        return total;
    }
}
