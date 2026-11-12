package com.myredis.persistence.snapshot;

import com.myredis.datatype.HashRedisValue;
import com.myredis.datatype.ListRedisValue;
import com.myredis.datatype.RedisValue;
import com.myredis.datatype.SetRedisValue;
import com.myredis.datatype.SortedSetRedisValue;
import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.PersistenceException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

public final class SnapshotReader {

    private SnapshotReader() {}

    public static DatabaseSnapshot decode(byte[] fileBytes) throws PersistenceException {
        if (fileBytes.length < SnapshotFormat.MAGIC.length + 6) {
            throw new PersistenceException("ERR snapshot file is truncated or corrupted");
        }
        int crcIndex = fileBytes.length - 4;
        byte[] body = Arrays.copyOfRange(fileBytes, 0, crcIndex);
        int expectedCrc = readIntBigEndian(fileBytes, crcIndex);
        CRC32 crc = new CRC32();
        crc.update(body);
        if ((int) crc.getValue() != expectedCrc) {
            throw new PersistenceException("ERR snapshot checksum mismatch");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            byte[] magic = in.readNBytes(SnapshotFormat.MAGIC.length);
            if (!Arrays.equals(magic, SnapshotFormat.MAGIC)) {
                throw new PersistenceException("ERR snapshot file has invalid magic header");
            }
            int version = in.readUnsignedShort();
            if (version != SnapshotFormat.VERSION) {
                throw new PersistenceException("ERR unsupported snapshot version: " + version);
            }
            long count = in.readLong();
            if (count < 0 || count > Integer.MAX_VALUE) {
                throw new PersistenceException("ERR snapshot entry count is invalid");
            }
            List<SnapshotEntry> entries = new ArrayList<>((int) count);
            for (long index = 0; index < count; index++) {
                entries.add(readEntry(in));
            }
            if (in.read() != -1) {
                throw new PersistenceException("ERR snapshot file has trailing data");
            }
            return new DatabaseSnapshot(entries);
        } catch (EOFException failure) {
            throw new PersistenceException("ERR snapshot file is truncated or corrupted");
        } catch (IOException failure) {
            throw PersistenceException.wrap("ERR failed to decode snapshot", failure);
        }
    }

    private static SnapshotEntry readEntry(DataInputStream in) throws IOException, PersistenceException {
        byte[] key = readBytes(in);
        long expireAt = in.readLong();
        byte type = in.readByte();
        RedisValue value =
                switch (type) {
                    case SnapshotFormat.TYPE_STRING -> new StringRedisValue(readBytes(in));
                    case SnapshotFormat.TYPE_LIST -> readList(in);
                    case SnapshotFormat.TYPE_SET -> readSet(in);
                    case SnapshotFormat.TYPE_HASH -> readHash(in);
                    case SnapshotFormat.TYPE_SORTED_SET -> readSortedSet(in);
                    default -> throw new PersistenceException("ERR snapshot contains unknown value type: " + type);
                };
        return new SnapshotEntry(key, value, expireAt);
    }

    private static ListRedisValue readList(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("negative list length");
        }
        ListRedisValue list = new ListRedisValue();
        if (count == 0) {
            return list;
        }
        byte[][] tail = new byte[count][];
        for (int index = 0; index < count; index++) {
            tail[index] = readBytes(in);
        }
        list.pushTail(tail);
        return list;
    }

    private static SetRedisValue readSet(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("negative set length");
        }
        SetRedisValue set = new SetRedisValue();
        for (int index = 0; index < count; index++) {
            set.add(readBytes(in));
        }
        return set;
    }

    private static HashRedisValue readHash(DataInputStream in) throws IOException {
        int fieldCount = in.readInt();
        if (fieldCount < 0) {
            throw new IOException("negative hash length");
        }
        HashRedisValue hash = new HashRedisValue();
        for (int index = 0; index < fieldCount; index++) {
            hash.set(readBytes(in), readBytes(in));
        }
        return hash;
    }

    private static SortedSetRedisValue readSortedSet(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("negative zset length");
        }
        SortedSetRedisValue zset = new SortedSetRedisValue();
        for (int index = 0; index < count; index++) {
            zset.add(in.readDouble(), readBytes(in));
        }
        return zset;
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("negative byte length");
        }
        return in.readNBytes(length);
    }

    private static int readIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
