package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class SInterCommand implements Command {

    private final KeyValueStore store;

    public SInterCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SINTER";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "sinter");
        SetValueCommandSupport.requireKeys(arguments);
        validateSetKeys(store, arguments);
        return new ClientReply.Array(SetValueCommandSupport.intersection(store, arguments));
    }

    static void validateSetKeys(KeyValueStore store, List<byte[]> keys) throws CommandException {
        for (byte[] key : keys) {
            var stored = store.get(key);
            if (stored.isPresent()) {
                SetValueCommandSupport.requireSet(stored.get());
            }
        }
    }
}
