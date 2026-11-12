package com.myredis.command;

import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class ZCardCommand implements Command {

    private final KeyValueStore store;

    public ZCardCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "ZCARD";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "zcard");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        SortedSetRedisValue zset = SortedSetCommandSupport.optionalSortedSet(store.get(key));
        if (zset == null) {
            return new ClientReply.Integer(0L);
        }
        synchronized (zset) {
            return new ClientReply.Integer(zset.size());
        }
    }
}
