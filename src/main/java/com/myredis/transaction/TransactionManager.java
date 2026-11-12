package com.myredis.transaction;

import com.myredis.network.ClientSession;

/**
 * Per-session transaction queues and optimistic locking (WATCH).
 */
public interface TransactionManager {

    void multi(ClientSession session);

    void discard(ClientSession session);

    boolean inTransaction(ClientSession session);
}
