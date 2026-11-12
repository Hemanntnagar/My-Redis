package com.myredis.replication;

/**
 * Role, replication offset, and replica connection management.
 */
public interface ReplicationCoordinator {

    ReplicationRole role();

    long replicationOffset();
}
