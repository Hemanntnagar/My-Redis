package com.myredis.persistence.aof;

/**
 * On-disk append-only log constants.
 */
public final class AofFormat {

    public static final byte[] MAGIC = new byte[] {'M', 'Y', 'R', 'A', 'O', 'F', '\0', '\0'};
    public static final int VERSION_1 = 1;
    /** Includes snapshot baseline CRC and record count to skip duplicate replay after {@code SAVE}. */
    public static final int VERSION_2 = 2;
    public static final int VERSION = VERSION_2;
    public static final int VERSION_1_HEADER_LENGTH = MAGIC.length + 4;
    public static final int VERSION_2_HEADER_LENGTH = VERSION_1_HEADER_LENGTH + 8;
    public static final String FILE_NAME = "appendonly.aof";
    public static final String REWRITE_TEMP_SUFFIX = ".rewrite";

    private AofFormat() {}
}
