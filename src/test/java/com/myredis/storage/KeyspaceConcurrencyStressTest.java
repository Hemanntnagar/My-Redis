package com.myredis.storage;

import com.myredis.command.IncrCommand;
import com.myredis.command.DelCommand;
import com.myredis.command.GetCommand;
import com.myredis.command.SetCommand;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyspaceConcurrencyStressTest {

    private ExpirationAwareKeyValueStore store;
    private InMemoryExpirationService expiration;

    @BeforeEach
    void setUp() {
        expiration = new InMemoryExpirationService();
        store = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration);
    }

    @Test
    void concurrentIncrOnSharedKeyIsLinearizable() throws Exception {
        byte[] key = "counter".getBytes(StandardCharsets.UTF_8);
        IncrCommand incr = new IncrCommand(store);
        ClientSession session = new StubSession();
        int threads = 32;
        int incrementsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int worker = 0; worker < threads; worker++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int index = 0; index < incrementsPerThread; index++) {
                                ClientReply reply = incr.execute(session, List.of(key));
                                if (!(reply instanceof ClientReply.Integer)) {
                                    errors.incrementAndGet();
                                }
                            }
                        } catch (Exception failure) {
                            errors.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(0, errors.get());
        assertEquals(
                (long) threads * incrementsPerThread,
                Long.parseLong(
                        new String(
                                ((StringRedisValue) store.get(key).orElseThrow()).bytes(), StandardCharsets.UTF_8)));
    }

    @Test
    void mixedReadsWritesDeletesAndExpirations() throws Exception {
        SetCommand set = new SetCommand(store);
        GetCommand get = new GetCommand(store);
        DelCommand del = new DelCommand(store);
        ClientSession session = new StubSession();
        byte[] hot = "hot".getBytes(StandardCharsets.UTF_8);
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger anomalies = new AtomicInteger();

        for (int worker = 0; worker < threads; worker++) {
            final int id = worker;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int round = 0; round < 100; round++) {
                                byte[] key = ("k:" + id + ":" + (round % 7)).getBytes(StandardCharsets.UTF_8);
                                if (round % 5 == 0) {
                                    expiration.register(key, System.currentTimeMillis() + 86_400_000L);
                                }
                                if (round % 3 == 0) {
                                    set.execute(session, List.of(key, "v".getBytes(StandardCharsets.UTF_8)));
                                } else if (round % 3 == 1) {
                                    get.execute(session, List.of(key));
                                } else {
                                    del.execute(session, List.of(key));
                                }
                                if (round % 11 == 0) {
                                    store.activeExpireCycle(8, 4);
                                }
                            }
                            set.execute(session, List.of(hot, Integer.toString(id).getBytes(StandardCharsets.UTF_8)));
                        } catch (CommandException failure) {
                            anomalies.incrementAndGet();
                        } catch (Exception failure) {
                            anomalies.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertTrue(done.await(90, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(0, anomalies.get());
        assertTrue(store.exists(hot));
    }

    private static final class StubSession implements ClientSession {
        @Override
        public String id() {
            return "stress";
        }

        @Override
        public java.nio.channels.SocketChannel channel() {
            throw new UnsupportedOperationException();
        }
    }
}
