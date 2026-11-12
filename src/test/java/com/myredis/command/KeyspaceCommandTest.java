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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyspaceCommandTest {

    private InMemoryKeyValueStore store;
    private KeysCommand keysCommand;
    private DbSizeCommand dbSizeCommand;
    private FlushDbCommand flushDbCommand;
    private SetCommand setCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        keysCommand = new KeysCommand(store);
        dbSizeCommand = new DbSizeCommand(store);
        flushDbCommand = new FlushDbCommand(store);
        setCommand = new SetCommand(store);
        session = new StubSession();
    }

    @Test
    void dbSizeReflectsStoredKeys() throws CommandException, StorageException {
        assertEquals(0L, integer(dbSizeCommand.execute(session, List.of())));
        store.put(key("a"), new StringRedisValue(value("1")));
        store.put(key("b"), new StringRedisValue(value("2")));
        assertEquals(2L, integer(dbSizeCommand.execute(session, List.of())));
    }

    @Test
    void keysReturnsMatchesInUnsignedByteOrder() throws CommandException, StorageException {
        store.put(key("user:2"), new StringRedisValue(value("b")));
        store.put(key("user:10"), new StringRedisValue(value("c")));
        store.put(key("user:1"), new StringRedisValue(value("a")));
        store.put(key("other"), new StringRedisValue(value("x")));

        ClientReply.Array reply =
                array(keysCommand.execute(session, List.of(pattern("user:*"))));
        List<String> names = decodeAll(reply);
        assertEquals(List.of("user:1", "user:10", "user:2"), names);
    }

    @Test
    void keysReturnsEmptyArrayWhenNothingMatches() throws CommandException {
        ClientReply.Array reply = array(keysCommand.execute(session, List.of(pattern("nope*"))));
        assertTrue(reply.elements().isEmpty());
    }

    @Test
    void flushDbRemovesAllKeys() throws CommandException, StorageException {
        store.put(key("k1"), new StringRedisValue(value("v")));
        assertInstanceOf(ClientReply.SimpleString.class, flushDbCommand.execute(session, List.of()));
        assertEquals(0L, integer(dbSizeCommand.execute(session, List.of())));
        assertTrue(array(keysCommand.execute(session, List.of(pattern("*")))).elements().isEmpty());
    }

    @Test
    void keysRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> keysCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'keys' command", failure.clientMessage());
    }

    @Test
    void concurrentWritesRemainVisibleToKeyspaceCommands() throws Exception {
        int threads = 6;
        int keysPerThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int thread = 0; thread < threads; thread++) {
            final int id = thread;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int index = 0; index < keysPerThread; index++) {
                                byte[] key = ("worker:" + id + ":" + index).getBytes(StandardCharsets.UTF_8);
                                setCommand.execute(session, List.of(key, value(String.valueOf(index))));
                            }
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals((long) threads * keysPerThread, integer(dbSizeCommand.execute(session, List.of())));
        ClientReply.Array reply = array(keysCommand.execute(session, List.of(pattern("worker:*"))));
        assertEquals(threads * keysPerThread, reply.elements().size());
    }

    @Test
    void flushDbConcurrentWithSetsEndsEmpty() throws Exception {
        AtomicBoolean flushing = new AtomicBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch done = new CountDownLatch(4);

        for (int worker = 0; worker < 3; worker++) {
            final int id = worker;
            pool.submit(
                    () -> {
                        try {
                            for (int index = 0; index < 100; index++) {
                                byte[] key = ("temp:" + id + ":" + index).getBytes(StandardCharsets.UTF_8);
                                setCommand.execute(session, List.of(key, value("v")));
                            }
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        } finally {
                            done.countDown();
                        }
                    });
        }

        pool.submit(
                () -> {
                    try {
                        TimeUnit.MILLISECONDS.sleep(5);
                        flushing.set(true);
                        flushDbCommand.execute(session, List.of());
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    } finally {
                        done.countDown();
                    }
                });

        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(flushing.get());
        assertEquals(0L, integer(dbSizeCommand.execute(session, List.of())));
    }

    private static long integer(ClientReply reply) {
        return ((ClientReply.Integer) reply).value();
    }

    private static ClientReply.Array array(ClientReply reply) {
        return assertInstanceOf(ClientReply.Array.class, reply);
    }

    private static List<String> decodeAll(ClientReply.Array reply) {
        List<String> names = new ArrayList<>(reply.elements().size());
        for (byte[] key : reply.elements()) {
            names.add(new String(key, StandardCharsets.UTF_8));
        }
        return names;
    }

    private static byte[] key(String name) {
        return name.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] value(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pattern(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
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
