package com.myredis.persistence;

import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.datatype.StringRedisValue;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.aof.AofFsyncPolicy;
import com.myredis.persistence.aof.AppendOnlyFile;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CompositeRecoveryIntegrationTest {

    @TempDir
    Path tempDir;

    private ExpirationAwareKeyValueStore store;
    private InMemoryExpirationService expiration;
    private AppendOnlyFile appendOnlyFile;
    private SnapshotPersistenceManager snapshot;
    private CommandClientRequestProcessor processor;
    private long digestBeforeRestart;

    @BeforeEach
    void setUp() throws Exception {
        expiration = new InMemoryExpirationService();
        store = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration);
        appendOnlyFile = AppendOnlyFile.open(tempDir, AofFsyncPolicy.ALWAYS);
        snapshot = new SnapshotPersistenceManager(tempDir, store, expiration);
        processor =
                CommandClientRequestProcessor.withBuiltinCommands(
                        new CompositePersistenceManager(
                                snapshot,
                                appendOnlyFile,
                                store,
                                expiration,
                                com.myredis.expiration.SystemExpirationClock.INSTANCE),
                        appendOnlyFile,
                        store,
                        expiration,
                        com.myredis.expiration.SystemExpirationClock.INSTANCE);
        seedAllTypes();
        digestBeforeRestart = KeyspaceIntegrityDigest.checksum(store, expiration);
    }

    @AfterEach
    void tearDown() {
        appendOnlyFile.close();
    }

    @Test
    void snapshotPlusAofRecoversAllTypesWithoutDuplicateBaselineReplay() throws Exception {
        ClientSession session = new StubSession();
        assertInstanceOf(ClientReply.Integer.class, processor.process(session, cmd("INCR", "counter")));
        assertInstanceOf(ClientReply.SimpleString.class, processor.process(session, cmd("SAVE")));
        appendOnlyFile.fsync();
        assertInstanceOf(ClientReply.SimpleString.class, processor.process(session, cmd("SET", "after", "save")));
        appendOnlyFile.fsync();

        ExpirationAwareKeyValueStore recoveredStore = freshStore();
        InMemoryExpirationService recoveredExpiration = recoveredStore.expirationService();
        CompositePersistenceManager loader =
                new CompositePersistenceManager(
                        new SnapshotPersistenceManager(tempDir, recoveredStore, recoveredExpiration),
                        AppendOnlyFile.open(tempDir, AofFsyncPolicy.NO),
                        recoveredStore,
                        recoveredExpiration,
                        com.myredis.expiration.SystemExpirationClock.INSTANCE);
        loader.load();

        long digestAfter = KeyspaceIntegrityDigest.checksum(recoveredStore, recoveredExpiration);
        assertEquals(7, recoveredStore.keyCount());
        assertEquals("3", stringValue(recoveredStore, "counter"));
        assertEquals("save", stringValue(recoveredStore, "after"));
        assertNotEquals(digestBeforeRestart, digestAfter);
        loader.close();
    }

    @Test
    void saveWithoutDuplicateIncrFromBaselineAof() throws Exception {
        ClientSession session = new StubSession();
        for (int index = 0; index < 5; index++) {
            processor.process(session, cmd("INCR", "n"));
        }
        processor.process(session, cmd("SAVE"));
        appendOnlyFile.fsync();

        ExpirationAwareKeyValueStore recoveredStore = freshStore();
        InMemoryExpirationService recoveredExpiration = recoveredStore.expirationService();
        CompositePersistenceManager loader =
                new CompositePersistenceManager(
                        new SnapshotPersistenceManager(tempDir, recoveredStore, recoveredExpiration),
                        AppendOnlyFile.open(tempDir, AofFsyncPolicy.NO),
                        recoveredStore,
                        recoveredExpiration,
                        com.myredis.expiration.SystemExpirationClock.INSTANCE);
        loader.load();
        assertEquals("5", stringValue(recoveredStore, "n"));
        loader.close();
    }

    private void seedAllTypes() throws Exception {
        ClientSession session = new StubSession();
        processor.process(session, cmd("SET", "str", "hello"));
        processor.process(session, cmd("SET", "counter", "2"));
        processor.process(session, cmd("RPUSH", "list", "a", "b"));
        processor.process(session, cmd("SADD", "set", "x", "y"));
        processor.process(session, cmd("HSET", "hash", "f", "v"));
        processor.process(session, cmd("ZADD", "zset", "1.5", "m"));
        appendOnlyFile.fsync();
    }

    private ExpirationAwareKeyValueStore freshStore() {
        return new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), new InMemoryExpirationService());
    }

    private static List<byte[]> cmd(String... parts) {
        return java.util.Arrays.stream(parts).map(p -> p.getBytes(StandardCharsets.UTF_8)).toList();
    }

    private static String stringValue(ExpirationAwareKeyValueStore store, String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        return new String(((StringRedisValue) store.get(raw).orElseThrow()).bytes(), StandardCharsets.UTF_8);
    }

    private static final class StubSession implements ClientSession {
        @Override
        public String id() {
            return "recovery-test";
        }

        @Override
        public java.nio.channels.SocketChannel channel() {
            throw new UnsupportedOperationException();
        }
    }
}
