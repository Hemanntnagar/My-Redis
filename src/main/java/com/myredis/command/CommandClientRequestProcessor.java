package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientRequestProcessor;
import com.myredis.network.ClientSession;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Turns decoded RESP command arrays into {@link CommandDispatcher} invocations.
 */
public final class CommandClientRequestProcessor implements ClientRequestProcessor {

    private final CommandDispatcher dispatcher;

    public CommandClientRequestProcessor(CommandDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public static CommandClientRequestProcessor withBuiltinCommands() {
        return new CommandClientRequestProcessor(RegistryCommandDispatcher.withBuiltinCommands());
    }

    @Override
    public ClientReply process(ClientSession session, List<byte[]> commandParts) {
        if (commandParts.isEmpty()) {
            return ClientReply.error("ERR empty command");
        }
        String commandName = new String(commandParts.getFirst(), StandardCharsets.UTF_8);
        List<byte[]> arguments = commandParts.size() == 1 ? List.of() : commandParts.subList(1, commandParts.size());
        try {
            return dispatcher.dispatch(session, commandName, arguments);
        } catch (CommandException failure) {
            return ClientReply.error(failure.clientMessage());
        }
    }
}
