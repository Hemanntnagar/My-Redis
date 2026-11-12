package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code EXPIRE} — sets a timeout in seconds on a key. */
public final class ExpireCommand implements Command {

    private final KeyValueStore store;
    private final ExpirationService expiration;
    private final ExpirationClock clock;

    public ExpireCommand(KeyValueStore store, ExpirationService expiration, ExpirationClock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "EXPIRE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "expire");
        return ExpireCommandSupport.setExpiration(
                store,
                expiration,
                clock,
                arguments.get(0),
                arguments.get(1),
                ExpireCommandSupport.ExpireMode.RELATIVE_SECONDS);
    }
}
