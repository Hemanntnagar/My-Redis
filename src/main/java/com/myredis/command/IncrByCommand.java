package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code INCRBY} — increments the integer value of a key by the given amount. */
public final class IncrByCommand implements Command {

    private final KeyValueStore store;

    public IncrByCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "INCRBY";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "incrby");
        byte[] key = arguments.get(0);
        byte[] increment = arguments.get(1);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        if (increment == null) {
            throw new CommandException("ERR invalid increment");
        }
        long delta = StringIntegerValues.parseStrict(increment);
        return StringIntegerValues.incrementBy(store, key, delta);
    }
}
