package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class ZRemCommand implements Command {

    private final KeyValueStore store;

    public ZRemCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "ZREM";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 2, "zrem");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[][] members = LPushCommand.requireElements(arguments, 1);
        return new ClientReply.Integer(SortedSetCommandSupport.removeMembers(store, key, members));
    }
}
