package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.transaction.TransactionManager;

import java.util.List;
import java.util.Objects;

public final class DiscardCommand implements Command {

    private final TransactionManager transactionManager;

    public DiscardCommand(TransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public String name() {
        return "DISCARD";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "discard");
        transactionManager.discard(session);
        return ClientReply.ok();
    }
}
