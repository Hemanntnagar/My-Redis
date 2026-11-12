package com.myredis.pubsub;

import com.myredis.network.ClientConnectionManager;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.network.ClientSocketWriter;
import com.myredis.network.ConnectedClientSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory channel fan-out with RESP2 pub/sub push messages.
 */
public final class InMemoryPubSubBroker implements PubSubBroker {

    private ClientConnectionManager connections;
    private final Map<ChannelKey, Set<String>> channelSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<ChannelKey>> sessionChannels = new ConcurrentHashMap<>();

    public InMemoryPubSubBroker(ClientConnectionManager connections) {
        this.connections = connections;
    }

    public void bindConnectionManager(ClientConnectionManager connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public boolean inPubSubMode(ClientSession session) {
        return subscriptionCount(session) > 0;
    }

    @Override
    public int subscriptionCount(ClientSession session) {
        Set<ChannelKey> channels = sessionChannels.get(session.id());
        return channels == null ? 0 : channels.size();
    }

    @Override
    public List<ClientReply> subscribe(ClientSession session, byte[]... channels) {
        List<ClientReply> acknowledgements = new ArrayList<>(channels.length);
        for (byte[] channel : channels) {
            registerSubscription(session, channel);
            acknowledgements.add(subscriptionAck("subscribe", channel, subscriptionCount(session)));
        }
        return acknowledgements;
    }

    @Override
    public List<ClientReply> unsubscribe(ClientSession session, byte[]... channels) {
        if (channels.length == 0) {
            return unsubscribeAll(session);
        }
        List<ClientReply> acknowledgements = new ArrayList<>(channels.length);
        for (byte[] channel : channels) {
            removeSubscription(session, channel);
            acknowledgements.add(subscriptionAck("unsubscribe", channel, subscriptionCount(session)));
        }
        return acknowledgements;
    }

    @Override
    public int publish(byte[] channel, byte[] message) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        ChannelKey key = new ChannelKey(channel);
        Set<String> subscribers = channelSubscribers.get(key);
        if (subscribers == null || subscribers.isEmpty()) {
            return 0;
        }
        ClientReply push = messagePush(channel, message);
        int delivered = 0;
        if (connections == null) {
            return subscribers.size();
        }
        for (String sessionId : Set.copyOf(subscribers)) {
            ConnectedClientSession target = connections.findSession(sessionId);
            if (target == null) {
                continue;
            }
            if (pushToClient(target, push)) {
                delivered++;
            }
        }
        return delivered;
    }

    @Override
    public void clientDisconnected(ClientSession session) {
        Set<ChannelKey> channels = sessionChannels.remove(session.id());
        if (channels == null) {
            return;
        }
        for (ChannelKey channel : channels) {
            Set<String> subscribers = channelSubscribers.get(channel);
            if (subscribers == null) {
                continue;
            }
            subscribers.remove(session.id());
            if (subscribers.isEmpty()) {
                channelSubscribers.remove(channel);
            }
        }
    }

    private List<ClientReply> unsubscribeAll(ClientSession session) {
        Set<ChannelKey> channels = sessionChannels.remove(session.id());
        if (channels == null || channels.isEmpty()) {
            return List.of(subscriptionAck("unsubscribe", null, 0));
        }
        List<ClientReply> acknowledgements = new ArrayList<>();
        for (ChannelKey channel : channels) {
            Set<String> subscribers = channelSubscribers.get(channel);
            if (subscribers != null) {
                subscribers.remove(session.id());
                if (subscribers.isEmpty()) {
                    channelSubscribers.remove(channel);
                }
            }
            acknowledgements.add(
                    subscriptionAck("unsubscribe", channel.bytes(), subscriptionCount(session)));
        }
        return acknowledgements;
    }

    private void registerSubscription(ClientSession session, byte[] channel) {
        ChannelKey key = new ChannelKey(channel);
        sessionChannels.computeIfAbsent(session.id(), ignored -> ConcurrentHashMap.newKeySet()).add(key);
        channelSubscribers.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(session.id());
    }

    private boolean removeSubscription(ClientSession session, byte[] channel) {
        ChannelKey key = new ChannelKey(channel);
        Set<ChannelKey> channels = sessionChannels.get(session.id());
        if (channels == null) {
            return false;
        }
        boolean removedFromSession = channels.remove(key);
        Set<String> subscribers = channelSubscribers.get(key);
        if (subscribers != null) {
            subscribers.remove(session.id());
            if (subscribers.isEmpty()) {
                channelSubscribers.remove(key);
            }
        }
        if (channels.isEmpty()) {
            sessionChannels.remove(session.id());
        }
        return removedFromSession;
    }

    private boolean pushToClient(ConnectedClientSession session, ClientReply reply) {
        boolean written = new ClientSocketWriter(session).write(reply);
        if (!written) {
            connections.remove(session);
        }
        return written;
    }

    private static ClientReply subscriptionAck(String kind, byte[] channel, int count) {
        ClientReply channelReply = channel == null ? new ClientReply.NullBulk() : new ClientReply.BulkString(channel);
        return new ClientReply.CompositeArray(
                List.of(
                        new ClientReply.BulkString(kind.getBytes()),
                        channelReply,
                        new ClientReply.Integer(count)));
    }

    private static ClientReply messagePush(byte[] channel, byte[] message) {
        return new ClientReply.CompositeArray(
                List.of(
                        new ClientReply.BulkString("message".getBytes()),
                        new ClientReply.BulkString(channel),
                        new ClientReply.BulkString(message)));
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
