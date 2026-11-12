package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code DECRBY} — decrements the integer value of a key by the given amount. */
public final class DecrByCommand implements Command {

    private final KeyValueStore store;

    public DecrByCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "DECRBY";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "decrby");
        byte[] key = arguments.get(0);
        byte[] decrement = arguments.get(1);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        if (decrement == null) {
            throw new CommandException("ERR invalid decrement");
        }
        long amount = StringIntegerValues.parseStrict(decrement);
        long delta = StringIntegerValues.negateIncrement(amount);
        return StringIntegerValues.incrementBy(store, key, delta);
    }
}
