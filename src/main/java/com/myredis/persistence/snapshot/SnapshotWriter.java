package com.myredis.persistence.snapshot;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.PersistenceException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.CRC32;

public final class SnapshotWriter {

    private SnapshotWriter() {}

    public static byte[] encode(DatabaseSnapshot snapshot) throws PersistenceException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(SnapshotFormat.MAGIC);
            out.writeShort(SnapshotFormat.VERSION);
            out.writeLong(snapshot.entries().size());
            for (SnapshotEntry entry : snapshot.entries()) {
                writeEntry(out, entry);
            }
            out.flush();
            byte[] body = buffer.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(body);
            ByteArrayOutputStream framedBuffer = new ByteArrayOutputStream(body.length + 4);
            DataOutputStream framedOut = new DataOutputStream(framedBuffer);
            framedOut.write(body);
            framedOut.writeInt((int) crc.getValue());
            framedOut.flush();
            return framedBuffer.toByteArray();
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to encode snapshot", failure);
        }
    }

    private static void writeEntry(DataOutputStream out, SnapshotEntry entry) throws IOException {
        writeBytes(out, entry.key());
        out.writeLong(entry.expireAtEpochMillis());
        RedisValue value = entry.value();
        switch (value.type()) {
            case STRING -> writeString(out, (StringRedisValue) value);
            case LIST -> writeList(out, (ListRedisValue) value);
            case SET -> writeSet(out, (SetRedisValue) value);
            case HASH -> writeHash(out, (HashRedisValue) value);
            case SORTED_SET -> writeSortedSet(out, (SortedSetRedisValue) value);
            default -> throw new IOException("unsupported type for snapshot: " + value.type());
        }
    }

    private static void writeString(DataOutputStream out, StringRedisValue value) throws IOException {
        out.writeByte(SnapshotFormat.TYPE_STRING);
        writeBytes(out, value.bytes());
    }

    private static void writeList(DataOutputStream out, ListRedisValue value) throws IOException {
        out.writeByte(SnapshotFormat.TYPE_LIST);
        List<byte[]> elements = value.snapshotElementsInOrder();
        out.writeInt(elements.size());
        for (byte[] element : elements) {
            writeBytes(out, element);
        }
    }

    private static void writeSet(DataOutputStream out, SetRedisValue value) throws IOException {
        out.writeByte(SnapshotFormat.TYPE_SET);
        List<byte[]> members = value.membersSorted();
        out.writeInt(members.size());
        for (byte[] member : members) {
            writeBytes(out, member);
        }
    }

    private static void writeHash(DataOutputStream out, HashRedisValue value) throws IOException {
        out.writeByte(SnapshotFormat.TYPE_HASH);
        List<byte[]> flat = value.getAllFlat();
        out.writeInt(flat.size() / 2);
        for (int index = 0; index < flat.size(); index += 2) {
            writeBytes(out, flat.get(index));
            writeBytes(out, flat.get(index + 1));
        }
    }

    private static void writeSortedSet(DataOutputStream out, SortedSetRedisValue value) throws IOException {
        out.writeByte(SnapshotFormat.TYPE_SORTED_SET);
        List<SortedSetRedisValue.ScoredMemberSnapshot> members = value.snapshotByRank();
        out.writeInt(members.size());
        for (SortedSetRedisValue.ScoredMemberSnapshot member : members) {
            out.writeDouble(member.score());
            writeBytes(out, member.member());
        }
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
