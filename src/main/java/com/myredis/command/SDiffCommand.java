package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class SDiffCommand implements Command {

    private final KeyValueStore store;

    public SDiffCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SDIFF";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "sdiff");
        SetValueCommandSupport.requireKeys(arguments);
        SInterCommand.validateSetKeys(store, arguments);
        return new ClientReply.Array(SetValueCommandSupport.difference(store, arguments));
    }
}
