package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code RENAME} — renames a key, overwriting the destination when it already exists. */
public final class RenameCommand implements Command {

    private final KeyValueStore store;

    public RenameCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "RENAME";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "rename");
        byte[] source = arguments.get(0);
        byte[] destination = arguments.get(1);
        if (source == null || destination == null) {
            throw new CommandException("ERR invalid key");
        }
        try {
            store.rename(source, destination);
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return ClientReply.ok();
    }
}
