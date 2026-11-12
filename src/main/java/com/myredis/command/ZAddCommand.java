package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class ZAddCommand implements Command {

    private final KeyValueStore store;

    public ZAddCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "ZADD";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        SortedSetCommandSupport.requireScoreMemberPairs(arguments.size(), "zadd");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        List<SortedSetCommandSupport.ScoreMember> pairs =
                SortedSetCommandSupport.parseScoreMembers(arguments, 1);
        return new ClientReply.Integer(SortedSetCommandSupport.add(store, key, pairs));
    }
}
