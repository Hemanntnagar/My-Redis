package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EchoCommandTest {

    private final EchoCommand command = new EchoCommand();
    private final ClientSession session = new StubSession();

    @Test
    void echoReturnsMessageAsBulkString() throws CommandException {
        byte[] message = "world".getBytes(StandardCharsets.UTF_8);
        ClientReply reply = command.execute(session, List.of(message));
        assertInstanceOf(ClientReply.BulkString.class, reply);
        assertArrayEquals(message, ((ClientReply.BulkString) reply).value());
    }

    @Test
    void echoRequiresExactlyOneArgument() {
        CommandException failure = assertThrows(CommandException.class, () -> command.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'echo' command", failure.clientMessage());
    }

    private static final class StubSession implements ClientSession {

        @Override
        public String id() {
            return "test";
        }

        @Override
        public java.nio.channels.SocketChannel channel() {
            throw new UnsupportedOperationException();
        }
    }
}
