package com.myredis.network;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable per-connection session bound to a single {@link SocketChannel}.
 */
public final class ConnectedClientSession implements ClientSession {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong();

    private final String id;
    private final SocketChannel channel;
    private final SocketAddress remoteAddress;

    ConnectedClientSession(SocketChannel channel) {
        this.id = Long.toHexString(ID_SEQUENCE.incrementAndGet());
        this.channel = Objects.requireNonNull(channel, "channel");
        this.remoteAddress = channel.socket().getRemoteSocketAddress();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public SocketChannel channel() {
        return channel;
    }

    /** Remote endpoint as reported by the socket; useful for structured logs. */
    SocketAddress remoteAddress() {
        return remoteAddress;
    }
}
