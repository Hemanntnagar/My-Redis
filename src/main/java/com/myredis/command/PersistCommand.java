package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.expiration.ExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code PERSIST} — removes the timeout on a key. */
public final class PersistCommand implements Command {

    private final KeyValueStore store;
    private final ExpirationService expiration;

    public PersistCommand(KeyValueStore store, ExpirationService expiration) {
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
    }

    @Override
    public String name() {
        return "PERSIST";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "persist");
        return ExpireCommandSupport.persist(store, expiration, arguments.get(0));
    }
}
