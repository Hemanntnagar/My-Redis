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

import com.myredis.persistence.aof.AppendOnlyFile;
import com.myredis.persistence.aof.WriteCommandClassifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns decoded RESP command arrays into {@link CommandDispatcher} invocations.
 */
public final class CommandClientRequestProcessor implements ClientRequestProcessor {

    private final CommandDispatcher dispatcher;
    private final AppendOnlyFile appendOnlyFile;

    public CommandClientRequestProcessor(CommandDispatcher dispatcher) {
        this(dispatcher, null);
    }

    public CommandClientRequestProcessor(CommandDispatcher dispatcher, AppendOnlyFile appendOnlyFile) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.appendOnlyFile = appendOnlyFile;
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
            com.myredis.persistence.SnapshotCommandSupport persistence,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock) {
        return withBuiltinCommands(persistence, null, store, expiration, clock);
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            com.myredis.persistence.SnapshotCommandSupport persistence,
            AppendOnlyFile appendOnlyFile,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock) {
        return new CommandClientRequestProcessor(
                RegistryCommandDispatcher.withBuiltinCommands(store, expiration, clock, persistence, appendOnlyFile),
                appendOnlyFile);
    }

    @Override
    public ClientReply process(ClientSession session, List<byte[]> commandParts) {
        if (commandParts.isEmpty()) {
            return ClientReply.error("ERR empty command");
        }
        String commandName = new String(commandParts.getFirst(), StandardCharsets.UTF_8);
        List<byte[]> arguments = commandParts.size() == 1 ? List.of() : commandParts.subList(1, commandParts.size());
        try {
            ClientReply reply = dispatcher.dispatch(session, commandName, arguments);
            appendSuccessfulWrite(commandName, commandParts, reply);
            return reply;
        } catch (CommandException failure) {
            return ClientReply.error(failure.clientMessage());
        }
    }

    private void appendSuccessfulWrite(String commandName, List<byte[]> commandParts, ClientReply reply) {
        if (appendOnlyFile == null || reply instanceof ClientReply.Error) {
            return;
        }
        if (!WriteCommandClassifier.isWriteCommand(commandName)) {
            return;
        }
        try {
            appendOnlyFile.append(commandParts);
        } catch (com.myredis.exception.PersistenceException failure) {
            System.err.println(
                    "AOF append failed for "
                            + commandName.toUpperCase(Locale.ROOT)
                            + ": "
                            + failure.clientMessage());
        }
    }
}
