package com.myredis.persistence.snapshot;

import java.util.zip.CRC32;

/** CRC32 helpers for on-disk snapshot files (full file bytes including footer). */
public final class SnapshotFileIntegrity {

    private SnapshotFileIntegrity() {}

    public static int crc32OfFileBytes(byte[] onDiskBytes) {
        CRC32 crc = new CRC32();
        crc.update(onDiskBytes);
        return (int) crc.getValue();
    }
}
