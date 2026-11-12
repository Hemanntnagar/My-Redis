package com.myredis.datatype;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Binary-safe Redis list backed by a doubly-ended queue for O(1) head/tail pushes and pops.
 */
public final class ListRedisValue implements RedisValue {

    private final ArrayDeque<byte[]> elements;

    public ListRedisValue() {
        this.elements = new ArrayDeque<>();
    }

    @Override
    public DataType type() {
        return DataType.LIST;
    }

    public synchronized long pushHead(byte[]... values) {
        Objects.requireNonNull(values, "values");
        for (byte[] value : values) {
            elements.addFirst(clone(value));
        }
        return elements.size();
    }

    public synchronized long pushTail(byte[]... values) {
        Objects.requireNonNull(values, "values");
        for (byte[] value : values) {
            elements.addLast(clone(value));
        }
        return elements.size();
    }

    public synchronized Optional<byte[]> popHead() {
        byte[] value = elements.pollFirst();
        return value == null ? Optional.empty() : Optional.of(value);
    }

    public synchronized Optional<byte[]> popTail() {
        byte[] value = elements.pollLast();
        return value == null ? Optional.empty() : Optional.of(value);
    }

    public synchronized long length() {
        return elements.size();
    }

    /** Head-to-tail copy for snapshot persistence. */
    public synchronized List<byte[]> snapshotElementsInOrder() {
        List<byte[]> ordered = new ArrayList<>(elements.size());
        for (byte[] element : elements) {
            ordered.add(element.clone());
        }
        return ordered;
    }

    public synchronized List<byte[]> rangeInclusive(int start, int stop) {
        int size = elements.size();
        if (size == 0 || start > stop || start >= size) {
            return List.of();
        }
        if (stop >= size) {
            stop = size - 1;
        }
        List<byte[]> slice = new ArrayList<>(stop - start + 1);
        int index = 0;
        for (byte[] element : elements) {
            if (index > stop) {
                break;
            }
            if (index >= start) {
                slice.add(element.clone());
            }
            index++;
        }
        return slice;
    }

    public synchronized Optional<byte[]> elementAt(int index) {
        if (index < 0 || index >= elements.size()) {
            return Optional.empty();
        }
        int position = 0;
        for (byte[] element : elements) {
            if (position == index) {
                return Optional.of(element.clone());
            }
            position++;
        }
        return Optional.empty();
    }

    public synchronized boolean setAt(int index, byte[] value) {
        if (index < 0 || index >= elements.size()) {
            return false;
        }
        ArrayDeque<byte[]> rebuilt = new ArrayDeque<>(elements.size());
        int position = 0;
        for (byte[] element : elements) {
            if (position == index) {
                rebuilt.addLast(clone(value));
            } else {
                rebuilt.addLast(element);
            }
            position++;
        }
        elements.clear();
        elements.addAll(rebuilt);
        return true;
    }

    private static byte[] clone(byte[] value) {
        Objects.requireNonNull(value, "value");
        return value.clone();
    }

}
