package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.transaction.TransactionManager;

import java.util.List;
import java.util.Objects;

public final class WatchCommand implements Command {

    private final TransactionManager transactionManager;

    public WatchCommand(TransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public String name() {
        return "WATCH";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "watch");
        transactionManager.watch(session, arguments);
        return ClientReply.ok();
    }
}
