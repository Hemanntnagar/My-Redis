package com.myredis.transaction;

import com.myredis.network.ClientSession;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-session watched keys and marks sessions whose watched keys were mutated by another client.
 */
public final class WatchRegistry {

    private final Map<ChannelKey, Set<String>> keyWatchers = new ConcurrentHashMap<>();
    private final Map<String, Set<ChannelKey>> sessionWatches = new ConcurrentHashMap<>();
    private final Set<String> dirtySessions = ConcurrentHashMap.newKeySet();

    public void watch(ClientSession session, byte[] key) {
        String sessionId = session.id();
        ChannelKey channelKey = new ChannelKey(key);
        sessionWatches.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(channelKey);
        keyWatchers.computeIfAbsent(channelKey, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        dirtySessions.remove(sessionId);
    }

    public void unwatch(ClientSession session) {
        clearSessionWatches(session.id());
        dirtySessions.remove(session.id());
    }

    public boolean isDirty(ClientSession session) {
        return dirtySessions.contains(session.id());
    }

    public void clearDirty(ClientSession session) {
        dirtySessions.remove(session.id());
    }

    public void keyModified(byte[] key) {
        ChannelKey channelKey = new ChannelKey(key);
        Set<String> watchers = keyWatchers.get(channelKey);
        if (watchers == null || watchers.isEmpty()) {
            return;
        }
        dirtySessions.addAll(watchers);
    }

    public void clientDisconnected(ClientSession session) {
        unwatch(session);
    }

    private void clearSessionWatches(String sessionId) {
        Set<ChannelKey> watched = sessionWatches.remove(sessionId);
        if (watched == null) {
            return;
        }
        for (ChannelKey key : watched) {
            Set<String> watchers = keyWatchers.get(key);
            if (watchers == null) {
                continue;
            }
            watchers.remove(sessionId);
            if (watchers.isEmpty()) {
                keyWatchers.remove(key);
            }
        }
    }

    private record ChannelKey(byte[] bytes) {

        private ChannelKey {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChannelKey that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
