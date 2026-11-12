package com.myredis.command;

import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ZRankCommand implements Command {

    private final KeyValueStore store;

    public ZRankCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "ZRANK";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "zrank");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[] member = LPushCommand.requireElements(arguments, 1)[0];

        SortedSetRedisValue zset = SortedSetCommandSupport.optionalSortedSet(store.get(key));
        if (zset == null) {
            return new ClientReply.NullBulk();
        }
        Optional<Long> rank;
        synchronized (zset) {
            rank = zset.rank(member);
        }
        if (rank.isEmpty()) {
            return new ClientReply.NullBulk();
        }
        return new ClientReply.Integer(rank.get());
    }
}
