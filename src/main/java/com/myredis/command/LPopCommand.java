package com.myredis.command;

import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LPopCommand implements Command {

    private final KeyValueStore store;

    public LPopCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "LPOP";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "lpop");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        Optional<RedisValue> stored = store.get(key);
        ListRedisValue list = ListCommandSupport.optionalList(stored);
        if (list == null) {
            return new ClientReply.NullBulk();
        }
        return ListCommandSupport.popHead(list)
                .<ClientReply>map(ClientReply.BulkString::new)
                .orElseGet(ClientReply.NullBulk::new);
    }
}
