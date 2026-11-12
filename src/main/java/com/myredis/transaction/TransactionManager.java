package com.myredis.transaction;

import com.myredis.command.CommandExecutor;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.util.List;

/**
 * Per-session transaction queues and optimistic locking (WATCH).
 */
public interface TransactionManager {

    void multi(ClientSession session) throws CommandException;

    void discard(ClientSession session) throws CommandException;

    boolean inTransaction(ClientSession session);

    ClientReply exec(ClientSession session, CommandExecutor executor) throws CommandException;

    void watch(ClientSession session, List<byte[]> keys) throws CommandException;

    void unwatch(ClientSession session) throws CommandException;

    void enqueue(ClientSession session, String commandName, List<byte[]> arguments) throws CommandException;

    void clientDisconnected(ClientSession session);
}
