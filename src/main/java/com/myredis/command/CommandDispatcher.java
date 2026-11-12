package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.util.List;

/**
 * Resolves a command name to a {@link Command} and runs it in the client context.
 */
public interface CommandDispatcher {

    ClientReply dispatch(ClientSession session, String commandName, List<byte[]> arguments)
            throws CommandException;
}
