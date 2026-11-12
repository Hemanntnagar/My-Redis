package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code DEL} — removes one or more keys; returns the number of keys removed. */
public final class DelCommand implements Command {

    private final KeyValueStore store;

    public DelCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "DEL";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "del");
        long removed = 0;
        for (byte[] key : arguments) {
            if (key == null) {
                throw new CommandException("ERR invalid key");
            }
            if (store.delete(key)) {
                removed++;
            }
        }
        return new ClientReply.Integer(removed);
    }
}
