package com.myredis.command;

import com.myredis.datatype.RedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.storage.KeyValueStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SetValueCommandSupport {

    static final String WRONGTYPE = ListCommandSupport.WRONGTYPE;

    private SetValueCommandSupport() {
    }

    static long addMembers(KeyValueStore store, byte[] key, byte[][] members) throws CommandException {
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            SetRedisValue set = new SetRedisValue();
            long added = set.add(members);
            put(store, key, set);
            return added;
        }
        SetRedisValue set = requireSet(stored.get());
        synchronized (set) {
            long added = set.add(members);
            put(store, key, set);
            return added;
        }
    }

    static long removeMembers(KeyValueStore store, byte[] key, byte[][] members) throws CommandException {
        Optional<RedisValue> stored = store.get(key);
        if (stored.isEmpty()) {
            return 0L;
        }
        SetRedisValue set = requireSet(stored.get());
        synchronized (set) {
            return set.remove(members);
        }
    }

    static SetRedisValue optionalSet(Optional<RedisValue> stored) throws CommandException {
        if (stored.isEmpty()) {
            return null;
        }
        return requireSet(stored.get());
    }

    static SetRedisValue requireSet(RedisValue value) throws CommandException {
        if (value instanceof SetRedisValue set) {
            return set;
        }
        throw new CommandException(WRONGTYPE);
    }

    static void requireKeys(List<byte[]> arguments) throws CommandException {
        for (byte[] key : arguments) {
            if (key == null) {
                throw new CommandException("ERR invalid key");
            }
        }
    }

    static List<Set<byte[]>> memberSets(KeyValueStore store, List<byte[]> keys) throws CommandException {
        List<Set<byte[]>> sets = new ArrayList<>(keys.size());
        for (byte[] key : keys) {
            SetRedisValue set = optionalSet(store.get(key));
            if (set == null) {
                sets.add(Set.of());
            } else {
                synchronized (set) {
                    sets.add(set.snapshotMembers());
                }
            }
        }
        return sets;
    }

    static List<byte[]> intersection(KeyValueStore store, List<byte[]> keys) throws CommandException {
        List<Set<byte[]>> sets = memberSets(store, keys);
        if (sets.isEmpty()) {
            return List.of();
        }
        Set<MemberKey> acc = toMemberKeys(sets.getFirst());
        for (int index = 1; index < sets.size(); index++) {
            acc.retainAll(toMemberKeys(sets.get(index)));
        }
        return sortMembers(acc);
    }

    static List<byte[]> union(KeyValueStore store, List<byte[]> keys) throws CommandException {
        Set<MemberKey> acc = new HashSet<>();
        for (Set<byte[]> members : memberSets(store, keys)) {
            acc.addAll(toMemberKeys(members));
        }
        return sortMembers(acc);
    }

    static List<byte[]> difference(KeyValueStore store, List<byte[]> keys) throws CommandException {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Set<byte[]>> sets = memberSets(store, keys);
        Set<MemberKey> result = new HashSet<>(toMemberKeys(sets.getFirst()));
        for (int index = 1; index < sets.size(); index++) {
            result.removeAll(toMemberKeys(sets.get(index)));
        }
        return sortMembers(result);
    }

    private static Set<MemberKey> toMemberKeys(Set<byte[]> members) {
        Set<MemberKey> keys = new HashSet<>(members.size());
        for (byte[] member : members) {
            keys.add(new MemberKey(member));
        }
        return keys;
    }

    private static List<byte[]> sortMembers(Set<MemberKey> members) {
        List<byte[]> sorted = new ArrayList<>(members.size());
        for (MemberKey member : members) {
            sorted.add(member.bytes().clone());
        }
        sorted.sort(Arrays::compareUnsigned);
        return sorted;
    }

    private static void put(KeyValueStore store, byte[] key, SetRedisValue value) throws CommandException {
        try {
            store.put(key, value);
        } catch (StorageException failure) {
            throw new CommandException(failure.clientMessage());
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
