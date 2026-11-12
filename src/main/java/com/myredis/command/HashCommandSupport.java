package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.storage.KeyValueStore;

import java.util.Optional;

final class HashCommandSupport {

    static final String WRONGTYPE = ListCommandSupport.WRONGTYPE;

    private HashCommandSupport() {
    }

    static void requireFieldValuePairs(int argumentCount, String commandName) throws CommandException {
        if (argumentCount < 3 || (argumentCount - 1) % 2 != 0) {
            throw new CommandException("ERR wrong number of arguments for '" + commandName + "' command");
        }
    }

    static HashRedisValue optionalHash(Optional<RedisValue> stored) throws CommandException {
        if (stored.isEmpty()) {
            return null;
        }
        return requireHash(stored.get());
    }

    static HashRedisValue requireHash(RedisValue value) throws CommandException {
        if (value instanceof HashRedisValue hash) {
            return hash;
        }
        throw new CommandException(WRONGTYPE);
    }

    static long setFields(KeyValueStore store, byte[] key, byte[][] fieldValuePairs) throws CommandException {
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            HashRedisValue hash = new HashRedisValue();
            long added;
            synchronized (hash) {
                added = hash.setMany(fieldValuePairs);
            }
            put(store, key, hash);
            return added;
        }
        HashRedisValue hash = requireHash(stored.get());
        synchronized (hash) {
            long added = hash.setMany(fieldValuePairs);
            put(store, key, hash);
            return added;
        }
    }

    static void put(KeyValueStore store, byte[] key, HashRedisValue hash) throws CommandException {
        try {
            store.put(key, hash);
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
    }
}
