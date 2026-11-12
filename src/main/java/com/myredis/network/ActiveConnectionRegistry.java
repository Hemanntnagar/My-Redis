package com.myredis.network;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks live client sessions and enforces a configurable connection ceiling.
 */
final class ActiveConnectionRegistry {

    private final int maxConnections;
    private final AtomicInteger activeCount = new AtomicInteger();
    private final ConcurrentHashMap<String, ConnectedClientSession> sessions = new ConcurrentHashMap<>();

    ActiveConnectionRegistry(int maxConnections) {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be at least 1");
        }
        this.maxConnections = maxConnections;
    }

    /**
     * @return registered session, or {@code null} when at capacity
     */
    ConnectedClientSession tryRegister(SocketChannel channel) {
        Objects.requireNonNull(channel, "channel");

        while (true) {
            int current = activeCount.get();
            if (current >= maxConnections) {
                return null;
            }
            if (activeCount.compareAndSet(current, current + 1)) {
                ConnectedClientSession session = new ConnectedClientSession(channel);
                sessions.put(session.id(), session);
                return session;
            }
        }
    }

    void unregister(ConnectedClientSession session) {
        if (session == null) {
            return;
        }
        if (sessions.remove(session.id(), session)) {
            activeCount.decrementAndGet();
        }
        closeQuietly(session.channel());
    }

    void closeAllSessions() {
        for (ConnectedClientSession session : sessions.values()) {
            closeQuietly(session.channel());
        }
        sessions.clear();
        activeCount.set(0);
    }

    int activeCount() {
        return activeCount.get();
    }

    int maxConnections() {
        return maxConnections;
    }

    private static void closeQuietly(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best effort during teardown.
        }
    }
}
