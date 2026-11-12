package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.InMemoryKeyValueStore;
import com.myredis.storage.KeyValueStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Dispatches by command name to registered {@link Command} implementations.
 */
public final class RegistryCommandDispatcher implements CommandDispatcher {

    private final Map<String, Command> commands;

    public RegistryCommandDispatcher(Map<String, Command> commands) {
        this.commands = Map.copyOf(commands);
    }

    public static RegistryCommandDispatcher withBuiltinCommands() {
        return withBuiltinCommands(new InMemoryKeyValueStore());
    }

    public static RegistryCommandDispatcher withBuiltinCommands(KeyValueStore store) {
        Map<String, Command> registry = new LinkedHashMap<>();
        register(registry, new PingCommand());
        register(registry, new EchoCommand());
        register(registry, new SetCommand(store));
        register(registry, new GetCommand(store));
        register(registry, new DelCommand(store));
        register(registry, new ExistsCommand(store));
        register(registry, new TypeCommand(store));
        register(registry, new MsetCommand(store));
        register(registry, new MgetCommand(store));
        register(registry, new AppendCommand(store));
        return new RegistryCommandDispatcher(registry);
    }

    private static void register(Map<String, Command> registry, Command command) {
        registry.put(command.name().toUpperCase(Locale.ROOT), command);
    }

    @Override
    public ClientReply dispatch(ClientSession session, String commandName, List<byte[]> arguments)
            throws CommandException {
        Objects.requireNonNull(commandName, "commandName");
        Command command = commands.get(commandName.toUpperCase(Locale.ROOT));
        if (command == null) {
            throw new CommandException("ERR unknown command '" + commandName + "'");
        }
        return command.execute(session, arguments);
    }
}
