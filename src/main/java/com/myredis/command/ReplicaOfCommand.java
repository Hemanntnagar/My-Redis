package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.replication.ReplicaUpstreamClient;
import com.myredis.server.ServerCollaborators;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** {@code REPLICAOF} — configure this process as a replica of a primary. */
public final class ReplicaOfCommand implements Command {

    private final ServerCollaborators collaborators;
    private final java.util.function.BiFunction<String, Integer, ReplicaUpstreamClient> upstreamFactory;

    public ReplicaOfCommand(
            ServerCollaborators collaborators,
            java.util.function.BiFunction<String, Integer, ReplicaUpstreamClient> upstreamFactory) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.upstreamFactory = Objects.requireNonNull(upstreamFactory, "upstreamFactory");
    }

    @Override
    public String name() {
        return "REPLICAOF";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        if (arguments.size() != 2) {
            throw new CommandException("ERR wrong number of arguments for 'replicaof' command");
        }
        String host = new String(arguments.get(0), StandardCharsets.UTF_8);
        String portToken = new String(arguments.get(1), StandardCharsets.UTF_8);
        if ("no".equalsIgnoreCase(host) && "one".equalsIgnoreCase(portToken)) {
            collaborators.replication().configureAsMaster();
            return ClientReply.ok();
        }
        int port;
        try {
            port = Integer.parseInt(portToken.trim());
        } catch (NumberFormatException failure) {
            throw new CommandException("ERR invalid REPLICAOF port");
        }
        collaborators.replication().configureAsReplica(host, port);
        ReplicaUpstreamClient client = upstreamFactory.apply(host, port);
        client.start();
        return ClientReply.ok();
    }
}
