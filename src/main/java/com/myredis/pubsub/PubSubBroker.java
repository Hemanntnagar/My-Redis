package com.myredis.pubsub;

import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.util.List;

/**
 * Fan-out of messages to subscribed clients.
 */
public interface PubSubBroker {

    boolean inPubSubMode(ClientSession session);

    int subscriptionCount(ClientSession session);

    List<ClientReply> subscribe(ClientSession session, byte[]... channels);

    List<ClientReply> unsubscribe(ClientSession session, byte[]... channels);

    int publish(byte[] channel, byte[] message);

    void clientDisconnected(ClientSession session);
}
