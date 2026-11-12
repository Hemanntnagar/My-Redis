package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientRequestProcessor;
import com.myredis.network.ClientSession;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationService;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import com.myredis.storage.KeyValueStore;

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
        return withBuiltinCommands(new InMemoryKeyValueStore());
    }

    public static CommandClientRequestProcessor withBuiltinCommands(KeyValueStore store) {
        return new CommandClientRequestProcessor(RegistryCommandDispatcher.withBuiltinCommands(store));
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            KeyValueStore store, ExpirationService expiration, ExpirationClock clock) {
        return new CommandClientRequestProcessor(RegistryCommandDispatcher.withBuiltinCommands(store, expiration, clock));
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            com.myredis.persistence.SnapshotPersistenceManager persistence,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock) {
        return new CommandClientRequestProcessor(
                RegistryCommandDispatcher.withBuiltinCommands(store, expiration, clock, persistence));
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
