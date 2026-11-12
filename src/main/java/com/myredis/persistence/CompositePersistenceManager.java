package com.myredis.persistence;

import com.myredis.command.CommandDispatcher;
import com.myredis.command.RegistryCommandDispatcher;
import com.myredis.exception.PersistenceException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.persistence.aof.AofRecoveryHeader;
import com.myredis.persistence.aof.AofReplayer;
import com.myredis.persistence.aof.AppendOnlyFile;
import com.myredis.persistence.snapshot.SnapshotFileIntegrity;
import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.util.Objects;

/**
 * Loads snapshot state then replays the append-only log; coordinates flush and rewrite.
 */
public final class CompositePersistenceManager implements PersistenceManager, SnapshotCommandSupport {

    private final SnapshotPersistenceManager snapshot;
    private final AppendOnlyFile appendOnlyFile;
    private final ExpirationAwareKeyValueStore store;
    private final InMemoryExpirationService expiration;
    private final ExpirationClock replayClock;

    public CompositePersistenceManager(
            SnapshotPersistenceManager snapshot,
            AppendOnlyFile appendOnlyFile,
            ExpirationAwareKeyValueStore store,
            InMemoryExpirationService expiration,
            ExpirationClock replayClock) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.appendOnlyFile = appendOnlyFile;
        this.store = Objects.requireNonNull(store, "store");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
        this.replayClock = Objects.requireNonNull(replayClock, "replayClock");
    }

    public SnapshotPersistenceManager snapshot() {
        return snapshot;
    }

    public AppendOnlyFile appendOnlyFile() {
        return appendOnlyFile;
    }

    @Override
    public void load() throws PersistenceException {
        byte[] snapshotBytes = snapshot.readSnapshotBytesIfPresent();
        snapshot.load();
        if (appendOnlyFile == null) {
            validateLoadedKeyspace();
            return;
        }
        byte[] bytes = appendOnlyFile.readFileBytes();
        if (bytes.length == 0) {
            validateLoadedKeyspace();
            return;
        }
        AofRecoveryHeader header = AofRecoveryHeader.parse(bytes);
        int snapshotCrc =
                snapshotBytes == null ? 0 : SnapshotFileIntegrity.crc32OfFileBytes(snapshotBytes);
        if (snapshotBytes != null
                && header.baselineSnapshotCrc32() != 0
                && header.baselineSnapshotCrc32() != snapshotCrc) {
            throw new PersistenceException(
                    "ERR AOF baseline snapshot CRC does not match dump.myredis; run SAVE and retry");
        }
        int skip =
                header.recordsToSkipAfterSnapshotLoad(snapshotCrc, snapshotBytes != null);
        CommandDispatcher replayDispatcher =
                RegistryCommandDispatcher.withBuiltinCommands(store, expiration, replayClock, snapshot);
        AofReplayer.replay(bytes, replayDispatcher, skip);
        validateLoadedKeyspace();
    }

    private void validateLoadedKeyspace() throws PersistenceException {
        if (store.keyCount() < 0) {
            throw new PersistenceException("ERR invalid keyspace size after recovery");
        }
        if (expiration.trackedKeyCount() > store.keyCount()) {
            throw new PersistenceException("ERR expiration metadata inconsistent with keyspace");
        }
    }

    @Override
    public void flush() throws PersistenceException {
        if (appendOnlyFile != null) {
            appendOnlyFile.fsync();
        }
        snapshot.flush();
    }

    public void close() {
        if (appendOnlyFile != null) {
            appendOnlyFile.close();
        }
    }

    @Override
    public void saveBlocking() throws PersistenceException {
        snapshot.saveBlocking();
        syncAofAfterSnapshot();
    }

    @Override
    public boolean beginBackgroundSave() {
        if (!snapshot.beginBackgroundSave()) {
            return false;
        }
        Thread waiter =
                new Thread(
                        () -> {
                            while (snapshot.isBackgroundSaveInProgress()) {
                                try {
                                    Thread.sleep(10L);
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                            try {
                                syncAofAfterSnapshot();
                            } catch (PersistenceException failure) {
                                System.err.println("AOF sync after BGSAVE failed: " + failure.clientMessage());
                            }
                        },
                        "myredis-bgsave-aof-sync");
        waiter.setDaemon(true);
        waiter.start();
        return true;
    }

    @Override
    public boolean isBackgroundSaveInProgress() {
        return snapshot.isBackgroundSaveInProgress();
    }

    private void syncAofAfterSnapshot() throws PersistenceException {
        if (appendOnlyFile != null) {
            byte[] onDisk = snapshot.readSnapshotBytesIfPresent();
            int baselineCrc = onDisk == null ? 0 : SnapshotFileIntegrity.crc32OfFileBytes(onDisk);
            appendOnlyFile.rewriteBlocking(store, expiration, baselineCrc);
        }
    }
}
