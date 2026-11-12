package com.myredis.persistence.aof;

import com.myredis.exception.PersistenceException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Parsed AOF file header including optional snapshot baseline for combined recovery. */
public record AofRecoveryHeader(
        int formatVersion,
        int baselineSnapshotCrc32,
        int baselineRecordCount) {

    public static AofRecoveryHeader parse(byte[] fileBytes) throws PersistenceException {
        if (fileBytes.length < AofFormat.MAGIC.length) {
            throw new PersistenceException("ERR AOF file too short");
        }
        for (int index = 0; index < AofFormat.MAGIC.length; index++) {
            if (fileBytes[index] != AofFormat.MAGIC[index]) {
                throw new PersistenceException("ERR invalid AOF magic");
            }
        }
        if (fileBytes.length < AofFormat.VERSION_1_HEADER_LENGTH) {
            throw new PersistenceException("ERR AOF header truncated");
        }
        int version =
                ByteBuffer.wrap(fileBytes, AofFormat.MAGIC.length, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (version == AofFormat.VERSION_1) {
            return new AofRecoveryHeader(AofFormat.VERSION_1, 0, 0);
        }
        if (version != AofFormat.VERSION_2) {
            throw new PersistenceException("ERR unsupported AOF version " + version);
        }
        if (fileBytes.length < AofFormat.VERSION_2_HEADER_LENGTH) {
            throw new PersistenceException("ERR AOF v2 header truncated");
        }
        int offset = AofFormat.VERSION_1_HEADER_LENGTH;
        int baselineCrc =
                ByteBuffer.wrap(fileBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        int baselineRecords =
                ByteBuffer.wrap(fileBytes, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (baselineRecords < 0) {
            throw new PersistenceException("ERR invalid AOF baseline record count");
        }
        return new AofRecoveryHeader(AofFormat.VERSION_2, baselineCrc, baselineRecords);
    }

    public int headerLength() {
        return formatVersion >= AofFormat.VERSION_2
                ? AofFormat.VERSION_2_HEADER_LENGTH
                : AofFormat.VERSION_1_HEADER_LENGTH;
    }

    /**
     * How many records to skip when a snapshot with matching CRC is loaded first.
     */
    public int recordsToSkipAfterSnapshotLoad(int loadedSnapshotCrc32, boolean snapshotLoaded) {
        if (!snapshotLoaded || baselineSnapshotCrc32 == 0) {
            return 0;
        }
        if (loadedSnapshotCrc32 != baselineSnapshotCrc32) {
            return 0;
        }
        return baselineRecordCount;
    }
}
