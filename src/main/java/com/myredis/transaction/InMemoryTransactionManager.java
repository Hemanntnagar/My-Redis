package com.myredis.transaction;

import com.myredis.command.CommandExecutor;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-client {@code MULTI}/{@code EXEC} queues with {@code WATCH}-based optimistic aborts.
 */
public final class InMemoryTransactionManager implements TransactionManager {

    private final WatchRegistry watchRegistry;
    private final Map<String, SessionTransaction> transactions = new ConcurrentHashMap<>();
    private final ReentrantLock execLock = new ReentrantLock();

    public InMemoryTransactionManager(WatchRegistry watchRegistry) {
        this.watchRegistry = Objects.requireNonNull(watchRegistry, "watchRegistry");
    }

    @Override
    public void multi(ClientSession session) throws CommandException {
        SessionTransaction state = transactions.computeIfAbsent(session.id(), ignored -> new SessionTransaction());
        if (state.inMulti) {
            throw new CommandException("ERR MULTI calls can not be nested");
        }
        state.inMulti = true;
        state.queue.clear();
    }

    @Override
    public void discard(ClientSession session) throws CommandException {
        SessionTransaction state = transactions.get(session.id());
        if (state == null || !state.inMulti) {
            throw new CommandException("ERR DISCARD without MULTI");
        }
        resetTransaction(state);
        watchRegistry.unwatch(session);
    }

    @Override
    public boolean inTransaction(ClientSession session) {
        SessionTransaction state = transactions.get(session.id());
        return state != null && state.inMulti;
    }

    @Override
    public ClientReply exec(ClientSession session, CommandExecutor executor) throws CommandException {
        SessionTransaction state = transactions.get(session.id());
        if (state == null || !state.inMulti) {
            throw new CommandException("ERR EXEC without MULTI");
        }
        if (watchRegistry.isDirty(session)) {
            resetTransaction(state);
            watchRegistry.unwatch(session);
            return new ClientReply.NullBulk();
        }
        List<ClientReply> replies = new ArrayList<>(state.queue.size());
        execLock.lock();
        try {
            for (QueuedCommand command : state.queue) {
                replies.add(executor.execute(session, command.name(), command.arguments()));
            }
        } finally {
            execLock.unlock();
        }
        resetTransaction(state);
        watchRegistry.unwatch(session);
        watchRegistry.clearDirty(session);
        return new ClientReply.CompositeArray(List.copyOf(replies));
    }

    @Override
    public void watch(ClientSession session, List<byte[]> keys) throws CommandException {
        if (inTransaction(session)) {
            throw new CommandException("ERR WATCH inside MULTI is not allowed");
        }
        if (keys.isEmpty()) {
            throw new CommandException("ERR wrong number of arguments for 'watch' command");
        }
        for (byte[] key : keys) {
            if (key == null) {
                throw new CommandException("ERR invalid key");
            }
            watchRegistry.watch(session, key);
        }
    }

    @Override
    public void unwatch(ClientSession session) throws CommandException {
        if (inTransaction(session)) {
            throw new CommandException("ERR UNWATCH inside MULTI is not allowed");
        }
        watchRegistry.unwatch(session);
    }

    @Override
    public void enqueue(ClientSession session, String commandName, List<byte[]> arguments) throws CommandException {
        SessionTransaction state = transactions.get(session.id());
        if (state == null || !state.inMulti) {
            throw new CommandException("ERR MULTI not active");
        }
        String upper = commandName.toUpperCase(Locale.ROOT);
        if ("MULTI".equals(upper) || "EXEC".equals(upper) || "DISCARD".equals(upper) || "WATCH".equals(upper)) {
            throw new CommandException("ERR " + upper + " inside MULTI is not allowed");
        }
        if (isPubSubCommand(upper)) {
            throw new CommandException("ERR " + upper + " inside MULTI is not allowed");
        }
        state.queue.add(new QueuedCommand(upper, List.copyOf(arguments)));
    }

    @Override
    public void clientDisconnected(ClientSession session) {
        transactions.remove(session.id());
        watchRegistry.clientDisconnected(session);
    }

    private static boolean isPubSubCommand(String upper) {
        return "SUBSCRIBE".equals(upper) || "UNSUBSCRIBE".equals(upper) || "PUBLISH".equals(upper);
    }

    private static void resetTransaction(SessionTransaction state) {
        state.inMulti = false;
        state.queue.clear();
    }

    private static final class SessionTransaction {
        private boolean inMulti;
        private final List<QueuedCommand> queue = new ArrayList<>();
    }
}
