package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;

public final class LPushCommand implements Command {

    private final KeyValueStore store;

    public LPushCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "LPUSH";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 2, "lpush");
        byte[] key = requireKey(arguments.get(0));
        byte[][] elements = requireElements(arguments, 1);
        long length = ListCommandSupport.pushHead(store, key, elements);
        return new ClientReply.Integer(length);
    }

    static byte[] requireKey(byte[] key) throws CommandException {
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        return key;
    }

    static byte[][] requireElements(List<byte[]> arguments, int fromIndex) throws CommandException {
        byte[][] elements = new byte[arguments.size() - fromIndex][];
        for (int index = fromIndex; index < arguments.size(); index++) {
            byte[] element = arguments.get(index);
            if (element == null) {
                throw new CommandException("ERR invalid value");
            }
            elements[index - fromIndex] = element;
        }
        return elements;
    }
}
