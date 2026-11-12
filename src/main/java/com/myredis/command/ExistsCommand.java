package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code EXISTS} — counts how many of the given keys are present. */
public final class ExistsCommand implements Command {

    private final KeyValueStore store;

    public ExistsCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "EXISTS";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "exists");
        long count = 0;
        for (byte[] key : arguments) {
            if (key == null) {
                throw new CommandException("ERR invalid key");
            }
            if (store.exists(key)) {
                count++;
            }
        }
        return new ClientReply.Integer(count);
    }
}
