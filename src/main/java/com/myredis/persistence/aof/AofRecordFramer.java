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
        byte[] header = new byte[AofFormat.MAGIC.length + 4];
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        buffer.put(AofFormat.MAGIC);
        buffer.putInt(AofFormat.VERSION);
        return header;
    }

    public static int headerLength() {
        return AofFormat.MAGIC.length + 4;
    }

    public static boolean hasValidHeader(byte[] fileBytes) {
        if (fileBytes.length < headerLength()) {
            return false;
        }
        for (int index = 0; index < AofFormat.MAGIC.length; index++) {
            if (fileBytes[index] != AofFormat.MAGIC[index]) {
                return false;
            }
        }
        int version = ByteBuffer.wrap(fileBytes, AofFormat.MAGIC.length, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        return version == AofFormat.VERSION;
    }

    public record ReadResult(List<byte[]> commandPayloads, int ignoredTrailingBytes) {}

    public static ReadResult readAllRecords(byte[] fileBytes) {
        if (fileBytes.length == 0) {
            return new ReadResult(List.of(), 0);
        }
        if (!hasValidHeader(fileBytes)) {
            return new ReadResult(List.of(), fileBytes.length);
        }
        int offset = headerLength();
        List<byte[]> records = new ArrayList<>();
        while (offset + LENGTH_FIELD_BYTES <= fileBytes.length) {
            int length = ByteBuffer.wrap(fileBytes, offset, LENGTH_FIELD_BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
            offset += LENGTH_FIELD_BYTES;
            if (length < 0 || offset + length > fileBytes.length) {
                int ignored = fileBytes.length - (offset - LENGTH_FIELD_BYTES);
                return new ReadResult(List.copyOf(records), ignored);
            }
            byte[] payload = new byte[length];
            System.arraycopy(fileBytes, offset, payload, 0, length);
            records.add(payload);
            offset += length;
        }
        int ignored = fileBytes.length - offset;
        return new ReadResult(List.copyOf(records), ignored);
    }
}
