package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class SUnionCommand implements Command {

    private final KeyValueStore store;

    public SUnionCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SUNION";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "sunion");
        SetValueCommandSupport.requireKeys(arguments);
        SInterCommand.validateSetKeys(store, arguments);
        return new ClientReply.Array(SetValueCommandSupport.union(store, arguments));
    }
}
