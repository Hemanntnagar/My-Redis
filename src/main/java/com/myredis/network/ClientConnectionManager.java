package com.myredis.network;

import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry for live client sockets: registration, removal, capacity limits, idle
 * disconnects, and shutdown cleanup.
 */
public final class ClientConnectionManager {

    private final int maxConnections;
    private final Duration clientIdleTimeout;
    private final AtomicInteger activeCount = new AtomicInteger();
    private final ConcurrentHashMap<String, ConnectedClientSession> sessions = new ConcurrentHashMap<>();

    public ClientConnectionManager(int maxConnections, Duration clientIdleTimeout) {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be at least 1");
        }
        this.maxConnections = maxConnections;
        this.clientIdleTimeout =
                Objects.requireNonNull(clientIdleTimeout, "clientIdleTimeout");
        if (clientIdleTimeout.isNegative()) {
            throw new IllegalArgumentException("clientIdleTimeout must not be negative");
        }
    }

    /**
     * Reserves a connection slot and tracks the session, or returns {@code null} when at capacity.
     */
    public ConnectedClientSession register(SocketChannel channel) {
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

    /**
     * Drops tracking for a session and closes its socket.
     */
    public void remove(ConnectedClientSession session) {
        if (session == null) {
            return;
        }
        if (sessions.remove(session.id(), session)) {
            activeCount.decrementAndGet();
        }
        closeQuietly(session.channel());
    }

    /**
     * Closes every registered client socket and clears the registry (used during server shutdown).
     */
    public void shutdownAll() {
        for (ConnectedClientSession session : sessions.values()) {
            closeQuietly(session.channel());
        }
        sessions.clear();
        activeCount.set(0);
    }

    /**
     * Disconnects sessions that have been idle longer than {@link #clientIdleTimeout()}.
     */
    public void enforceIdleTimeouts(StructuredServerLogger logger) {
        if (clientIdleTimeout.isZero()) {
            return;
        }
        long thresholdNanos = clientIdleTimeout.toNanos();
        for (ConnectedClientSession session : sessions.values()) {
            if (session.idleNanos() < thresholdNanos) {
                continue;
            }
            logger.logInfo(
                    "client_idle_timeout",
                    "client disconnected after idle timeout",
                    LogContext.builder()
                            .put("session_id", session.id())
                            .put("remote", String.valueOf(session.remoteAddress()))
                            .put("idle_ms", String.valueOf(clientIdleTimeout.toMillis()))
                            .build());
            remove(session);
        }
    }

    public int activeCount() {
        return activeCount.get();
    }

    public int maxConnections() {
        return maxConnections;
    }

    public Duration clientIdleTimeout() {
        return clientIdleTimeout;
    }

    public boolean isTracking(ConnectedClientSession session) {
        return session != null && sessions.containsKey(session.id());
    }

    /** Returns the live session for {@code sessionId}, or {@code null} when disconnected. */
    public ConnectedClientSession findSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }

    private static void closeQuietly(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best effort during teardown.
        }
    }
}
