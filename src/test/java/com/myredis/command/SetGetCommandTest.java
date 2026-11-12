package com.myredis.command;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetGetCommandTest {

    private static final byte[] KEY = "user:1".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private SetCommand setCommand;
    private GetCommand getCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        setCommand = new SetCommand(store);
        getCommand = new GetCommand(store);
        session = new StubSession();
    }

    @Test
    void setStoresValueAndReturnsOk() throws CommandException {
        byte[] value = "alice".getBytes(StandardCharsets.UTF_8);
        ClientReply reply = setCommand.execute(session, List.of(KEY, value));
        assertInstanceOf(ClientReply.SimpleString.class, reply);
        assertEquals("OK", ((ClientReply.SimpleString) reply).value());
    }

    @Test
    void getReturnsNullBulkForMissingKey() throws CommandException {
        ClientReply reply = getCommand.execute(session, List.of(KEY));
        assertInstanceOf(ClientReply.NullBulk.class, reply);
    }

    @Test
    void getReturnsStoredValue() throws CommandException, StorageException {
        byte[] value = "hello".getBytes(StandardCharsets.UTF_8);
        store.put(KEY, new StringRedisValue(value));

        ClientReply reply = getCommand.execute(session, List.of(KEY));
        assertInstanceOf(ClientReply.BulkString.class, reply);
        assertArrayEquals(value, ((ClientReply.BulkString) reply).value());
    }

    @Test
    void setOverwritesExistingValue() throws CommandException {
        byte[] first = "one".getBytes(StandardCharsets.UTF_8);
        byte[] second = "two".getBytes(StandardCharsets.UTF_8);
        setCommand.execute(session, List.of(KEY, first));
        setCommand.execute(session, List.of(KEY, second));

        ClientReply reply = getCommand.execute(session, List.of(KEY));
        assertInstanceOf(ClientReply.BulkString.class, reply);
        assertArrayEquals(second, ((ClientReply.BulkString) reply).value());
    }

    @Test
    void setAndGetPreserveBinaryBytes() throws CommandException {
        byte[] binary = new byte[] {0x00, 0x01, (byte) 0xFF, 0x0A};
        setCommand.execute(session, List.of(KEY, binary));

        ClientReply reply = getCommand.execute(session, List.of(KEY));
        assertInstanceOf(ClientReply.BulkString.class, reply);
        assertArrayEquals(binary, ((ClientReply.BulkString) reply).value());
    }

    @Test
    void setRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> setCommand.execute(session, List.of(KEY)));
        assertEquals("ERR wrong number of arguments for 'set' command", failure.clientMessage());
    }

    @Test
    void getRejectsWrongArity() {
        byte[] value = "v".getBytes(StandardCharsets.UTF_8);
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () -> getCommand.execute(session, List.of(KEY, value)));
        assertEquals("ERR wrong number of arguments for 'get' command", failure.clientMessage());
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
