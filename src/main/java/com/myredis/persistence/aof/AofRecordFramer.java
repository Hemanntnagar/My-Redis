package com.myredis.persistence.aof;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Length-prefixed AOF records so crash recovery can ignore a partial trailing frame.
 */
public final class AofRecordFramer {

    public static final int LENGTH_FIELD_BYTES = 4;

    private AofRecordFramer() {}

    public static byte[] frame(byte[] respPayload) {
        byte[] framed = new byte[LENGTH_FIELD_BYTES + respPayload.length];
        ByteBuffer.wrap(framed).order(ByteOrder.BIG_ENDIAN).putInt(respPayload.length).put(respPayload);
        return framed;
    }

    public static byte[] fileHeader() {
        return fileHeader(0, 0);
    }

    public static byte[] fileHeader(int baselineSnapshotCrc32, int baselineRecordCount) {
        byte[] header = new byte[AofFormat.VERSION_2_HEADER_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        buffer.put(AofFormat.MAGIC);
        buffer.putInt(AofFormat.VERSION_2);
        buffer.putInt(baselineSnapshotCrc32);
        buffer.putInt(baselineRecordCount);
        return header;
    }

    public static int headerLength() {
        return AofFormat.VERSION_2_HEADER_LENGTH;
    }

    public static boolean hasValidHeader(byte[] fileBytes) {
        if (fileBytes.length < AofFormat.VERSION_1_HEADER_LENGTH) {
            return false;
        }
        for (int index = 0; index < AofFormat.MAGIC.length; index++) {
            if (fileBytes[index] != AofFormat.MAGIC[index]) {
                return false;
            }
        }
        int version = ByteBuffer.wrap(fileBytes, AofFormat.MAGIC.length, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        return version == AofFormat.VERSION_1 || version == AofFormat.VERSION_2;
    }

    public record ReadResult(List<byte[]> commandPayloads, int ignoredTrailingBytes) {}

    public static ReadResult readAllRecords(byte[] fileBytes) {
        return readAllRecords(fileBytes, 0);
    }

    public static ReadResult readAllRecords(byte[] fileBytes, int skipCompleteRecords) {
        if (fileBytes.length == 0) {
            return new ReadResult(List.of(), 0);
        }
        if (!hasValidHeader(fileBytes)) {
            return new ReadResult(List.of(), fileBytes.length);
        }
        int headerBytes;
        try {
            headerBytes = AofRecoveryHeader.parse(fileBytes).headerLength();
        } catch (com.myredis.exception.PersistenceException failure) {
            return new ReadResult(List.of(), fileBytes.length);
        }
        int offset = headerBytes;
        List<byte[]> records = new ArrayList<>();
        int skipped = 0;
        while (offset + LENGTH_FIELD_BYTES <= fileBytes.length) {
            int length = ByteBuffer.wrap(fileBytes, offset, LENGTH_FIELD_BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
            offset += LENGTH_FIELD_BYTES;
            if (length < 0 || offset + length > fileBytes.length) {
                int ignored = fileBytes.length - (offset - LENGTH_FIELD_BYTES);
                return new ReadResult(List.copyOf(records), ignored);
            }
            byte[] payload = new byte[length];
            System.arraycopy(fileBytes, offset, payload, 0, length);
            offset += length;
            if (skipped < skipCompleteRecords) {
                skipped++;
                continue;
            }
            records.add(payload);
        }
        int ignored = fileBytes.length - offset;
        return new ReadResult(List.copyOf(records), ignored);
    }
}
