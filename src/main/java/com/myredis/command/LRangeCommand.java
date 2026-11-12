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

public final class LRangeCommand implements Command {

    private final KeyValueStore store;

    public LRangeCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "LRANGE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 3, "lrange");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        long start = StringIntegerValues.parseStrict(arguments.get(1));
        long stop = StringIntegerValues.parseStrict(arguments.get(2));

        Optional<RedisValue> stored = store.get(key);
        ListRedisValue list = ListCommandSupport.optionalList(stored);
        if (list == null) {
            return new ClientReply.Array(List.of());
        }

        int size;
        List<byte[]> slice;
        synchronized (list) {
            size = (int) list.length();
            int[] bounds = ListCommandSupport.normalizeRange(start, stop, size);
            if (bounds[0] < 0) {
                return new ClientReply.Array(List.of());
            }
            slice = list.rangeInclusive(bounds[0], bounds[1]);
        }
        return new ClientReply.Array(slice);
    }
}
