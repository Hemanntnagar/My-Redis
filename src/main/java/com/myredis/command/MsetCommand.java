package com.myredis.command;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code MSET} — atomically sets multiple key-value pairs; returns {@code OK}. */
public final class MsetCommand implements Command {

    private final KeyValueStore store;

    public MsetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "MSET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireEven(arguments, 2, "mset");
        for (int i = 0; i < arguments.size(); i += 2) {
            byte[] key = arguments.get(i);
            byte[] value = arguments.get(i + 1);
            if (key == null) {
                throw new CommandException("ERR invalid key");
            }
            if (value == null) {
                throw new CommandException("ERR invalid value");
            }
        }
        try {
            for (int i = 0; i < arguments.size(); i += 2) {
                store.put(arguments.get(i), new StringRedisValue(arguments.get(i + 1)));
            }
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return ClientReply.ok();
    }
}
