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

/** {@code STRLEN} — returns the length of the string value stored at a key. */
public final class StrlenCommand implements Command {

    private final KeyValueStore store;

    public StrlenCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "STRLEN";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "strlen");
        byte[] key = arguments.get(0);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            return new ClientReply.Integer(0L);
        }
        RedisValue value = stored.get();
        if (!(value instanceof StringRedisValue stringValue)) {
            throw new CommandException(
                    "WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return new ClientReply.Integer(stringValue.bytes().length);
    }
}
