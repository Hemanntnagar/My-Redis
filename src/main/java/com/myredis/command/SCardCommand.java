package com.myredis.command;

import com.myredis.datatype.SetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class SCardCommand implements Command {

    private final KeyValueStore store;

    public SCardCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "SCARD";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "scard");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        SetRedisValue set = SetValueCommandSupport.optionalSet(store.get(key));
        if (set == null) {
            return new ClientReply.Integer(0L);
        }
        synchronized (set) {
            return new ClientReply.Integer(set.size());
        }
    }
}
