package com.myredis.network;

import java.util.List;

/**
 * Default processor used until a command pipeline is wired at bootstrap time.
 */
final class UnconfiguredClientRequestProcessor implements ClientRequestProcessor {

    @Override
    public ClientReply process(ClientSession session, List<byte[]> commandParts) {
        return ClientReply.error("ERR command handler not configured");
    }
}
