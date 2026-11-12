package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@code MGET} — returns values for multiple keys in argument order. */
public final class MgetCommand implements Command {

    private final KeyValueStore store;

    public MgetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "MGET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "mget");
        List<byte[]> values = new ArrayList<>(arguments.size());
        for (byte[] key : arguments) {
            if (key == null) {
                throw new CommandException("ERR invalid key");
            }
            Optional<RedisValue> stored = store.get(key);
            if (stored.isEmpty()) {
                values.add(null);
                continue;
            }
            RedisValue value = stored.get();
            if (!(value instanceof StringRedisValue stringValue)) {
                throw new CommandException(
                        "WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            values.add(stringValue.bytes());
        }
        return new ClientReply.Array(values);
    }
}
