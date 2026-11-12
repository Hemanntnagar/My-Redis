package com.myredis.persistence.aof;

import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.command.RegistryCommandDispatcher;
import com.myredis.datatype.StringRedisValue;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.CompositePersistenceManager;
import com.myredis.persistence.SnapshotPersistenceManager;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AofCrashRecoveryTest {

    @TempDir
    Path tempDir;

    private ExpirationAwareKeyValueStore store;
    private InMemoryExpirationService expiration;
    private AppendOnlyFile appendOnlyFile;
    private CommandClientRequestProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        expiration = new InMemoryExpirationService();
        store = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration);
        appendOnlyFile = AppendOnlyFile.open(tempDir, AofFsyncPolicy.ALWAYS);
        SnapshotPersistenceManager snapshot = new SnapshotPersistenceManager(tempDir, store, expiration);
        processor =
                CommandClientRequestProcessor.withBuiltinCommands(
                        snapshot, appendOnlyFile, store, expiration, com.myredis.expiration.SystemExpirationClock.INSTANCE);
    }

    @AfterEach
    void tearDown() {
        appendOnlyFile.close();
    }

    @Test
    void replayRestoresSuccessfulWrites() throws Exception {
        ClientSession session = new StubSession();
        assertInstanceOf(
                ClientReply.SimpleString.class,
                processor.process(session, command("SET", "user:1", "alice")));
        assertInstanceOf(
                ClientReply.SimpleString.class,
                processor.process(session, command("SET", "user:2", "bob")));
        appendOnlyFile.fsync();

        InMemoryExpirationService recoveredExpiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore recovered =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), recoveredExpiration);
        CompositePersistenceManager loader =
                new CompositePersistenceManager(
                        new SnapshotPersistenceManager(tempDir, recovered, recoveredExpiration),
                        AppendOnlyFile.open(tempDir, AofFsyncPolicy.NO),
                        recovered,
                        recoveredExpiration,
                        com.myredis.expiration.SystemExpirationClock.INSTANCE);
        loader.load();

        assertEquals("alice", valueOf(recovered, "user:1"));
        assertEquals("bob", valueOf(recovered, "user:2"));
        loader.close();
    }

    @Test
    void incompleteTrailingRecordIsIgnoredOnRecovery() throws Exception {
        ClientSession session = new StubSession();
        processor.process(session, command("SET", "stable", "yes"));
        appendOnlyFile.fsync();
        processor.process(session, command("SET", "lost", "maybe"));

        Path file = tempDir.resolve(AofFormat.FILE_NAME);
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 5));

        ExpirationAwareKeyValueStore recoveredStore = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), new InMemoryExpirationService());
        InMemoryExpirationService recoveredExpiration = new InMemoryExpirationService();
        recoveredStore = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), recoveredExpiration);
        AofReplayer.ReplaySummary summary =
                AofReplayer.replay(Files.readAllBytes(file), dispatcher(recoveredStore, recoveredExpiration));

        assertEquals("yes", valueOf(recoveredStore, "stable"));
        assertTrue(summary.incompleteTrailingBytes() > 0);
        assertEquals(1, summary.recordsApplied());
    }

    @Test
    void rewriteCompactsLog() throws Exception {
        ClientSession session = new StubSession();
        for (int index = 0; index < 50; index++) {
            processor.process(session, command("SET", "k", Integer.toString(index)));
        }
        long before = Files.size(tempDir.resolve(AofFormat.FILE_NAME));

        appendOnlyFile.rewriteBlocking(store, expiration);
        long after = Files.size(tempDir.resolve(AofFormat.FILE_NAME));

        assertTrue(after < before);

        ExpirationAwareKeyValueStore recovered =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), new InMemoryExpirationService());
        InMemoryExpirationService recoveredExpiration = new InMemoryExpirationService();
        CompositePersistenceManager loader =
                new CompositePersistenceManager(
                        new SnapshotPersistenceManager(tempDir, recovered, recoveredExpiration),
                        AppendOnlyFile.open(tempDir, AofFsyncPolicy.NO),
                        recovered,
                        recoveredExpiration,
                        com.myredis.expiration.SystemExpirationClock.INSTANCE);
        loader.load();
        assertEquals("49", valueOf(recovered, "k"));
        loader.close();
    }

    @Test
    void bgrewriteaofCommandCompletes() throws Exception {
        ClientSession session = new StubSession();
        processor.process(session, command("SET", "bg", "ok"));
        assertInstanceOf(
                ClientReply.SimpleString.class,
                RegistryCommandDispatcher.withBuiltinCommands(store, expiration, com.myredis.expiration.SystemExpirationClock.INSTANCE, new SnapshotPersistenceManager(tempDir, store, expiration), appendOnlyFile)
                        .dispatch(session, "BGREWRITEAOF", List.of()));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (appendOnlyFile.isRewriteInProgress() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(Files.isRegularFile(tempDir.resolve(AofFormat.FILE_NAME)));
    }

    private static RegistryCommandDispatcher dispatcher(
            ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration) {
        return RegistryCommandDispatcher.withBuiltinCommands(
                store,
                expiration,
                com.myredis.expiration.SystemExpirationClock.INSTANCE,
                new SnapshotPersistenceManager(Path.of("."), store, expiration));
    }

    private static List<byte[]> command(String... parts) {
        return java.util.Arrays.stream(parts).map(part -> part.getBytes(StandardCharsets.UTF_8)).toList();
    }

    private static String valueOf(ExpirationAwareKeyValueStore store, String key) throws Exception {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        return new String(((StringRedisValue) store.get(raw).orElseThrow()).bytes(), StandardCharsets.UTF_8);
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
