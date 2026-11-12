package com.myredis.persistence.aof;

/**
 * Controls how often the append-only log is flushed to stable storage.
 */
public enum AofFsyncPolicy {

    /** {@code fsync} after every write. */
    ALWAYS,

    /** {@code fsync} at most once per second in a background loop. */
    EVERY_SEC,

    /** Rely on the OS page cache; {@code fsync} on explicit flush or rewrite only. */
    NO
}
