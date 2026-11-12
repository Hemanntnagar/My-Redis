package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HMGetCommand implements Command {

    private final KeyValueStore store;

    public HMGetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "HMGET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 2, "hmget");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        HashRedisValue hash = HashCommandSupport.optionalHash(store.get(key));
        List<byte[]> values = new ArrayList<>(arguments.size() - 1);
        if (hash == null) {
            for (int index = 1; index < arguments.size(); index++) {
                HGetCommand.requireField(arguments.get(index));
                values.add(null);
            }
            return new ClientReply.Array(values);
        }
        synchronized (hash) {
            for (int index = 1; index < arguments.size(); index++) {
                byte[] field = HGetCommand.requireField(arguments.get(index));
                values.add(hash.get(field).orElse(null));
            }
        }
        return new ClientReply.Array(values);
    }
}
