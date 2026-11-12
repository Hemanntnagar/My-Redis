package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@code GET} — returns the string value for a key, or null bulk when the key is absent. */
public final class GetCommand implements Command {

    private final KeyValueStore store;

    public GetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "GET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "get");
        byte[] key = arguments.get(0);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            return new ClientReply.NullBulk();
        }
        RedisValue value = stored.get();
        if (!(value instanceof StringRedisValue stringValue)) {
            throw new CommandException(
                    "WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return new ClientReply.BulkString(stringValue.bytes());
    }
}
