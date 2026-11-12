package com.myredis.command;

import com.myredis.datatype.SetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class SMembersCommand implements Command {

    private final KeyValueStore store;

    public SMembersCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SMEMBERS";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "smembers");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        SetRedisValue set = SetValueCommandSupport.optionalSet(store.get(key));
        if (set == null) {
            return new ClientReply.Array(List.of());
        }
        List<byte[]> members;
        synchronized (set) {
            members = set.membersSorted();
        }
        return new ClientReply.Array(members);
    }
}
