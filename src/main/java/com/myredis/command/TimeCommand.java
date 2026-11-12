package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.time.Instant;
import java.util.List;

/** {@code TIME} — Unix timestamp and microsecond fraction. */
public final class TimeCommand implements Command {

    @Override
    public String name() {
        return "TIME";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        if (!arguments.isEmpty()) {
            throw new CommandException("ERR wrong number of arguments for 'time' command");
        }
        Instant now = Instant.now();
        return new ClientReply.CompositeArray(
                List.of(
                        new ClientReply.BulkString(Long.toString(now.getEpochSecond()).getBytes()),
                        new ClientReply.BulkString(Long.toString(now.getNano() / 1_000).getBytes())));
    }
}
