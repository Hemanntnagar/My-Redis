package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientConnectionManager;
import com.myredis.network.ConnectedClientSession;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.server.ServerCollaborators;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** {@code CLIENT LIST} — active connection summary. */
public final class ClientListCommand implements Command {

    private final ServerCollaborators collaborators;

    public ClientListCommand(ServerCollaborators collaborators) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
    }

    @Override
    public String name() {
        return "CLIENT";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        if (arguments.isEmpty()) {
            throw new CommandException("ERR wrong number of arguments for 'client' command");
        }
        String sub = new String(arguments.get(0), StandardCharsets.UTF_8);
        if ("LIST".equalsIgnoreCase(sub)) {
            return listClients();
        }
        if ("INFO".equalsIgnoreCase(sub)) {
            return clientInfo(session);
        }
        throw new CommandException("ERR unknown CLIENT subcommand '" + sub + "'");
    }

    private ClientReply listClients() {
        ClientConnectionManager connections = collaborators.connections();
        if (connections == null) {
            return new ClientReply.BulkString(new byte[0]);
        }
        StringBuilder builder = new StringBuilder();
        for (ConnectedClientSession client : connections.liveSessions()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(client.describeForClientList());
        }
        return new ClientReply.BulkString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ClientReply clientInfo(ClientSession session) {
        if (!(session instanceof ConnectedClientSession connected)) {
            return new ClientReply.BulkString(("id=" + session.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new ClientReply.BulkString(connected.describeForClientList().getBytes(StandardCharsets.UTF_8));
    }
}
