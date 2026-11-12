package com.myredis.command;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.SnapshotPersistenceManager;
import com.myredis.persistence.snapshot.SnapshotFormat;
import com.myredis.persistence.snapshot.SnapshotReader;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveBgsaveCommandTest {

    @TempDir
    Path tempDir;

    private ExpirationAwareKeyValueStore store;
    private InMemoryExpirationService expiration;
    private SnapshotPersistenceManager persistence;
    private SaveCommand saveCommand;
    private BgsaveCommand bgsaveCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        expiration = new InMemoryExpirationService();
        store = new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), expiration);
        persistence = new SnapshotPersistenceManager(tempDir, store, expiration);
        saveCommand = new SaveCommand(persistence);
        bgsaveCommand = new BgsaveCommand(persistence);
        session = new StubSession();
    }

    @Test
    void saveWritesSnapshotFile() throws Exception {
        store.put("k".getBytes(StandardCharsets.UTF_8), new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        assertInstanceOf(ClientReply.SimpleString.class, saveCommand.execute(session, List.of()));
        Path file = tempDir.resolve(SnapshotFormat.FILE_NAME);
        assertTrue(Files.isRegularFile(file));
        SnapshotReader.decode(Files.readAllBytes(file));
    }

    @Test
    void bgsaveCompletesInBackground() throws Exception, StorageException {
        store.put("k".getBytes(StandardCharsets.UTF_8), new StringRedisValue("v".getBytes(StandardCharsets.UTF_8)));
        assertInstanceOf(ClientReply.SimpleString.class, bgsaveCommand.execute(session, List.of()));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (persistence.isBackgroundSaveInProgress() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(Files.isRegularFile(tempDir.resolve(SnapshotFormat.FILE_NAME)));
    }

    @Test
    void bgsaveRejectsConcurrentJob() throws Exception {
        assertInstanceOf(ClientReply.SimpleString.class, bgsaveCommand.execute(session, List.of()));
        CommandException failure =
                assertThrows(CommandException.class, () -> bgsaveCommand.execute(session, List.of()));
        assertEquals("ERR Background save already in progress", failure.clientMessage());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (persistence.isBackgroundSaveInProgress() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void loadRestoresSaveOutput() throws Exception, StorageException {
        byte[] key = "boot".getBytes(StandardCharsets.UTF_8);
        store.put(key, new StringRedisValue("payload".getBytes(StandardCharsets.UTF_8)));
        expiration.register(key, System.currentTimeMillis() + 3_600_000L);
        persistence.saveBlocking();

        ExpirationAwareKeyValueStore fresh =
                new ExpirationAwareKeyValueStore(new InMemoryKeyValueStore(), new InMemoryExpirationService());
        SnapshotPersistenceManager loader = new SnapshotPersistenceManager(tempDir, fresh, fresh.expirationService());
        loader.load();

        assertEquals(1, fresh.keyCount());
        assertTrue(fresh.expirationService().expireAtMillis(key).orElseThrow() > System.currentTimeMillis());
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
