package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class RPushCommand implements Command {

    private final KeyValueStore store;

    public RPushCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "RPUSH";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 2, "rpush");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[][] elements = LPushCommand.requireElements(arguments, 1);
        long length = ListCommandSupport.pushTail(store, key, elements);
        return new ClientReply.Integer(length);
    }
}
