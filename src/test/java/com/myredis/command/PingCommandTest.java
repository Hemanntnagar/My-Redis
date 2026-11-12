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

class PingCommandTest {

    private final PingCommand command = new PingCommand();
    private final ClientSession session = new StubSession();

    @Test
    void pingWithoutArgumentsReturnsPong() throws CommandException {
        ClientReply reply = command.execute(session, List.of());
        assertInstanceOf(ClientReply.SimpleString.class, reply);
        assertEquals("PONG", ((ClientReply.SimpleString) reply).value());
    }

    @Test
    void pingWithMessageReturnsBulkString() throws CommandException {
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        ClientReply reply = command.execute(session, List.of(message));
        assertInstanceOf(ClientReply.BulkString.class, reply);
        assertArrayEquals(message, ((ClientReply.BulkString) reply).value());
    }

    @Test
    void pingRejectsTooManyArguments() {
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () ->
                                command.execute(
                                        session,
                                        List.of("a".getBytes(StandardCharsets.UTF_8), "b".getBytes(StandardCharsets.UTF_8))));
        assertEquals("ERR wrong number of arguments for 'ping' command", failure.clientMessage());
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
