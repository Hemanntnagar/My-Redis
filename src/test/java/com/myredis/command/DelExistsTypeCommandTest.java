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

class DelExistsTypeCommandTest {

    private static final byte[] KEY_A = "a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_B = "b".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_MISSING = "missing".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private DelCommand delCommand;
    private ExistsCommand existsCommand;
    private TypeCommand typeCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        delCommand = new DelCommand(store);
        existsCommand = new ExistsCommand(store);
        typeCommand = new TypeCommand(store);
        session = new StubSession();
    }

    @Test
    void delRemovesExistingKey() throws CommandException, StorageException {
        store.put(KEY_A, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply = delCommand.execute(session, List.of(KEY_A));
        assertInteger(reply, 1);
        assertEquals(false, store.exists(KEY_A));
    }

    @Test
    void delIsNoOpForMissingKey() throws CommandException {
        ClientReply reply = delCommand.execute(session, List.of(KEY_MISSING));
        assertInteger(reply, 0);
    }

    @Test
    void delRemovesMultipleKeysAndReturnsCount() throws CommandException, StorageException {
        store.put(KEY_A, new StringRedisValue("1".getBytes(StandardCharsets.UTF_8)));
        store.put(KEY_B, new StringRedisValue("2".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply = delCommand.execute(session, List.of(KEY_A, KEY_B, KEY_MISSING));
        assertInteger(reply, 2);
    }

    @Test
    void delRejectsEmptyArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> delCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'del' command", failure.clientMessage());
    }

    @Test
    void existsReturnsZeroForMissingKeys() throws CommandException {
        ClientReply reply = existsCommand.execute(session, List.of(KEY_MISSING));
        assertInteger(reply, 0);
    }

    @Test
    void existsCountsPresentKeys() throws CommandException, StorageException {
        store.put(KEY_A, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply = existsCommand.execute(session, List.of(KEY_A, KEY_B, KEY_MISSING));
        assertInteger(reply, 1);
    }

    @Test
    void existsRejectsEmptyArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> existsCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'exists' command", failure.clientMessage());
    }

    @Test
    void typeReturnsNoneForMissingKey() throws CommandException {
        ClientReply reply = typeCommand.execute(session, List.of(KEY_MISSING));
        assertInstanceOf(ClientReply.SimpleString.class, reply);
        assertEquals("none", ((ClientReply.SimpleString) reply).value());
    }

    @Test
    void typeReturnsStringForStringValue() throws CommandException, StorageException {
        store.put(KEY_A, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply = typeCommand.execute(session, List.of(KEY_A));
        assertInstanceOf(ClientReply.SimpleString.class, reply);
        assertEquals("string", ((ClientReply.SimpleString) reply).value());
    }

    @Test
    void typeRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> typeCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'type' command", failure.clientMessage());
    }

    private static void assertInteger(ClientReply reply, long expected) {
        assertInstanceOf(ClientReply.Integer.class, reply);
        assertEquals(expected, ((ClientReply.Integer) reply).value());
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
