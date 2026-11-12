package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class HDelCommand implements Command {

    private final KeyValueStore store;

    public HDelCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "HDEL";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 2, "hdel");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        HashRedisValue hash = HashCommandSupport.optionalHash(store.get(key));
        if (hash == null) {
            return new ClientReply.Integer(0L);
        }
        byte[][] fields = new byte[arguments.size() - 1][];
        for (int index = 1; index < arguments.size(); index++) {
            fields[index - 1] = HGetCommand.requireField(arguments.get(index));
        }
        synchronized (hash) {
            return new ClientReply.Integer(hash.delete(fields));
        }
    }
}
