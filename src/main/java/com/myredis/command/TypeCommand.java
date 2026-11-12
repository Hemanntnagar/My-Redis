package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@code TYPE} — returns the type of the value at a key, or {@code none} when absent. */
public final class TypeCommand implements Command {

    private final KeyValueStore store;

    public TypeCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "TYPE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "type");
        byte[] key = arguments.get(0);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            return new ClientReply.SimpleString("none");
        }
        return new ClientReply.SimpleString(stored.get().type().redisTypeName());
    }
}
