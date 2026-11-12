package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.util.List;

/**
 * {@code PING} — simple-string {@code PONG} with no arguments; bulk string echo with one argument.
 */
public final class PingCommand implements Command {

    @Override
    public String name() {
        return "PING";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireBetween(arguments, 0, 1, "ping");
        if (arguments.isEmpty()) {
            return new ClientReply.SimpleString("PONG");
        }
        return new ClientReply.BulkString(arguments.getFirst());
    }
}
