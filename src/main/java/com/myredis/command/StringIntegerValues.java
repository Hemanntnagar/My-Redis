package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.storage.KeyValueStore;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Parsing and arithmetic for Redis-style integer string values stored as {@link StringRedisValue}.
 */
final class StringIntegerValues {

    static final String NOT_INTEGER = "ERR value is not an integer or out of range";
    static final String OVERFLOW = "ERR increment or decrement would produce a value out of range";

    private StringIntegerValues() {
    }

    static ClientReply incrementBy(KeyValueStore store, byte[] key, long delta) throws CommandException {
        long[] next = {0L};
        try {
            store.merge(
                    key,
                    existing -> {
                        long current = 0L;
                        if (existing != null) {
                            if (!(existing instanceof StringRedisValue stringValue)) {
                                throw new IncrementStateException(wrongType());
                            }
                            try {
                                current = parseStrict(stringValue.bytes());
                            } catch (CommandException failure) {
                                throw new IncrementStateException(failure);
                            }
                        }
                        try {
                            next[0] = Math.addExact(current, delta);
                        } catch (ArithmeticException failure) {
                            throw new IncrementStateException(new CommandException(OVERFLOW));
                        }
                        return new StringRedisValue(
                                Long.toString(next[0]).getBytes(StandardCharsets.US_ASCII));
                    });
        } catch (IncrementStateException state) {
            throw state.failure();
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return new ClientReply.Integer(next[0]);
    }

    private static final class IncrementStateException extends RuntimeException {
        private final CommandException failure;

        private IncrementStateException(CommandException failure) {
            super(failure);
            this.failure = failure;
        }

        private CommandException failure() {
            return failure;
        }
    }

    private static long readCounter(KeyValueStore store, byte[] key) throws CommandException {
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            return 0L;
        }
        RedisValue value = stored.get();
        if (!(value instanceof StringRedisValue stringValue)) {
            throw wrongType();
        }
        return parseStrict(stringValue.bytes());
    }

    static long parseStrict(byte[] bytes) throws CommandException {
        if (bytes == null || bytes.length == 0) {
            throw notInteger();
        }
        int index = 0;
        if (bytes[0] == '+' || bytes[0] == '-') {
            if (bytes.length == 1) {
                throw notInteger();
            }
            index = 1;
        }
        for (int i = index; i < bytes.length; i++) {
            byte digit = bytes[i];
            if (digit < '0' || digit > '9') {
                throw notInteger();
            }
        }
        try {
            return Long.parseLong(new String(bytes, StandardCharsets.US_ASCII));
        } catch (NumberFormatException failure) {
            throw notInteger();
        }
    }

    static long negateIncrement(long increment) throws CommandException {
        try {
            return Math.negateExact(increment);
        } catch (ArithmeticException failure) {
            throw new CommandException(OVERFLOW);
        }
    }

    private static CommandException notInteger() {
        return new CommandException(NOT_INTEGER);
    }

    private static CommandException wrongType() {
        return new CommandException("WRONGTYPE Operation against a key holding the wrong kind of value");
    }
}
