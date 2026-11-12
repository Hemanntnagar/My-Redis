package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** {@code KEYS} — returns all keys matching a glob pattern in unsigned byte order. */
public final class KeysCommand implements Command {

    private final KeyValueStore store;

    public KeysCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "KEYS";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 1, "keys");
        byte[] pattern = arguments.get(0);
        if (pattern == null) {
            throw new CommandException("ERR invalid pattern");
        }
        List<byte[]> matched = new ArrayList<>();
        for (byte[] key : store.snapshotKeys()) {
            if (KeyGlobMatcher.matches(key, pattern)) {
                matched.add(key);
            }
        }
        matched.sort(Arrays::compareUnsigned);
        return new ClientReply.Array(matched);
    }
}
