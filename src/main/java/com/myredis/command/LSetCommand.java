package com.myredis.command;

import com.myredis.datatype.ListRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class LSetCommand implements Command {

    private final KeyValueStore store;

    public LSetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "LSET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 3, "lset");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        long rawIndex = StringIntegerValues.parseStrict(arguments.get(1));
        byte[] element = arguments.get(2);
        if (element == null) {
            throw new CommandException("ERR invalid value");
        }

        ListRedisValue list = ListCommandSupport.requireExistingList(store.get(key));
        synchronized (list) {
            int index = ListCommandSupport.normalizeIndex(rawIndex, (int) list.length());
            if (index < 0 || !list.setAt(index, element)) {
                throw new CommandException(ListCommandSupport.INDEX_OUT_OF_RANGE);
            }
        }
        try {
            store.put(key, list);
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return ClientReply.ok();
    }
}
