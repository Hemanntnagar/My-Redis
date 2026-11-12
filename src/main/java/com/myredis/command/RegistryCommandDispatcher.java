package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationService;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;
import com.myredis.storage.ExpirationAwareKeyValueStore;
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
        if (store instanceof ExpirationAwareKeyValueStore aware) {
            return withBuiltinCommands(store, aware.expirationService(), SystemExpirationClock.INSTANCE);
        }
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        KeyValueStore awareStore = new ExpirationAwareKeyValueStore(store, expiration, SystemExpirationClock.INSTANCE);
        return withBuiltinCommands(awareStore, expiration, SystemExpirationClock.INSTANCE);
    }

    public static RegistryCommandDispatcher withBuiltinCommands(
            KeyValueStore store, ExpirationService expiration, ExpirationClock clock) {
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
        register(registry, new IncrCommand(store));
        register(registry, new DecrCommand(store));
        register(registry, new IncrByCommand(store));
        register(registry, new DecrByCommand(store));
        register(registry, new StrlenCommand(store));
        register(registry, new KeysCommand(store));
        register(registry, new DbSizeCommand(store));
        register(registry, new FlushDbCommand(store));
        register(registry, new RenameCommand(store));
        register(registry, new RenameNxCommand(store));
        register(registry, new RandomKeyCommand(store));
        register(registry, new LPushCommand(store));
        register(registry, new RPushCommand(store));
        register(registry, new LPopCommand(store));
        register(registry, new RPopCommand(store));
        register(registry, new LLenCommand(store));
        register(registry, new LRangeCommand(store));
        register(registry, new LIndexCommand(store));
        register(registry, new LSetCommand(store));
        register(registry, new SAddCommand(store));
        register(registry, new SRemCommand(store));
        register(registry, new SMembersCommand(store));
        register(registry, new SIsMemberCommand(store));
        register(registry, new SCardCommand(store));
        register(registry, new SInterCommand(store));
        register(registry, new SUnionCommand(store));
        register(registry, new SDiffCommand(store));
        register(registry, new HSetCommand(store));
        register(registry, new HGetCommand(store));
        register(registry, new HMGetCommand(store));
        register(registry, new HGetAllCommand(store));
        register(registry, new HDelCommand(store));
        register(registry, new HExistsCommand(store));
        register(registry, new HLenCommand(store));
        register(registry, new HKeysCommand(store));
        register(registry, new HValsCommand(store));
        register(registry, new ZAddCommand(store));
        register(registry, new ZRemCommand(store));
        register(registry, new ZScoreCommand(store));
        register(registry, new ZRankCommand(store));
        register(registry, new ZRevRankCommand(store));
        register(registry, new ZRangeCommand(store));
        register(registry, new ZCardCommand(store));
        register(registry, new TtlCommand(store, expiration, clock));
        register(registry, new PttlCommand(store, expiration, clock));
        register(registry, new ExpireCommand(store, expiration, clock));
        register(registry, new PexpireCommand(store, expiration, clock));
        register(registry, new ExpireAtCommand(store, expiration, clock));
        register(registry, new PexpireAtCommand(store, expiration, clock));
        register(registry, new PersistCommand(store, expiration));
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
