package com.myredis.persistence;

import com.myredis.exception.PersistenceException;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.persistence.snapshot.AtomicSnapshotFiles;
import com.myredis.persistence.snapshot.DatabaseSnapshot;
import com.myredis.persistence.snapshot.KeyspaceSnapshotter;
import com.myredis.persistence.snapshot.SnapshotReader;
import com.myredis.persistence.snapshot.SnapshotWriter;
import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Snapshot persistence: atomic {@code dump.myredis} files with checksum validation.
 */
public final class SnapshotPersistenceManager implements PersistenceManager, SnapshotCommandSupport {

    private final Path persistenceDirectory;
    private final ExpirationAwareKeyValueStore store;
    private final InMemoryExpirationService expiration;
    private final AtomicBoolean backgroundSaveInProgress = new AtomicBoolean();
    private final AtomicReference<Thread> backgroundThread = new AtomicReference<>();

    public SnapshotPersistenceManager(
            String persistenceDirectory,
            ExpirationAwareKeyValueStore store,
            InMemoryExpirationService expiration) {
        this(Path.of(Objects.requireNonNull(persistenceDirectory, "persistenceDirectory")), store, expiration);
    }

    public SnapshotPersistenceManager(
            Path persistenceDirectory, ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration) {
        this.persistenceDirectory = Objects.requireNonNull(persistenceDirectory, "persistenceDirectory");
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
    }

    public Path persistenceDirectory() {
        return persistenceDirectory;
    }

    public byte[] readSnapshotBytesIfPresent() throws PersistenceException {
        return AtomicSnapshotFiles.readIfPresent(persistenceDirectory);
    }

    @Override
    public void load() throws PersistenceException {
        byte[] encoded = AtomicSnapshotFiles.readIfPresent(persistenceDirectory);
        if (encoded == null) {
            return;
        }
        DatabaseSnapshot snapshot = SnapshotReader.decode(encoded);
        try {
            KeyspaceSnapshotter.restore(store, expiration, snapshot);
        } catch (RuntimeException failure) {
            throw PersistenceException.wrap("ERR failed to apply snapshot", failure);
        }
    }

    @Override
    public void flush() throws PersistenceException {
        saveBlocking();
    }

    public void saveBlocking() throws PersistenceException {
        DatabaseSnapshot snapshot = KeyspaceSnapshotter.capture(store, expiration);
        byte[] encoded = SnapshotWriter.encode(snapshot);
        AtomicSnapshotFiles.writeAtomically(persistenceDirectory, encoded);
    }

    public boolean beginBackgroundSave() {
        if (!backgroundSaveInProgress.compareAndSet(false, true)) {
            return false;
        }
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                saveBlocking();
                            } catch (PersistenceException failure) {
                                System.err.println("Background save failed: " + failure.clientMessage());
                            } finally {
                                backgroundSaveInProgress.set(false);
                                backgroundThread.set(null);
                            }
                        },
                        "myredis-bgsave");
        worker.setDaemon(true);
        backgroundThread.set(worker);
        worker.start();
        return true;
    }

    public boolean isBackgroundSaveInProgress() {
        return backgroundSaveInProgress.get();
    }
}
