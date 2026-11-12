package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code RENAMENX} — renames a key only when the destination name is free. */
public final class RenameNxCommand implements Command {

    private final KeyValueStore store;

    public RenameNxCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "RENAMENX";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "renamenx");
        byte[] source = arguments.get(0);
        byte[] destination = arguments.get(1);
        if (source == null || destination == null) {
            throw new CommandException("ERR invalid key");
        }
        try {
            boolean renamed = store.renameIfAbsent(source, destination);
            return new ClientReply.Integer(renamed ? 1L : 0L);
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
    }
}
