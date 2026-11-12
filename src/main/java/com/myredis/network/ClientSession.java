package com.myredis.network;

import java.nio.channels.SocketChannel;

/**
 * Per-client connection state for the duration of a TCP session.
 */
public interface ClientSession {

    String id();

    SocketChannel channel();
}
