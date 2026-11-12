package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.SnapshotCommandSupport;

import java.util.List;
import java.util.Objects;

/** {@code BGSAVE} — spawns a background thread to write the snapshot. */
public final class BgsaveCommand implements Command {

    private final SnapshotCommandSupport persistence;

    public BgsaveCommand(SnapshotCommandSupport persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public String name() {
        return "BGSAVE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "bgsave");
        if (!persistence.beginBackgroundSave()) {
            throw new CommandException("ERR Background save already in progress");
        }
        return ClientReply.ok();
    }
}
