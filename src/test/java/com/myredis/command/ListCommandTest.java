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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListCommandTest {

    private static final byte[] KEY = "items".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MISSING = "nolist".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private LPushCommand lPushCommand;
    private RPushCommand rPushCommand;
    private LPopCommand lPopCommand;
    private RPopCommand rPopCommand;
    private LLenCommand lLenCommand;
    private LRangeCommand lRangeCommand;
    private LIndexCommand lIndexCommand;
    private LSetCommand lSetCommand;
    private TypeCommand typeCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        lPushCommand = new LPushCommand(store);
        rPushCommand = new RPushCommand(store);
        lPopCommand = new LPopCommand(store);
        rPopCommand = new RPopCommand(store);
        lLenCommand = new LLenCommand(store);
        lRangeCommand = new LRangeCommand(store);
        lIndexCommand = new LIndexCommand(store);
        lSetCommand = new LSetCommand(store);
        typeCommand = new TypeCommand(store);
        session = new StubSession();
    }

    @Test
    void pushAndPopMaintainHeadTailOrder() throws CommandException {
        lPushCommand.execute(session, List.of(KEY, el("a"), el("b")));
        rPushCommand.execute(session, List.of(KEY, el("c")));
        assertEquals(3L, integer(lLenCommand.execute(session, List.of(KEY))));

        assertEquals("b", bulk(lPopCommand.execute(session, List.of(KEY))));
        assertEquals("c", bulk(rPopCommand.execute(session, List.of(KEY))));
        assertEquals(1L, integer(lLenCommand.execute(session, List.of(KEY))));
    }

    @Test
    void lPopAndLLenOnMissingOrEmptyKey() throws CommandException {
        assertInstanceOf(ClientReply.NullBulk.class, lPopCommand.execute(session, List.of(MISSING)));
        assertEquals(0L, integer(lLenCommand.execute(session, List.of(MISSING))));

        lPushCommand.execute(session, List.of(KEY, el("only")));
        lPopCommand.execute(session, List.of(KEY));
        assertInstanceOf(ClientReply.NullBulk.class, lPopCommand.execute(session, List.of(KEY)));
        assertEquals(0L, integer(lLenCommand.execute(session, List.of(KEY))));
    }

    @Test
    void lRangeHonorsInclusiveBoundsAndNegativeIndexes() throws CommandException {
        rPushCommand.execute(session, List.of(KEY, el("0"), el("1"), el("2"), el("3")));
        List<String> middle = decodeArray(lRangeCommand.execute(session, List.of(KEY, el("1"), el("2"))));
        assertEquals(List.of("1", "2"), middle);

        List<String> tail = decodeArray(lRangeCommand.execute(session, List.of(KEY, el("-2"), el("-1"))));
        assertEquals(List.of("2", "3"), tail);

        List<String> missing = decodeArray(lRangeCommand.execute(session, List.of(MISSING, el("0"), el("-1"))));
        assertEquals(List.of(), missing);
    }

    @Test
    void lRangeReturnsEmptyWhenStartBeyondList() throws CommandException {
        rPushCommand.execute(session, List.of(KEY, el("a")));
        List<String> empty = decodeArray(lRangeCommand.execute(session, List.of(KEY, el("5"), el("6"))));
        assertEquals(List.of(), empty);
    }

    @Test
    void lIndexSupportsNegativeIndexAndNilOnMissingKey() throws CommandException {
        rPushCommand.execute(session, List.of(KEY, el("x"), el("y")));
        assertEquals("y", bulk(lIndexCommand.execute(session, List.of(KEY, el("-1")))));
        assertInstanceOf(ClientReply.NullBulk.class, lIndexCommand.execute(session, List.of(KEY, el("9"))));
        assertInstanceOf(ClientReply.NullBulk.class, lIndexCommand.execute(session, List.of(MISSING, el("0"))));
    }

    @Test
    void lSetUpdatesElementOrRejectsOutOfRange() throws CommandException {
        rPushCommand.execute(session, List.of(KEY, el("a"), el("b")));
        assertInstanceOf(ClientReply.SimpleString.class, lSetCommand.execute(session, List.of(KEY, el("1"), el("B"))));
        assertEquals("B", bulk(lIndexCommand.execute(session, List.of(KEY, el("1")))));

        CommandException missing =
                assertThrows(
                        CommandException.class,
                        () -> lSetCommand.execute(session, List.of(MISSING, el("0"), el("z"))));
        assertEquals(ListCommandSupport.NO_SUCH_KEY, missing.clientMessage());

        CommandException outOfRange =
                assertThrows(
                        CommandException.class,
                        () -> lSetCommand.execute(session, List.of(KEY, el("4"), el("z"))));
        assertEquals(ListCommandSupport.INDEX_OUT_OF_RANGE, outOfRange.clientMessage());
    }

    @Test
    void pushAgainstStringKeyReturnsWrongType() throws StorageException {
        store.put(KEY, new StringRedisValue("text".getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () -> lPushCommand.execute(session, List.of(KEY, el("v"))));
        assertEquals(ListCommandSupport.WRONGTYPE, failure.clientMessage());
    }

    @Test
    void typeReportsList() throws CommandException {
        lPushCommand.execute(session, List.of(KEY, el("v")));
        ClientReply.SimpleString reply =
                assertInstanceOf(ClientReply.SimpleString.class, typeCommand.execute(session, List.of(KEY)));
        assertEquals("list", reply.value());
    }

    private static long integer(ClientReply reply) {
        return ((ClientReply.Integer) reply).value();
    }

    private static String bulk(ClientReply reply) {
        return new String(((ClientReply.BulkString) reply).value(), StandardCharsets.UTF_8);
    }

    private static List<String> decodeArray(ClientReply reply) {
        ClientReply.Array array = assertInstanceOf(ClientReply.Array.class, reply);
        List<String> values = new ArrayList<>(array.elements().size());
        for (byte[] element : array.elements()) {
            values.add(new String(element, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static byte[] el(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
