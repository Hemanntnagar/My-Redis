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

public final class LIndexCommand implements Command {

    private final KeyValueStore store;

    public LIndexCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "LINDEX";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "lindex");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        long rawIndex = StringIntegerValues.parseStrict(arguments.get(1));

        Optional<RedisValue> stored = store.get(key);
        ListRedisValue list = ListCommandSupport.optionalList(stored);
        if (list == null) {
            return new ClientReply.NullBulk();
        }

        synchronized (list) {
            int index = ListCommandSupport.normalizeIndex(rawIndex, (int) list.length());
            if (index < 0) {
                return new ClientReply.NullBulk();
            }
            return list.elementAt(index)
                    .<ClientReply>map(ClientReply.BulkString::new)
                    .orElseGet(ClientReply.NullBulk::new);
        }
    }
}
