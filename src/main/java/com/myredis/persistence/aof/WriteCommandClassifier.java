package com.myredis.persistence.aof;

import java.util.Locale;
import java.util.Set;

/** Identifies mutating commands that belong in the append-only log. */
public final class WriteCommandClassifier {

    private static final Set<String> WRITE_COMMANDS =
            Set.of(
                    "SET",
                    "DEL",
                    "MSET",
                    "APPEND",
                    "INCR",
                    "DECR",
                    "INCRBY",
                    "DECRBY",
                    "FLUSHDB",
                    "RENAME",
                    "RENAMENX",
                    "LPUSH",
                    "RPUSH",
                    "LPOP",
                    "RPOP",
                    "LSET",
                    "SADD",
                    "SREM",
                    "HSET",
                    "HDEL",
                    "ZADD",
                    "ZREM",
                    "EXPIRE",
                    "PEXPIRE",
                    "EXPIREAT",
                    "PEXPIREAT",
                    "PERSIST");

    private WriteCommandClassifier() {}

    public static boolean isWriteCommand(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        return WRITE_COMMANDS.contains(commandName.toUpperCase(Locale.ROOT));
    }
}
