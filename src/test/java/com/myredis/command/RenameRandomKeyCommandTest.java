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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameRandomKeyCommandTest {

    private InMemoryKeyValueStore store;
    private RenameCommand renameCommand;
    private RenameNxCommand renameNxCommand;
    private RandomKeyCommand randomKeyCommand;
    private GetCommand getCommand;
    private ExistsCommand existsCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        renameCommand = new RenameCommand(store);
        renameNxCommand = new RenameNxCommand(store);
        randomKeyCommand = new RandomKeyCommand(store);
        getCommand = new GetCommand(store);
        existsCommand = new ExistsCommand(store);
        session = new StubSession();
    }

    @Test
    void renameMovesValueAndOverwritesDestination() throws CommandException, StorageException {
        byte[] source = key("from");
        byte[] destination = key("to");
        byte[] other = key("other");
        byte[] payload = new byte[] {'b', 0x00, (byte) 0xFF};
        store.put(source, new StringRedisValue(payload));
        store.put(destination, new StringRedisValue("old".getBytes(StandardCharsets.UTF_8)));
        store.put(other, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(ClientReply.SimpleString.class, renameCommand.execute(session, List.of(source, destination)));
        assertFalse(store.exists(source));
        assertTrue(store.exists(destination));

        ClientReply.BulkString got = assertInstanceOf(ClientReply.BulkString.class, getCommand.execute(session, List.of(destination)));
        assertArrayEquals(payload, got.value());
        assertEquals(1L, ((ClientReply.Integer) existsCommand.execute(session, List.of(other))).value());
    }

    @Test
    void renameSameKeySucceedsWhenKeyExists() throws CommandException, StorageException {
        byte[] name = key("stable");
        store.put(name, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        assertInstanceOf(ClientReply.SimpleString.class, renameCommand.execute(session, List.of(name, name)));
        assertTrue(store.exists(name));
    }

    @Test
    void renameFailsWhenSourceMissing() {
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () -> renameCommand.execute(session, List.of(key("missing"), key("to"))));
        assertEquals("ERR no such key", failure.clientMessage());
    }

    @Test
    void renamenxPreservesSourceWhenDestinationExists() throws CommandException, StorageException {
        byte[] source = key("src");
        byte[] destination = key("dst");
        store.put(source, new StringRedisValue("value".getBytes(StandardCharsets.UTF_8)));
        store.put(destination, new StringRedisValue("taken".getBytes(StandardCharsets.UTF_8)));

        ClientReply reply = renameNxCommand.execute(session, List.of(source, destination));
        assertEquals(0L, ((ClientReply.Integer) reply).value());
        assertTrue(store.exists(source));
        assertTrue(store.exists(destination));

        ClientReply.BulkString untouched =
                assertInstanceOf(ClientReply.BulkString.class, getCommand.execute(session, List.of(destination)));
        assertEquals("taken", new String(untouched.value(), StandardCharsets.UTF_8));
    }

    @Test
    void renamenxReturnsOneAndMovesValue() throws CommandException, StorageException {
        byte[] source = key("old");
        byte[] destination = key("new");
        store.put(source, new StringRedisValue("payload".getBytes(StandardCharsets.UTF_8)));

        ClientReply reply = renameNxCommand.execute(session, List.of(source, destination));
        assertEquals(1L, ((ClientReply.Integer) reply).value());
        assertFalse(store.exists(source));
        assertTrue(store.exists(destination));
    }

    @Test
    void renamenxSameKeyReturnsZero() throws CommandException, StorageException {
        byte[] name = key("same");
        store.put(name, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        ClientReply reply = renameNxCommand.execute(session, List.of(name, name));
        assertEquals(0L, ((ClientReply.Integer) reply).value());
    }

    @Test
    void randomKeyReturnsNullBulkOnEmptyDatabase() throws CommandException {
        ClientReply reply = randomKeyCommand.execute(session, List.of());
        assertInstanceOf(ClientReply.NullBulk.class, reply);
    }

    @Test
    void randomKeyReturnsOneOfExistingKeys() throws CommandException, StorageException {
        store.put(key("a"), new StringRedisValue("1".getBytes(StandardCharsets.UTF_8)));
        store.put(key("b"), new StringRedisValue("2".getBytes(StandardCharsets.UTF_8)));
        store.put(key("c"), new StringRedisValue("3".getBytes(StandardCharsets.UTF_8)));

        Set<String> allowed = Set.of("a", "b", "c");
        Set<String> observed = new HashSet<>();
        for (int attempt = 0; attempt < 40; attempt++) {
            ClientReply.BulkString reply =
                    assertInstanceOf(ClientReply.BulkString.class, randomKeyCommand.execute(session, List.of()));
            observed.add(new String(reply.value(), StandardCharsets.UTF_8));
        }
        assertTrue(allowed.containsAll(observed) && !observed.isEmpty());
    }

    @Test
    void renameRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> renameCommand.execute(session, List.of(key("a"))));
        assertEquals("ERR wrong number of arguments for 'rename' command", failure.clientMessage());
    }

    private static byte[] key(String name) {
        return name.getBytes(StandardCharsets.UTF_8);
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
