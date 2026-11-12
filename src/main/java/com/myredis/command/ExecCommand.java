package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.transaction.TransactionManager;

import java.util.List;
import java.util.Objects;

public final class ExecCommand implements Command {

    private final TransactionManager transactionManager;
    private final CommandExecutor executor;

    public ExecCommand(TransactionManager transactionManager, CommandExecutor executor) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public String name() {
        return "EXEC";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "exec");
        return transactionManager.exec(session, executor);
    }
}
