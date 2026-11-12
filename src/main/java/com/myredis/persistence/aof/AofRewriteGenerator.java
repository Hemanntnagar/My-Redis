package com.myredis.persistence.aof;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.persistence.snapshot.DatabaseSnapshot;
import com.myredis.persistence.snapshot.SnapshotEntry;
import com.myredis.persistence.snapshot.SnapshotFormat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Builds minimal command sequences that recreate a captured database snapshot. */
public final class AofRewriteGenerator {

    private AofRewriteGenerator() {}

    public static List<List<byte[]>> commandsFor(DatabaseSnapshot snapshot) {
        List<List<byte[]>> commands = new ArrayList<>();
        commands.add(List.of(bytes("FLUSHDB")));
        for (SnapshotEntry entry : snapshot.entries()) {
            commands.addAll(commandsForEntry(entry));
        }
        return commands;
    }

    private static List<List<byte[]>> commandsForEntry(SnapshotEntry entry) {
        List<List<byte[]>> commands = new ArrayList<>();
        commands.addAll(valueCommands(entry.key(), entry.value()));
        if (entry.expireAtEpochMillis() != SnapshotFormat.NO_EXPIRE) {
            commands.add(
                    List.of(
                            bytes("PEXPIREAT"),
                            entry.key().clone(),
                            Long.toString(entry.expireAtEpochMillis()).getBytes(StandardCharsets.UTF_8)));
        }
        return commands;
    }

    private static List<List<byte[]>> valueCommands(byte[] key, RedisValue value) {
        return switch (value.type()) {
            case STRING -> List.of(List.of(bytes("SET"), key.clone(), ((StringRedisValue) value).bytes().clone()));
            case LIST -> listCommands(key, (ListRedisValue) value);
            case SET -> setCommands(key, (SetRedisValue) value);
            case HASH -> hashCommands(key, (HashRedisValue) value);
            case SORTED_SET -> sortedSetCommands(key, (SortedSetRedisValue) value);
            case STREAM -> throw new IllegalStateException("stream type is not supported in AOF rewrite");
        };
    }

    private static List<List<byte[]>> listCommands(byte[] key, ListRedisValue value) {
        List<byte[]> elements = value.snapshotElementsInOrder();
        if (elements.isEmpty()) {
            return List.of();
        }
        List<byte[]> command = new ArrayList<>(2 + elements.size());
        command.add(bytes("RPUSH"));
        command.add(key.clone());
        for (byte[] element : elements) {
            command.add(element.clone());
        }
        return List.of(command);
    }

    private static List<List<byte[]>> setCommands(byte[] key, SetRedisValue value) {
        List<byte[]> members = value.membersSorted();
        if (members.isEmpty()) {
            return List.of();
        }
        List<byte[]> command = new ArrayList<>(2 + members.size());
        command.add(bytes("SADD"));
        command.add(key.clone());
        for (byte[] member : members) {
            command.add(member.clone());
        }
        return List.of(command);
    }

    private static List<List<byte[]>> hashCommands(byte[] key, HashRedisValue value) {
        List<byte[]> flat = value.getAllFlat();
        List<List<byte[]>> commands = new ArrayList<>();
        for (int index = 0; index < flat.size(); index += 2) {
            commands.add(
                    List.of(
                            bytes("HSET"),
                            key.clone(),
                            flat.get(index).clone(),
                            flat.get(index + 1).clone()));
        }
        return commands;
    }

    private static List<List<byte[]>> sortedSetCommands(byte[] key, SortedSetRedisValue value) {
        List<List<byte[]>> commands = new ArrayList<>();
        for (SortedSetRedisValue.ScoredMemberSnapshot member : value.snapshotByRank()) {
            commands.add(
                    List.of(
                            bytes("ZADD"),
                            key.clone(),
                            Double.toString(member.score()).getBytes(StandardCharsets.UTF_8),
                            member.member().clone()));
        }
        return commands;
    }

    private static byte[] bytes(String command) {
        return command.getBytes(StandardCharsets.UTF_8);
    }
}
