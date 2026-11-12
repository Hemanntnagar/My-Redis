package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class HGetAllCommand implements Command {

    private final KeyValueStore store;

    public HGetAllCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "HGETALL";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "hgetall");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        HashRedisValue hash = HashCommandSupport.optionalHash(store.get(key));
        if (hash == null) {
            return new ClientReply.Array(List.of());
        }
        synchronized (hash) {
            return new ClientReply.Array(hash.getAllFlat());
        }
    }
}
