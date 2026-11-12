package com.myredis.command;

import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.storage.KeyValueStore;

import java.util.Optional;

final class ListCommandSupport {

    static final String WRONGTYPE = "WRONGTYPE Operation against a key holding the wrong kind of value";
    static final String NO_SUCH_KEY = "ERR no such key";
    static final String INDEX_OUT_OF_RANGE = "ERR index out of range";

    private ListCommandSupport() {
    }

    static long pushHead(KeyValueStore store, byte[] key, byte[][] elements) throws CommandException {
        return push(store, key, elements, true);
    }

    static long pushTail(KeyValueStore store, byte[] key, byte[][] elements) throws CommandException {
        return push(store, key, elements, false);
    }

    private static long push(KeyValueStore store, byte[] key, byte[][] elements, boolean head) throws CommandException {
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            ListRedisValue list = new ListRedisValue();
            long length = head ? list.pushHead(elements) : list.pushTail(elements);
            try {
                store.put(key, list);
            } catch (StorageException failure) {
                throw new CommandException(failure.clientMessage());
            }
            return length;
        }
        return mutateList(stored.get(), list -> head ? list.pushHead(elements) : list.pushTail(elements));
    }

    static Optional<byte[]> popHead(ListRedisValue list) {
        synchronized (list) {
            return list.popHead();
        }
    }

    static Optional<byte[]> popTail(ListRedisValue list) {
        synchronized (list) {
            return list.popTail();
        }
    }

    static long length(ListRedisValue list) {
        synchronized (list) {
            return list.length();
        }
    }

    static ListRedisValue requireExistingList(Optional<RedisValue> stored) throws CommandException {
        if (stored.isEmpty()) {
            throw new CommandException(NO_SUCH_KEY);
        }
        return requireList(stored.get());
    }

    static ListRedisValue requireList(RedisValue value) throws CommandException {
        if (value instanceof ListRedisValue list) {
            return list;
        }
        throw new CommandException(WRONGTYPE);
    }

    static ListRedisValue optionalList(Optional<RedisValue> stored) throws CommandException {
        if (stored.isEmpty()) {
            return null;
        }
        return requireList(stored.get());
    }

    static int normalizeIndex(long index, int size) {
        long resolved = index;
        if (resolved < 0) {
            resolved = size + resolved;
        }
        if (resolved < 0 || resolved >= size) {
            return -1;
        }
        return (int) resolved;
    }

    static int[] normalizeRange(long start, long stop, int size) {
        long from = start;
        long to = stop;
        if (from < 0) {
            from = size + from;
        }
        if (to < 0) {
            to = size + to;
        }
        if (from < 0) {
            from = 0;
        }
        if (to >= size) {
            to = size - 1;
        }
        if (size == 0 || from > to || from >= size) {
            return new int[] {-1, -1};
        }
        return new int[] {(int) from, (int) to};
    }

    static <T> T mutateList(RedisValue value, ListMutation<T> mutation) throws CommandException {
        ListRedisValue list = requireList(value);
        synchronized (list) {
            return mutation.apply(list);
        }
    }

    @FunctionalInterface
    interface ListMutation<T> {
        T apply(ListRedisValue list);
    }
}
