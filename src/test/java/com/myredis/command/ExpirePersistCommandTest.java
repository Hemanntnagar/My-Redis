package com.myredis.command;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.datatype.SortedSetRedisValue;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpirePersistCommandTest {

    private static final byte[] MISSING = "missing".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TIME_60 = "60".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TIME_5_000 = "5000".getBytes(StandardCharsets.UTF_8);

    private MutableExpirationClock clock;
    private InMemoryExpirationService expiration;
    private ExpirationAwareKeyValueStore store;
    private ExpireCommand expireCommand;
    private PexpireCommand pexpireCommand;
    private ExpireAtCommand expireAtCommand;
    private PexpireAtCommand pexpireAtCommand;
    private PersistCommand persistCommand;
    private TtlCommand ttlCommand;
    private PttlCommand pttlCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        clock = new MutableExpirationClock(1_000_000L);
        expiration = new InMemoryExpirationService();
        store = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration, clock);
        expireCommand = new ExpireCommand(store, expiration, clock);
        pexpireCommand = new PexpireCommand(store, expiration, clock);
        expireAtCommand = new ExpireAtCommand(store, expiration, clock);
        pexpireAtCommand = new PexpireAtCommand(store, expiration, clock);
        persistCommand = new PersistCommand(store, expiration);
        ttlCommand = new TtlCommand(store, expiration, clock);
        pttlCommand = new PttlCommand(store, expiration, clock);
        session = new StubSession();
    }

    @Test
    void expireFamilyReturnsZeroForMissingKey() throws CommandException {
        byte[] key = key("absent");
        assertInteger(expireCommand.execute(session, List.of(key, TIME_60)), 0L);
        assertInteger(pexpireCommand.execute(session, List.of(key, TIME_5_000)), 0L);
        assertInteger(expireAtCommand.execute(session, List.of(key, ascii("2000"))), 0L);
        assertInteger(pexpireAtCommand.execute(session, List.of(key, ascii("2000000"))), 0L);
    }

    @Test
    void expireSetsTtlOnEveryValueType() throws CommandException, StorageException {
        assertExpireWorks(key("string"), new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        ListRedisValue list = new ListRedisValue();
        list.pushHead(new byte[][] {"a".getBytes(StandardCharsets.UTF_8)});
        assertExpireWorks(key("list"), list);
        SetRedisValue set = new SetRedisValue();
        set.add("m".getBytes(StandardCharsets.UTF_8));
        assertExpireWorks(key("set"), set);
        HashRedisValue hash = new HashRedisValue();
        hash.set("f".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));
        assertExpireWorks(key("hash"), hash);
        SortedSetRedisValue zset = new SortedSetRedisValue();
        zset.add(1.0, "m".getBytes(StandardCharsets.UTF_8));
        assertExpireWorks(key("zset"), zset);
    }

    @Test
    void pexpireSetsMillisecondDeadline() throws CommandException, StorageException {
        byte[] key = key("timed");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertInteger(pexpireCommand.execute(session, List.of(key, TIME_5_000)), 1L);
        assertInteger(pttlCommand.execute(session, List.of(key)), 5_000L);
        assertInteger(ttlCommand.execute(session, List.of(key)), 5L);
    }

    @Test
    void expireAtUsesUnixSeconds() throws CommandException, StorageException {
        byte[] key = key("at");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertInteger(expireAtCommand.execute(session, List.of(key, ascii("1010"))), 1L);
        assertInteger(ttlCommand.execute(session, List.of(key)), 10L);
    }

    @Test
    void pexpireAtUsesUnixMilliseconds() throws CommandException, StorageException {
        byte[] key = key("at-ms");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertInteger(pexpireAtCommand.execute(session, List.of(key, ascii("1005000"))), 1L);
        assertInteger(pttlCommand.execute(session, List.of(key)), 5_000L);
    }

    @Test
    void expireWithZeroRemovesKeyImmediately() throws CommandException, StorageException {
        byte[] key = key("zero");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertInteger(expireCommand.execute(session, List.of(key, ascii("0"))), 1L);
        assertFalse(store.exists(key));
    }

    @Test
    void expireAtInPastRemovesKeyImmediately() throws CommandException, StorageException {
        byte[] key = key("past");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertInteger(expireAtCommand.execute(session, List.of(key, ascii("500"))), 1L);
        assertFalse(store.exists(key));
    }

    @Test
    void persistRemovesExpiration() throws CommandException, StorageException {
        byte[] key = key("persist");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        expireCommand.execute(session, List.of(key, TIME_60));
        assertInteger(persistCommand.execute(session, List.of(key)), 1L);
        assertInteger(ttlCommand.execute(session, List.of(key)), ExpirationReplies.NO_EXPIRE);
    }

    @Test
    void persistReturnsZeroWithoutExistingTimeout() throws CommandException, StorageException {
        byte[] key = key("plain");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        assertInteger(persistCommand.execute(session, List.of(key)), 0L);
    }

    @Test
    void persistReturnsZeroForMissingKey() throws CommandException {
        assertInteger(persistCommand.execute(session, List.of(MISSING)), 0L);
    }

  /** Overwriting expiry via {@code EXPIRE} updates the deadline (works with every type above). */
    @Test
    void expireOverwritesPreviousDeadline() throws CommandException, StorageException {
        byte[] key = key("overwrite");
        store.put(key, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        expireCommand.execute(session, List.of(key, TIME_60));
        assertInteger(expireCommand.execute(session, List.of(key, ascii("120"))), 1L);
        assertInteger(ttlCommand.execute(session, List.of(key)), 120L);
    }

    private void assertExpireWorks(byte[] key, com.myredis.datatype.RedisValue value) throws StorageException, CommandException {
        store.put(key, value);
        assertInteger(expireCommand.execute(session, List.of(key, TIME_60)), 1L);
        assertInteger(ttlCommand.execute(session, List.of(key)), 60L);
        assertTrue(store.exists(key));
    }

    private static byte[] key(String name) {
        return name.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] ascii(String digits) {
        return digits.getBytes(StandardCharsets.US_ASCII);
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
