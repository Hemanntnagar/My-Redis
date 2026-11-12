package com.myredis.network;

import java.util.List;

/**
 * Executes one decoded client command without performing socket I/O.
 */
@FunctionalInterface
public interface ClientRequestProcessor {

    ClientReply process(ClientSession session, List<byte[]> commandParts);

    /** Invoked after the socket closes so per-client pub/sub and transaction state can be released. */
    default void onDisconnect(ClientSession session) {}
}
