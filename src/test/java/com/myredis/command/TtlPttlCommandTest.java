package com.myredis.command;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationReplies;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtlPttlCommandTest {

    private static final byte[] KEY = "k".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MISSING = "missing".getBytes(StandardCharsets.UTF_8);

    private MutableExpirationClock clock;
    private InMemoryExpirationService expiration;
    private ExpirationAwareKeyValueStore store;
    private TtlCommand ttlCommand;
    private PttlCommand pttlCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        clock = new MutableExpirationClock(10_000L);
        expiration = new InMemoryExpirationService();
        store = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration, clock);
        ttlCommand = new TtlCommand(store, expiration, clock);
        pttlCommand = new PttlCommand(store, expiration, clock);
        session = new StubSession();
    }

    @Test
    void ttlAndPttlReturnKeyMissingForAbsentKey() throws CommandException {
        assertInteger(ttlCommand.execute(session, List.of(MISSING)), ExpirationReplies.KEY_MISSING);
        assertInteger(pttlCommand.execute(session, List.of(MISSING)), ExpirationReplies.KEY_MISSING);
    }

    @Test
    void ttlAndPttlReturnNoExpireForPersistentKey() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        assertInteger(ttlCommand.execute(session, List.of(KEY)), ExpirationReplies.NO_EXPIRE);
        assertInteger(pttlCommand.execute(session, List.of(KEY)), ExpirationReplies.NO_EXPIRE);
    }

    @Test
    void ttlReturnsSecondPrecisionWithCeiling() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(KEY, clock.currentTimeMillis() + 1_500L);

        assertInteger(ttlCommand.execute(session, List.of(KEY)), 2L);
        assertInteger(pttlCommand.execute(session, List.of(KEY)), 1_500L);
    }

    @Test
    void pttlReturnsExactMillisRemaining() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(KEY, clock.currentTimeMillis() + 250L);

        assertInteger(pttlCommand.execute(session, List.of(KEY)), 250L);
        assertInteger(ttlCommand.execute(session, List.of(KEY)), 1L);
    }

    @Test
    void ttlPurgesExpiredKeyAndReturnsKeyMissing() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(KEY, clock.currentTimeMillis() + 100L);
        clock.advance(100L);

        assertInteger(ttlCommand.execute(session, List.of(KEY)), ExpirationReplies.KEY_MISSING);
        assertFalse(store.exists(KEY));
        assertTrue(expiration.findMetadata(KEY).isEmpty());
    }

    @Test
    void pttlPurgesExpiredKeyAndReturnsKeyMissing() throws CommandException, StorageException {
        store.put(KEY, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(KEY, clock.currentTimeMillis() + 50L);
        clock.advance(50L);

        assertInteger(pttlCommand.execute(session, List.of(KEY)), ExpirationReplies.KEY_MISSING);
        assertFalse(store.exists(KEY));
    }

    @Test
    void ttlRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> ttlCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'ttl' command", failure.clientMessage());
    }

    @Test
    void pttlRejectsWrongArity() {
        CommandException failure =
                assertThrows(CommandException.class, () -> pttlCommand.execute(session, List.of()));
        assertEquals("ERR wrong number of arguments for 'pttl' command", failure.clientMessage());
    }

    @Test
    void renamePreservesExpirationMetadata() throws CommandException, StorageException {
        byte[] source = "from".getBytes(StandardCharsets.UTF_8);
        byte[] destination = "to".getBytes(StandardCharsets.UTF_8);
        store.put(source, new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        expiration.register(source, clock.currentTimeMillis() + 5_000L);

        new RenameCommand(store).execute(session, List.of(source, destination));

        assertFalse(store.exists(source));
        assertInteger(ttlCommand.execute(session, List.of(destination)), 5L);
        assertInteger(pttlCommand.execute(session, List.of(destination)), 5_000L);
    }

    private static void assertInteger(ClientReply reply, long expected) {
        assertInstanceOf(ClientReply.Integer.class, reply);
        assertEquals(expected, ((ClientReply.Integer) reply).value());
    }

    private static final class MutableExpirationClock implements ExpirationClock {

        private long nowMillis;

        private MutableExpirationClock(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        void advance(long deltaMillis) {
            nowMillis += deltaMillis;
        }

        @Override
        public long currentTimeMillis() {
            return nowMillis;
        }
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
