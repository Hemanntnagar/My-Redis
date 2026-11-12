package com.myredis.command;

import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationReplies;
import com.myredis.expiration.ExpirationService;
import com.myredis.expiration.KeyExpirationMetadata;
import com.myredis.network.ClientReply;
import com.myredis.storage.KeyValueStore;

final class TtlCommandSupport {

    private TtlCommandSupport() {}

    static ClientReply compute(
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock,
            byte[] key,
            boolean millisecondPrecision) {
        if (!store.exists(key)) {
            return new ClientReply.Integer(ExpirationReplies.KEY_MISSING);
        }
        long now = clock.currentTimeMillis();
        if (expiration.isExpired(key, now)) {
            store.delete(key);
            expiration.cancel(key);
            return new ClientReply.Integer(ExpirationReplies.KEY_MISSING);
        }
        KeyExpirationMetadata metadata =
                expiration.findMetadata(key).orElse(null);
        if (metadata == null) {
            return new ClientReply.Integer(ExpirationReplies.NO_EXPIRE);
        }
        long reply =
                millisecondPrecision ? metadata.remainingMillisAt(now) : metadata.remainingSecondsAt(now);
        return new ClientReply.Integer(reply);
    }
}
