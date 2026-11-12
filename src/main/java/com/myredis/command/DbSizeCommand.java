package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code DBSIZE} — returns the number of keys in the active logical database. */
public final class DbSizeCommand implements Command {

    private final KeyValueStore store;

    public DbSizeCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "DBSIZE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "dbsize");
        return new ClientReply.Integer(store.keyCount());
    }
}
