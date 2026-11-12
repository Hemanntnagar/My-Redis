/**
 * Durability: RDB snapshots ({@link com.myredis.persistence.SnapshotPersistenceManager}) and
 * optional AOF ({@link com.myredis.persistence.aof.AppendOnlyFile}) coordinated by
 * {@link com.myredis.persistence.CompositePersistenceManager}.
 * <p>
 * Implementations coordinate with {@link com.myredis.storage.KeyValueStore}.
 */
package com.myredis.persistence;
