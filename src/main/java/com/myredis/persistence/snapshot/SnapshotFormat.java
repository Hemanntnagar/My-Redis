package com.myredis.persistence.snapshot;

/**
 * On-disk snapshot constants; see {@code docs/SNAPSHOT.md}.
 */
public final class SnapshotFormat {

    public static final byte[] MAGIC = new byte[] {'M', 'Y', 'R', 'D', 'S', 'N', 'A', 'P'};
    public static final int VERSION = 1;
    public static final String FILE_NAME = "dump.myredis";
    public static final String TEMP_SUFFIX = ".tmp";

    public static final byte TYPE_STRING = 0;
    public static final byte TYPE_LIST = 1;
    public static final byte TYPE_SET = 2;
    public static final byte TYPE_HASH = 3;
    public static final byte TYPE_SORTED_SET = 4;

    public static final long NO_EXPIRE = -1L;

    private SnapshotFormat() {}
}
