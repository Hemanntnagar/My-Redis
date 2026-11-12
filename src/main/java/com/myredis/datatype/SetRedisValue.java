package com.myredis.datatype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Binary-safe Redis set with uniqueness enforced by member content.
 */
public final class SetRedisValue implements RedisValue {

    private final Set<Member> members = new HashSet<>();

    @Override
    public DataType type() {
        return DataType.SET;
    }

    public synchronized long add(byte[]... values) {
        long added = 0;
        for (byte[] value : values) {
            if (members.add(new Member(value))) {
                added++;
            }
        }
        return added;
    }

    public synchronized long remove(byte[]... values) {
        long removed = 0;
        for (byte[] value : values) {
            if (members.remove(new Member(value))) {
                removed++;
            }
        }
        return removed;
    }

    public synchronized boolean contains(byte[] value) {
        return members.contains(new Member(value));
    }

    public synchronized long size() {
        return members.size();
    }

    public synchronized List<byte[]> membersSorted() {
        List<byte[]> sorted = new ArrayList<>(members.size());
        for (Member member : members) {
            sorted.add(member.bytes().clone());
        }
        sorted.sort(Arrays::compareUnsigned);
        return sorted;
    }

    public synchronized Set<byte[]> snapshotMembers() {
        Set<byte[]> snapshot = new HashSet<>(members.size());
        for (Member member : members) {
            snapshot.add(member.bytes().clone());
        }
        return snapshot;
    }

    private record Member(byte[] bytes) {

        private Member {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Member that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
