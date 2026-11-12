package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.pubsub.PubSubBroker;

import java.util.List;
import java.util.Objects;

public final class SubscribeCommand implements Command {

    private final PubSubBroker pubSubBroker;

    public SubscribeCommand(PubSubBroker pubSubBroker) {
        this.pubSubBroker = Objects.requireNonNull(pubSubBroker, "pubSubBroker");
    }

    @Override
    public String name() {
        return "SUBSCRIBE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireAtLeast(arguments, 1, "subscribe");
        for (byte[] channel : arguments) {
            if (channel == null) {
                throw new CommandException("ERR invalid channel");
            }
        }
        List<ClientReply> acknowledgements = pubSubBroker.subscribe(session, arguments.toArray(byte[][]::new));
        if (acknowledgements.size() == 1) {
            return acknowledgements.getFirst();
        }
        return new ClientReply.Sequence(acknowledgements);
    }
}
