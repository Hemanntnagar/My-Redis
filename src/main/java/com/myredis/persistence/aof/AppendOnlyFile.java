package com.myredis.persistence.aof;

import com.myredis.exception.PersistenceException;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.persistence.snapshot.DatabaseSnapshot;
import com.myredis.persistence.snapshot.KeyspaceSnapshotter;
import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Append-only command log with configurable fsync, crash-safe framing, and rewrite.
 */
public final class AppendOnlyFile {

    private final Path directory;
    private final AofFsyncPolicy fsyncPolicy;
    private final Object writeLock = new Object();
    private final AtomicBoolean backgroundFsyncRunning = new AtomicBoolean();
    private final AtomicBoolean fsyncRequested = new AtomicBoolean();
    private final AtomicReference<Thread> backgroundFsyncThread = new AtomicReference<>();
    private final AtomicBoolean rewriteInProgress = new AtomicBoolean();
    private volatile FileChannel channel;

    public AppendOnlyFile(Path directory, AofFsyncPolicy fsyncPolicy) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.fsyncPolicy = Objects.requireNonNull(fsyncPolicy, "fsyncPolicy");
    }

    public static AppendOnlyFile open(Path directory, AofFsyncPolicy fsyncPolicy) throws PersistenceException {
        AppendOnlyFile log = new AppendOnlyFile(directory, fsyncPolicy);
        log.openChannel();
        if (fsyncPolicy == AofFsyncPolicy.EVERY_SEC) {
            log.startBackgroundFsync();
        }
        return log;
    }

    public Path filePath() {
        return directory.resolve(AofFormat.FILE_NAME);
    }

    public AofFsyncPolicy fsyncPolicy() {
        return fsyncPolicy;
    }

    public void append(List<byte[]> commandParts) throws PersistenceException {
        Objects.requireNonNull(commandParts, "commandParts");
        if (commandParts.isEmpty()) {
            return;
        }
        byte[] framed = AofRecordFramer.frame(AofRespCommandCodec.encode(commandParts));
        synchronized (writeLock) {
            ensureChannel();
            try {
                ByteBuffer buffer = ByteBuffer.wrap(framed);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            } catch (IOException failure) {
                throw PersistenceException.wrap("ERR failed to append AOF record", failure);
            }
            applyFsyncPolicyAfterWrite();
        }
    }

    public void fsync() throws PersistenceException {
        synchronized (writeLock) {
            forceChannel();
        }
    }

    public byte[] readFileBytes() throws PersistenceException {
        Path file = filePath();
        if (!Files.isRegularFile(file)) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to read AOF", failure);
        }
    }

    public boolean beginRewrite(ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration) {
        if (!rewriteInProgress.compareAndSet(false, true)) {
            return false;
        }
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                rewriteBlocking(store, expiration);
                            } catch (PersistenceException failure) {
                                System.err.println("AOF rewrite failed: " + failure.clientMessage());
                            } finally {
                                rewriteInProgress.set(false);
                            }
                        },
                        "myredis-aof-rewrite");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    public boolean isRewriteInProgress() {
        return rewriteInProgress.get();
    }

    public void rewriteBlocking(ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration)
            throws PersistenceException {
        rewriteBlocking(store, expiration, 0);
    }

    public void rewriteBlocking(
            ExpirationAwareKeyValueStore store, InMemoryExpirationService expiration, int baselineSnapshotCrc32)
            throws PersistenceException {
        DatabaseSnapshot snapshot = KeyspaceSnapshotter.capture(store, expiration);
        List<List<byte[]>> commands = AofRewriteGenerator.commandsFor(snapshot);
        Path rewriteTemp = directory.resolve(AofFormat.FILE_NAME + AofFormat.REWRITE_TEMP_SUFFIX);
        try {
            Files.createDirectories(directory);
            byte[] body = buildFileBody(commands, baselineSnapshotCrc32);
            writeAndFsync(rewriteTemp, body);
            synchronized (writeLock) {
                closeChannel();
                try {
                    Files.move(
                            rewriteTemp,
                            filePath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException failure) {
                    Files.move(rewriteTemp, filePath(), StandardCopyOption.REPLACE_EXISTING);
                }
                openChannel();
            }
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to rewrite AOF", failure);
        }
    }

    public void close() {
        synchronized (writeLock) {
            stopBackgroundFsync();
            closeChannel();
        }
    }

    private byte[] buildFileBody(List<List<byte[]>> commands, int baselineSnapshotCrc32) {
        int capacity = AofRecordFramer.headerLength() + commands.size() * 128;
        ByteBuffer buffer = ByteBuffer.allocate(Math.max(capacity, 256));
        buffer.put(AofRecordFramer.fileHeader(baselineSnapshotCrc32, commands.size()));
        for (List<byte[]> command : commands) {
            buffer.put(AofRecordFramer.frame(AofRespCommandCodec.encode(command)));
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void openChannel() throws PersistenceException {
        try {
            Files.createDirectories(directory);
            Path file = filePath();
            boolean created = !Files.exists(file);
            channel =
                    FileChannel.open(
                            file,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE);
            if (created || channel.size() == 0) {
                ByteBuffer header = ByteBuffer.wrap(AofRecordFramer.fileHeader());
                while (header.hasRemaining()) {
                    channel.write(header);
                }
                channel.force(true);
            } else {
                channel.position(channel.size());
            }
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to open AOF", failure);
        }
    }

    private void ensureChannel() throws PersistenceException {
        if (channel == null || !channel.isOpen()) {
            openChannel();
        }
    }

    private void closeChannel() {
        FileChannel current = channel;
        channel = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private void applyFsyncPolicyAfterWrite() throws PersistenceException {
        switch (fsyncPolicy) {
            case ALWAYS -> forceChannel();
            case EVERY_SEC -> fsyncRequested.set(true);
            case NO -> { /* no-op */ }
        }
    }

    private void forceChannel() throws PersistenceException {
        if (channel == null) {
            return;
        }
        try {
            channel.force(false);
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to fsync AOF", failure);
        }
    }

    private static void writeAndFsync(Path target, byte[] bytes) throws IOException {
        try (FileChannel out =
                FileChannel.open(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                out.write(buffer);
            }
            out.force(true);
        }
    }

    private void startBackgroundFsync() {
        if (!backgroundFsyncRunning.compareAndSet(false, true)) {
            return;
        }
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                while (backgroundFsyncRunning.get()) {
                                    Thread.sleep(1_000L);
                                    if (fsyncRequested.compareAndSet(true, false)) {
                                        try {
                                            fsync();
                                        } catch (PersistenceException failure) {
                                            System.err.println("Background AOF fsync failed: " + failure.clientMessage());
                                        }
                                    }
                                }
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            } finally {
                                backgroundFsyncThread.set(null);
                            }
                        },
                        "myredis-aof-fsync");
        worker.setDaemon(true);
        backgroundFsyncThread.set(worker);
        worker.start();
    }

    private void stopBackgroundFsync() {
        backgroundFsyncRunning.set(false);
        Thread worker = backgroundFsyncThread.getAndSet(null);
        if (worker != null) {
            worker.interrupt();
        }
    }
}
