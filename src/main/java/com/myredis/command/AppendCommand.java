package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.KeyValueStore;

import java.util.List;
import java.util.Objects;
/** {@code APPEND} — appends a value to a string key, or creates the key; returns the new length. */
public final class AppendCommand implements Command {

    private final KeyValueStore store;

    public AppendCommand(KeyValueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public String name() {
        return "APPEND";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "append");
        byte[] key = arguments.get(0);
        byte[] value = arguments.get(1);
        if (key == null) {
            throw new CommandException("ERR invalid key");
        }
        if (value == null) {
            throw new CommandException("ERR invalid value");
        }
        int[] length = {0};
        try {
            store.merge(
                    key,
                    existing -> {
                        byte[] result;
                        if (existing == null) {
                            result = value.clone();
                        } else {
                            if (!(existing instanceof StringRedisValue stringValue)) {
                                throw new AppendStateException(
                                        new CommandException(
                                                "WRONGTYPE Operation against a key holding the wrong kind of value"));
                            }
                            byte[] current = stringValue.bytes();
                            result = new byte[current.length + value.length];
                            System.arraycopy(current, 0, result, 0, current.length);
                            System.arraycopy(value, 0, result, current.length, value.length);
                        }
                        length[0] = result.length;
                        return new StringRedisValue(result);
                    });
        } catch (AppendStateException state) {
            throw state.failure();
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
        return new ClientReply.Integer(length[0]);
    }

    private static final class AppendStateException extends RuntimeException {
        private final CommandException failure;

        private AppendStateException(CommandException failure) {
            super(failure);
            this.failure = failure;
        }

        private CommandException failure() {
            return failure;
        }
    }
}
