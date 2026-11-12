package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class HGetCommand implements Command {

    private final KeyValueStore store;

    public HGetCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "HGET";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "hget");
        byte[] key = LPushCommand.requireKey(arguments.get(0));
        byte[] field = requireField(arguments.get(1));
        HashRedisValue hash = HashCommandSupport.optionalHash(store.get(key));
        if (hash == null) {
            return new ClientReply.NullBulk();
        }
        synchronized (hash) {
            return hash.get(field)
                    .<ClientReply>map(ClientReply.BulkString::new)
                    .orElseGet(ClientReply.NullBulk::new);
        }
    }

    static byte[] requireField(byte[] field) throws CommandException {
        if (field == null) {
            throw new CommandException("ERR invalid field");
        }
        return field;
    }
}
