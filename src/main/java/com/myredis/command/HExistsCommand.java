package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class HExistsCommand implements Command {

    private final KeyValueStore store;

    public HExistsCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "HEXISTS";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "hexists");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[] field = HGetCommand.requireField(arguments.get(1));
        HashRedisValue hash = HashCommandSupport.optionalHash(store.get(key));
        if (hash == null) {
            return new ClientReply.Integer(0L);
        }
        synchronized (hash) {
            return new ClientReply.Integer(hash.exists(field) ? 1L : 0L);
        }
    }
}
