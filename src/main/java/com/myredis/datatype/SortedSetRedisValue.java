package com.myredis.datatype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Binary-safe sorted set ordered by score, with lexicographical tie-breaking on equal scores.
 */
public final class SortedSetRedisValue implements RedisValue {

    private final TreeSet<ScoredMember> ranked;
    private final Map<MemberKey, Double> scores = new HashMap<>();

    public SortedSetRedisValue() {
        ranked = new TreeSet<>(SortedSetRedisValue::compareScoredMembers);
    }

    @Override
    public DataType type() {
        return DataType.SORTED_SET;
    }

    public synchronized long add(double score, byte[] member) {
        MemberKey key = new MemberKey(member);
        Double existing = scores.get(key);
        if (existing != null) {
            if (Double.compare(existing, score) == 0) {
                return 0L;
            }
            ranked.remove(new ScoredMember(existing, key.bytes()));
            scores.put(key, score);
            ranked.add(new ScoredMember(score, key.bytes()));
            return 0L;
        }
        scores.put(key, score);
        ranked.add(new ScoredMember(score, key.bytes()));
        return 1L;
    }

    public synchronized long remove(byte[]... members) {
        long removed = 0L;
        for (byte[] member : members) {
            MemberKey key = new MemberKey(member);
            Double score = scores.remove(key);
            if (score == null) {
                continue;
            }
            ranked.remove(new ScoredMember(score, key.bytes()));
            removed++;
        }
        return removed;
    }

    public synchronized Optional<Double> score(byte[] member) {
        MemberKey key = new MemberKey(member);
        Double value = scores.get(key);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    public synchronized Optional<Long> rank(byte[] member) {
        MemberKey key = new MemberKey(member);
        Double score = scores.get(key);
        if (score == null) {
            return Optional.empty();
        }
        ScoredMember target = new ScoredMember(score, key.bytes());
        return Optional.of((long) ranked.headSet(target).size());
    }

    public synchronized Optional<Long> reverseRank(byte[] member) {
        return rank(member).map(value -> (long) scores.size() - 1L - value);
    }

    public synchronized long size() {
        return scores.size();
    }

    public synchronized List<byte[]> rangeByRank(long start, long stop) {
        int length = scores.size();
        if (length == 0) {
            return List.of();
        }
        int[] bounds = normalizeRange(start, stop, length);
        if (bounds[0] < 0) {
            return List.of();
        }
        List<byte[]> members = new ArrayList<>(bounds[1] - bounds[0] + 1);
        int index = 0;
        for (ScoredMember entry : ranked) {
            if (index > bounds[1]) {
                break;
            }
            if (index >= bounds[0]) {
                members.add(entry.member().clone());
            }
            index++;
        }
        return members;
    }

    private static int[] normalizeRange(long start, long stop, int size) {
        long from = start;
        long to = stop;
        if (from < 0) {
            from = size + from;
        }
        if (to < 0) {
            to = size + to;
        }
        if (from < 0) {
            from = 0;
        }
        if (to >= size) {
            to = size - 1;
        }
        if (size == 0 || from > to || from >= size) {
            return new int[] {-1, -1};
        }
        return new int[] {(int) from, (int) to};
    }

    private static int compareScoredMembers(ScoredMember left, ScoredMember right) {
        int byScore = Double.compare(left.score(), right.score());
        if (byScore != 0) {
            return byScore;
        }
        return Arrays.compareUnsigned(left.member(), right.member());
    }

    private record ScoredMember(double score, byte[] member) {

        private ScoredMember {
            member = member.clone();
        }
    }

    private record MemberKey(byte[] bytes) {

        private MemberKey {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MemberKey that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
