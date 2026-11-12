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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MsetMgetAppendCommandTest {

    private static final byte[] KEY_A = "a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_B = "b".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_MISSING = "missing".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private MsetCommand msetCommand;
    private MgetCommand mgetCommand;
    private AppendCommand appendCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        msetCommand = new MsetCommand(store);
        mgetCommand = new MgetCommand(store);
        appendCommand = new AppendCommand(store);
        session = new StubSession();
    }

    @Test
    void msetStoresMultiplePairsInOrderAndReturnsOk() throws CommandException {
        byte[] va = "1".getBytes(StandardCharsets.UTF_8);
        byte[] vb = "2".getBytes(StandardCharsets.UTF_8);
        ClientReply reply =
                msetCommand.execute(session, List.of(KEY_A, va, KEY_B, vb));
        assertInstanceOf(ClientReply.SimpleString.class, reply);
        assertEquals("OK", ((ClientReply.SimpleString) reply).value());

        ClientReply fetched =
                mgetCommand.execute(session, List.of(KEY_A, KEY_B, KEY_MISSING));
        ClientReply.Array array = assertInstanceOf(ClientReply.Array.class, fetched);
        assertArrayEquals(va, array.elements().get(0));
        assertArrayEquals(vb, array.elements().get(1));
        assertNull(array.elements().get(2));
    }

    @Test
    void mgetPreservesKeyOrder() throws CommandException, StorageException {
        store.put(KEY_A, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        store.put(KEY_B, new StringRedisValue("y".getBytes(StandardCharsets.UTF_8)));

        ClientReply reply = mgetCommand.execute(session, List.of(KEY_B, KEY_MISSING, KEY_A));
        ClientReply.Array array = assertInstanceOf(ClientReply.Array.class, reply);
        assertEquals("y", new String(array.elements().get(0), StandardCharsets.UTF_8));
        assertNull(array.elements().get(1));
        assertEquals("x", new String(array.elements().get(2), StandardCharsets.UTF_8));
    }

    @Test
    void appendCreatesKeyWhenMissing() throws CommandException {
        byte[] suffix = "hi".getBytes(StandardCharsets.UTF_8);
        ClientReply reply = appendCommand.execute(session, List.of(KEY_A, suffix));
        ClientReply.Integer length = assertInstanceOf(ClientReply.Integer.class, reply);
        assertEquals(2, length.value());
    }

    @Test
    void appendConcatenatesAndReturnsLength() throws CommandException, StorageException {
        store.put(KEY_A, new StringRedisValue("foo".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply =
                appendCommand.execute(session, List.of(KEY_A, "bar".getBytes(StandardCharsets.UTF_8)));
        assertEquals(6, ((ClientReply.Integer) reply).value());

        ClientReply fetched = mgetCommand.execute(session, List.of(KEY_A));
        assertEquals("foobar", new String(((ClientReply.Array) fetched).elements().get(0), StandardCharsets.UTF_8));
    }

    @Test
    void msetRejectsOddArgumentCount() {
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () ->
                                msetCommand.execute(
                                        session,
                                        List.of(KEY_A, "v".getBytes(StandardCharsets.UTF_8), KEY_B)));
        assertEquals("ERR wrong number of arguments for 'mset' command", failure.clientMessage());
    }

    @Test
    void mgetRejectsEmptyArguments() {
        CommandException failure =
                assertThrows(CommandException.class, () -> mgetCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'mget' command", failure.clientMessage());
    }

    @Test
    void appendRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> appendCommand.execute(session, List.of(KEY_A)));
        assertEquals("ERR wrong number of arguments for 'append' command", failure.clientMessage());
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
