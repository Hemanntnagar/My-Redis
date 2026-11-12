package com.myredis.command;

import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.util.List;

/** Executes one command outside the transaction interceptor (used by {@code EXEC}). */
@FunctionalInterface
public interface CommandExecutor {

    ClientReply execute(ClientSession session, String commandName, List<byte[]> arguments);
}
