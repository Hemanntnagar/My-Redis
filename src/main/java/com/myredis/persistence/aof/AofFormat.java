package com.myredis.persistence.aof;

/**
 * On-disk append-only log constants.
 */
public final class AofFormat {

    public static final byte[] MAGIC = new byte[] {'M', 'Y', 'R', 'A', 'O', 'F', '\0', '\0'};
    public static final int VERSION = 1;
    public static final String FILE_NAME = "appendonly.aof";
    public static final String REWRITE_TEMP_SUFFIX = ".rewrite";

    private AofFormat() {}
}
