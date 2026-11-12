package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class HSetCommand implements Command {

    private final KeyValueStore store;

    public HSetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "HSET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        HashCommandSupport.requireFieldValuePairs(arguments.size(), "hset");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[][] pairs = fieldValuePairs(arguments, 1);
        return new ClientReply.Integer(HashCommandSupport.setFields(store, key, pairs));
    }

    static byte[][] fieldValuePairs(List<byte[]> arguments, int fieldStart) throws CommandException {
        int pairCount = (arguments.size() - fieldStart) / 2;
        byte[][] pairs = new byte[pairCount * 2][];
        for (int index = 0; index < pairCount; index++) {
            byte[] field = arguments.get(fieldStart + index * 2);
            byte[] value = arguments.get(fieldStart + index * 2 + 1);
            if (field == null || value == null) {
                throw new CommandException("ERR invalid field or value");
            }
            pairs[index * 2] = field;
            pairs[index * 2 + 1] = value;
        }
        return pairs;
    }
}
