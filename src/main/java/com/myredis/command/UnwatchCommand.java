package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.transaction.TransactionManager;

import java.util.List;
import java.util.Objects;

public final class UnwatchCommand implements Command {

    private final TransactionManager transactionManager;

    public UnwatchCommand(TransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public String name() {
        return "UNWATCH";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "unwatch");
        transactionManager.unwatch(session);
        return ClientReply.ok();
    }
}
