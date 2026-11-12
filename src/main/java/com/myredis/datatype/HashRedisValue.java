package com.myredis.datatype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Binary-safe Redis hash: field names map to values in insertion-stable storage.
 */
public final class HashRedisValue implements RedisValue {

    private final Map<Field, byte[]> fields = new HashMap<>();

    @Override
    public DataType type() {
        return DataType.HASH;
    }

    public synchronized long set(byte[] field, byte[] value) {
        return fields.put(new Field(field), value.clone()) == null ? 1L : 0L;
    }

    public synchronized long setMany(byte[][] fieldValuePairs) {
        long added = 0;
        for (int index = 0; index < fieldValuePairs.length; index += 2) {
            added += set(fieldValuePairs[index], fieldValuePairs[index + 1]);
        }
        return added;
    }

    public synchronized Optional<byte[]> get(byte[] field) {
        byte[] value = fields.get(new Field(field));
        return value == null ? Optional.empty() : Optional.of(value.clone());
    }

    public synchronized long delete(byte[]... fieldNames) {
        long removed = 0;
        for (byte[] field : fieldNames) {
            if (fields.remove(new Field(field)) != null) {
                removed++;
            }
        }
        return removed;
    }

    public synchronized boolean exists(byte[] field) {
        return fields.containsKey(new Field(field));
    }

    public synchronized long size() {
        return fields.size();
    }

    public synchronized List<byte[]> keysSorted() {
        List<byte[]> keys = new ArrayList<>(fields.size());
        for (Field field : fields.keySet()) {
            keys.add(field.bytes().clone());
        }
        keys.sort(Arrays::compareUnsigned);
        return keys;
    }

    public synchronized List<byte[]> valuesSortedByKey() {
        List<byte[]> keys = keysSorted();
        List<byte[]> values = new ArrayList<>(keys.size());
        for (byte[] key : keys) {
            values.add(fields.get(new Field(key)).clone());
        }
        return values;
    }

    public synchronized List<byte[]> getAllFlat() {
        List<byte[]> flat = new ArrayList<>(fields.size() * 2);
        for (byte[] key : keysSorted()) {
            flat.add(key);
            flat.add(fields.get(new Field(key)).clone());
        }
        return flat;
    }

    private record Field(byte[] bytes) {

        private Field {
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Field that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
