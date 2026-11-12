package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code PTTL} — returns remaining lifetime in milliseconds for a key. */
public final class PttlCommand implements Command {

    private final KeyValueStore store;
    private final ExpirationService expiration;
    private final ExpirationClock clock;

    public PttlCommand(KeyValueStore store, ExpirationService expiration, ExpirationClock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "PTTL";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "pttl");
        byte[] key = arguments.get(0);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        return TtlCommandSupport.compute(store, expiration, clock, key, true);
    }
}
