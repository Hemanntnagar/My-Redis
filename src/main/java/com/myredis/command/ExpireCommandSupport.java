package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.storage.KeyValueStore;

final class ExpireCommandSupport {

    enum ExpireMode {
        RELATIVE_SECONDS,
        RELATIVE_MILLIS,
        ABSOLUTE_SECONDS,
        ABSOLUTE_MILLIS
    }

    private ExpireCommandSupport() {}

    static ClientReply setExpiration(
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock,
            byte[] key,
            byte[] timeArgument,
            ExpireMode mode) throws CommandException {
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        if (timeArgument == null) {
            throw new CommandException("ERR invalid expire time");
        }
        if (!store.exists(key)) {
            return new ClientReply.Integer(0L);
        }
        long time = StringIntegerValues.parseStrict(timeArgument);
        long now = clock.currentTimeMillis();
        long expireAt = resolveExpireAt(now, time, mode);
        if (expireAt <= now) {
            store.delete(key);
            expiration.cancel(key);
            return new ClientReply.Integer(1L);
        }
        expiration.register(key, expireAt);
        return new ClientReply.Integer(1L);
    }

    static ClientReply persist(
            KeyValueStore store, ExpirationService expiration, byte[] key) throws CommandException {
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        if (!store.exists(key)) {
            return new ClientReply.Integer(0L);
        }
        if (expiration.findMetadata(key).isEmpty()) {
            return new ClientReply.Integer(0L);
        }
        expiration.cancel(key);
        return new ClientReply.Integer(1L);
    }

    private static long resolveExpireAt(long nowEpochMillis, long time, ExpireMode mode) throws CommandException {
        try {
            return switch (mode) {
                case RELATIVE_SECONDS -> Math.addExact(nowEpochMillis, Math.multiplyExact(time, 1_000L));
                case RELATIVE_MILLIS -> Math.addExact(nowEpochMillis, time);
                case ABSOLUTE_SECONDS -> Math.multiplyExact(time, 1_000L);
                case ABSOLUTE_MILLIS -> time;
            };
        } catch (ArithmeticException failure) {
            throw new CommandException(StringIntegerValues.NOT_INTEGER);
        }
    }
}
