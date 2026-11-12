package com.myredis.pubsub;

import com.myredis.network.ClientSession;

/**
 * Fan-out of messages to subscribed clients.
 */
public interface PubSubBroker {

    void subscribe(ClientSession session, byte[] channel);

    void unsubscribe(ClientSession session, byte[] channel);

    int publish(byte[] channel, byte[] message);
}
