package com.myredis.command;

import com.myredis.datatype.SetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class SIsMemberCommand implements Command {

    private final KeyValueStore store;

    public SIsMemberCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SISMEMBER";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "sismember");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[] member = LPushCommand.requireElements(arguments, 1)[0];
        SetRedisValue set = SetValueCommandSupport.optionalSet(store.get(key));
        if (set == null) {
            return new ClientReply.Integer(0L);
        }
        synchronized (set) {
            return new ClientReply.Integer(set.contains(member) ? 1L : 0L);
        }
    }
}
