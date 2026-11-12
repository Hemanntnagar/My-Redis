package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.network.ConnectedClientSession;
import com.myredis.replication.ReplicationSyncResult;
import com.myredis.server.ServerCollaborators;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** {@code REPLSYNC} — primary/replica synchronization handshake. */
public final class ReplSyncCommand implements Command {

    private final ServerCollaborators collaborators;

    public ReplSyncCommand(ServerCollaborators collaborators) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
    }

    @Override
    public String name() {
        return "REPLSYNC";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        if (!(session instanceof ConnectedClientSession connected)) {
            throw new CommandException("ERR replication requires a live connection");
        }
        long replicaOffset = 0;
        if (arguments.size() == 1) {
            try {
                replicaOffset = Long.parseLong(new String(arguments.get(0), StandardCharsets.UTF_8).trim());
            } catch (NumberFormatException failure) {
                throw new CommandException("ERR invalid replication offset");
            }
        } else if (arguments.size() > 1) {
            throw new CommandException("ERR wrong number of arguments for 'replsync' command");
        }
        ReplicationSyncResult result =
                collaborators.replication().synchronizeReplica(connected, replicaOffset);
        long masterOffset =
                switch (result) {
                    case ReplicationSyncResult.FullSync full -> full.masterOffset();
                    case ReplicationSyncResult.PartialSync partial -> partial.masterOffset();
                    case ReplicationSyncResult.AlreadySynced synced -> synced.masterOffset();
                };
        return new ClientReply.SimpleString("OK " + masterOffset);
    }
}
