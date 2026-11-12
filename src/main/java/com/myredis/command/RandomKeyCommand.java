package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** {@code RANDOMKEY} — returns a random key from the active logical database. */
public final class RandomKeyCommand implements Command {

    private final KeyValueStore store;

    public RandomKeyCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "RANDOMKEY";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "randomkey");
        List<byte[]> keys = store.snapshotKeys();
        if (keys.isEmpty()) {
            return new ClientReply.NullBulk();
        }
        byte[] chosen = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        return new ClientReply.BulkString(chosen.clone());
    }
}
