package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.storage.KeyValueStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class SortedSetCommandSupport {

    static final String WRONGTYPE = ListCommandSupport.WRONGTYPE;
    static final String NOT_A_FLOAT = "ERR value is not a valid float";

    private SortedSetCommandSupport() {
    }

    static void requireScoreMemberPairs(int argumentCount, String commandName) throws CommandException {
        if (argumentCount < 3 || (argumentCount & 1) == 0) {
            throw new CommandException("ERR wrong number of arguments for '" + commandName + "' command");
        }
    }

    static List<ScoreMember> parseScoreMembers(List<byte[]> arguments, int fromIndex) throws CommandException {
        List<ScoreMember> pairs = new ArrayList<>((arguments.size() - fromIndex) / 2);
        for (int index = fromIndex; index < arguments.size(); index += 2) {
            double score = parseScore(arguments.get(index));
            byte[] member = requireMember(arguments.get(index + 1));
            pairs.add(new ScoreMember(score, member));
        }
        return pairs;
    }

    static double parseScore(byte[] bytes) throws CommandException {
        if (bytes == null || bytes.length == 0) {
            throw new CommandException(NOT_A_FLOAT);
        }
        String raw = new String(bytes, StandardCharsets.US_ASCII);
        try {
            double score = Double.parseDouble(raw);
            if (!Double.isFinite(score)) {
                throw new CommandException(NOT_A_FLOAT);
            }
            return score;
        } catch (NumberFormatException failure) {
            throw new CommandException(NOT_A_FLOAT);
        }
    }

    static byte[] formatScore(double score) {
        long asLong = (long) score;
        if (score == asLong) {
            return Long.toString(asLong).getBytes(StandardCharsets.US_ASCII);
        }
        return Double.toString(score).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] requireMember(byte[] member) throws CommandException {
        if (member == null) {
            throw new CommandException("ERR invalid value");
        }
        return member;
    }

    static long add(KeyValueStore store, byte[] key, List<ScoreMember> pairs) throws CommandException {
        Objects.requireNonNull(pairs, "pairs");
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            SortedSetRedisValue zset = new SortedSetRedisValue();
            long added;
            synchronized (zset) {
                added = addAll(zset, pairs);
            }
            put(store, key, zset);
            return added;
        }
        SortedSetRedisValue zset = requireSortedSet(stored.get());
        synchronized (zset) {
            long added = addAll(zset, pairs);
            put(store, key, zset);
            return added;
        }
    }

    private static long addAll(SortedSetRedisValue zset, List<ScoreMember> pairs) {
        long added = 0L;
        for (ScoreMember pair : pairs) {
            added += zset.add(pair.score(), pair.member());
        }
        return added;
    }

    static long removeMembers(KeyValueStore store, byte[] key, byte[][] members) throws CommandException {
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            return 0L;
        }
        SortedSetRedisValue zset = requireSortedSet(stored.get());
        synchronized (zset) {
            long removed = zset.remove(members);
            put(store, key, zset);
            return removed;
        }
    }

    static SortedSetRedisValue optionalSortedSet(Optional<RedisValue> stored) throws CommandException {
        if (stored.isEmpty()) {
            return null;
        }
        return requireSortedSet(stored.get());
    }

    static SortedSetRedisValue requireSortedSet(RedisValue value) throws CommandException {
        if (value instanceof SortedSetRedisValue zset) {
            return zset;
        }
        throw new CommandException(WRONGTYPE);
    }

    private static void put(KeyValueStore store, byte[] key, SortedSetRedisValue value) throws CommandException {
        try {
            store.put(key, value);
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
        }
    }

    record ScoreMember(double score, byte[] member) {}
}
