package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientSession;

import java.util.List;

/**
 * Executable Redis command identified by its canonical name (e.g. {@code GET}).
 */
public interface Command {

    String name();

    void execute(ClientSession session, List<byte[]> arguments) throws CommandException;
}
