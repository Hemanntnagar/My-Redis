package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.metrics.ServerStatistics;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.server.ServerCollaborators;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** {@code INFO} — server statistics in Redis-style {@code key:value} sections. */
public final class InfoCommand implements Command {

    private final ServerCollaborators collaborators;

    public InfoCommand(ServerCollaborators collaborators) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
    }

    @Override
    public String name() {
        return "INFO";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        String section = null;
        if (arguments.size() == 1) {
            section = new String(arguments.get(0), StandardCharsets.UTF_8);
        } else if (arguments.size() > 1) {
            throw new CommandException("ERR wrong number of arguments for 'info' command");
        }
        ServerStatistics statistics = collaborators.statistics();
        String body = statistics.renderInfoSection(section == null ? "default" : section.toLowerCase(Locale.ROOT));
        return new ClientReply.BulkString(body.getBytes(StandardCharsets.UTF_8));
    }
}
