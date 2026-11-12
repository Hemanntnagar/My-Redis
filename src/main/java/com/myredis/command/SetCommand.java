package com.myredis.command;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code SET} — stores a binary-safe string value under a key; returns {@code OK}. */
public final class SetCommand implements Command {

    private final KeyValueStore store;

    public SetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "set");
        byte[] key = arguments.get(0);
        byte[] value = arguments.get(1);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        if (value == null) {
            throw new CommandException("ERR invalid value");
        }
        try {
            store.put(key, new StringRedisValue(value));
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return ClientReply.ok();
    }
}
