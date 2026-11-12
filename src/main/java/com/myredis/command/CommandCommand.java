package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** {@code COMMAND} — lists registered command names. */
public final class CommandCommand implements Command {

    private final Map<String, Command> commands;

    public CommandCommand(Map<String, Command> commands) {
        this.commands = Map.copyOf(Objects.requireNonNull(commands, "commands"));
    }

    @Override
    public String name() {
        return "COMMAND";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        if (!arguments.isEmpty()) {
            throw new CommandException("ERR wrong number of arguments for 'command' command");
        }
        List<ClientReply> entries = new ArrayList<>();
        for (String name : commands.keySet().stream().sorted().toList()) {
            entries.add(commandInfo(name));
        }
        return new ClientReply.CompositeArray(entries);
    }

    private static ClientReply commandInfo(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return new ClientReply.CompositeArray(
                List.of(
                        new ClientReply.BulkString(upper.getBytes(StandardCharsets.UTF_8)),
                        new ClientReply.Integer(0),
                        new ClientReply.CompositeArray(List.of()),
                        new ClientReply.Integer(0),
                        new ClientReply.Integer(0),
                        new ClientReply.CompositeArray(List.of())));
    }
}
