package com.myredis.persistence.snapshot;

import com.myredis.exception.PersistenceException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicSnapshotFiles {

    private AtomicSnapshotFiles() {}

    public static Path snapshotPath(Path directory) {
        return directory.resolve(SnapshotFormat.FILE_NAME);
    }

    public static void writeAtomically(Path directory, byte[] bytes) throws PersistenceException {
        try {
            Files.createDirectories(directory);
            Path target = snapshotPath(directory);
            Path temp = directory.resolve(SnapshotFormat.FILE_NAME + SnapshotFormat.TEMP_SUFFIX);
            writeAndSync(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to write snapshot", failure);
        }
    }

    public static byte[] readIfPresent(Path directory) throws PersistenceException {
        Path file = snapshotPath(directory);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to read snapshot", failure);
        }
    }

    private static void writeAndSync(Path temp, byte[] bytes) throws IOException {
        try (FileChannel channel =
                FileChannel.open(
                        temp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
