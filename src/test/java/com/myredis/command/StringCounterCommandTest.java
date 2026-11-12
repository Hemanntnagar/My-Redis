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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringCounterCommandTest {

    private static final byte[] KEY = "counter".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MISSING = "absent".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private IncrCommand incrCommand;
    private DecrCommand decrCommand;
    private IncrByCommand incrByCommand;
    private DecrByCommand decrByCommand;
    private StrlenCommand strlenCommand;
    private GetCommand getCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        incrCommand = new IncrCommand(store);
        decrCommand = new DecrCommand(store);
        incrByCommand = new IncrByCommand(store);
        decrByCommand = new DecrByCommand(store);
        strlenCommand = new StrlenCommand(store);
        getCommand = new GetCommand(store);
        session = new StubSession();
    }

    @Test
    void incrOnMissingKeySetsOne() throws CommandException {
        ClientReply reply = incrCommand.execute(session, List.of(MISSING));
        assertEquals(1L, ((ClientReply.Integer) reply).value());
        assertStored(MISSING, "1");
    }

    @Test
    void decrOnMissingKeySetsNegativeOne() throws CommandException {
        ClientReply reply = decrCommand.execute(session, List.of(MISSING));
        assertEquals(-1L, ((ClientReply.Integer) reply).value());
    }

    @Test
    void incrByPreservesStoredEncoding() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("42".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply =
                incrByCommand.execute(
                        session,
                        List.of(KEY, "8".getBytes(StandardCharsets.UTF_8)));
        assertEquals(50L, ((ClientReply.Integer) reply).value());
        assertStored(KEY, "50");
    }

    @Test
    void decrBySubtractsAmount() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("10".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply =
                decrByCommand.execute(
                        session,
                        List.of(KEY, "4".getBytes(StandardCharsets.UTF_8)));
        assertEquals(6L, ((ClientReply.Integer) reply).value());
    }

    @Test
    void incrRejectsNonIntegerStringValue() throws StorageException {
        store.put(KEY, new StringRedisValue("not-a-number".getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(CommandException.class, () -> incrCommand.execute(session, List.of(KEY)));
        assertEquals(StringIntegerValues.NOT_INTEGER, failure.clientMessage());
    }

    @Test
    void incrByRejectsInvalidIncrementArgument() throws StorageException {
        store.put(KEY, new StringRedisValue("1".getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () ->
                                incrByCommand.execute(
                                        session,
                                        List.of(KEY, "12x".getBytes(StandardCharsets.UTF_8))));
        assertEquals(StringIntegerValues.NOT_INTEGER, failure.clientMessage());
    }

    @Test
    void incrOverflowsAtLongMax() throws StorageException {
        store.put(KEY, new StringRedisValue(Long.toString(Long.MAX_VALUE).getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(CommandException.class, () -> incrCommand.execute(session, List.of(KEY)));
        assertEquals(StringIntegerValues.OVERFLOW, failure.clientMessage());
    }

    @Test
    void decrOverflowsAtLongMin() throws StorageException {
        store.put(KEY, new StringRedisValue(Long.toString(Long.MIN_VALUE).getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(CommandException.class, () -> decrCommand.execute(session, List.of(KEY)));
        assertEquals(StringIntegerValues.OVERFLOW, failure.clientMessage());
    }

    @Test
    void incrByAcceptsSignedIncrementArgument() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("100".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply =
                incrByCommand.execute(
                        session,
                        List.of(KEY, "-30".getBytes(StandardCharsets.UTF_8)));
        assertEquals(70L, ((ClientReply.Integer) reply).value());
    }

    @Test
    void incrByZeroLeavesValueUnchanged() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("+7".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply =
                incrByCommand.execute(
                        session,
                        List.of(KEY, "0".getBytes(StandardCharsets.UTF_8)));
        assertEquals(7L, ((ClientReply.Integer) reply).value());
        assertStored(KEY, "7");
    }

    @Test
    void incrRejectsEmptyStringValue() throws StorageException {
        store.put(KEY, new StringRedisValue(new byte[0]));
        CommandException failure =
                assertThrows(CommandException.class, () -> incrCommand.execute(session, List.of(KEY)));
        assertEquals(StringIntegerValues.NOT_INTEGER, failure.clientMessage());
    }

    @Test
    void strlenReturnsZeroForMissingKey() throws CommandException {
        ClientReply reply = strlenCommand.execute(session, List.of(MISSING));
        assertEquals(0L, ((ClientReply.Integer) reply).value());
    }

    @Test
    void strlenReturnsByteLengthForBinaryValue() throws CommandException, StorageException {
        byte[] value = new byte[] {'a', 0, 'b', (byte) 0xFF};
        store.put(KEY, new StringRedisValue(value));
        ClientReply reply = strlenCommand.execute(session, List.of(KEY));
        assertEquals(value.length, ((ClientReply.Integer) reply).value());
    }

    @Test
    void strlenRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> strlenCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'strlen' command", failure.clientMessage());
    }

    @Test
    void incrRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> incrCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'incr' command", failure.clientMessage());
    }

    private void assertStored(byte[] key, String expected) throws CommandException {
        ClientReply reply = getCommand.execute(session, List.of(key));
        ClientReply.BulkString bulk = assertInstanceOf(ClientReply.BulkString.class, reply);
        assertEquals(expected, new String(bulk.value(), StandardCharsets.UTF_8));
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
