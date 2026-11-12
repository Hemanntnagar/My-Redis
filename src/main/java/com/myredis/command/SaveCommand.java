package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.exception.PersistenceException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.SnapshotCommandSupport;

import java.util.List;
import java.util.Objects;

/** {@code SAVE} — writes the current database snapshot to disk and blocks until complete. */
public final class SaveCommand implements Command {

    private final SnapshotCommandSupport persistence;

    public SaveCommand(SnapshotCommandSupport persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public String name() {
        return "SAVE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "save");
        try {
            persistence.saveBlocking();
        } catch (PersistenceException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return ClientReply.ok();
    }
}
