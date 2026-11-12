package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.pubsub.PubSubBroker;

import java.util.List;
import java.util.Objects;

public final class PublishCommand implements Command {

    private final PubSubBroker pubSubBroker;

    public PublishCommand(PubSubBroker pubSubBroker) {
        this.pubSubBroker = Objects.requireNonNull(pubSubBroker, "pubSubBroker");
    }

    @Override
    public String name() {
        return "PUBLISH";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireExact(arguments, 2, "publish");
        byte[] channel = arguments.get(0);
        byte[] message = arguments.get(1);
        if (channel == null) {
            throw new CommandException("ERR invalid channel");
        }
        if (message == null) {
            throw new CommandException("ERR invalid message");
        }
        return new ClientReply.Integer(pubSubBroker.publish(channel, message));
    }
}
