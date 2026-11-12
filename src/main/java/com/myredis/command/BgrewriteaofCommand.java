package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.aof.AppendOnlyFile;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.util.List;
import java.util.Objects;

/** {@code BGREWRITEAOF} — compacts the append-only log in the background. */
public final class BgrewriteaofCommand implements Command {

    private final AppendOnlyFile appendOnlyFile;
    private final ExpirationAwareKeyValueStore store;
    private final InMemoryExpirationService expiration;

    public BgrewriteaofCommand(
            AppendOnlyFile appendOnlyFile,
            ExpirationAwareKeyValueStore store,
            InMemoryExpirationService expiration) {
        this.appendOnlyFile = Objects.requireNonNull(appendOnlyFile, "appendOnlyFile");
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
    }

    @Override
    public String name() {
        return "BGREWRITEAOF";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 0, "bgrewriteaof");
        if (!appendOnlyFile.beginRewrite(store, expiration)) {
            throw new CommandException("ERR AOF rewrite already in progress");
        }
        return ClientReply.ok();
    }
}
