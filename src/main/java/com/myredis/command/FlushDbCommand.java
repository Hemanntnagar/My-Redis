package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code FLUSHDB} — deletes all keys in the active logical database. */
public final class FlushDbCommand implements Command {

    private final KeyValueStore store;

    public FlushDbCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "FLUSHDB";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "flushdb");
        store.clear();
        return ClientReply.ok();
    }
}
