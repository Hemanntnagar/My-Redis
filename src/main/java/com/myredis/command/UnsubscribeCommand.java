package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.pubsub.PubSubBroker;

import java.util.List;
import java.util.Objects;

public final class UnsubscribeCommand implements Command {

    private final PubSubBroker pubSubBroker;

    public UnsubscribeCommand(PubSubBroker pubSubBroker) {
        this.pubSubBroker = Objects.requireNonNull(pubSubBroker, "pubSubBroker");
    }

    @Override
    public String name() {
        return "UNSUBSCRIBE";
    }

    @Override
    public ClientReply execute(ClientSession session, List<byte[]> arguments) throws CommandException {
        CommandArity.requireBetween(arguments, 0, Integer.MAX_VALUE, "unsubscribe");
        for (byte[] channel : arguments) {
            if (channel == null) {
                throw new CommandException("ERR invalid channel");
            }
        }
        List<ClientReply> acknowledgements =
                pubSubBroker.unsubscribe(session, arguments.toArray(byte[][]::new));
        if (acknowledgements.isEmpty()) {
            return new ClientReply.CompositeArray(
                    List.of(
                            new ClientReply.BulkString("unsubscribe".getBytes()),
                            new ClientReply.NullBulk(),
                            new ClientReply.Integer(0)));
        }
        if (acknowledgements.size() == 1) {
            return acknowledgements.getFirst();
        }
        return new ClientReply.Sequence(acknowledgements);
    }
}
