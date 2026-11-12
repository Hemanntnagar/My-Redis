package com.myredis.persistence;

import com.myredis.exception.PersistenceException;

/** Operations exposed by {@code SAVE} and {@code BGSAVE}. */
public interface SnapshotCommandSupport {

    void saveBlocking() throws PersistenceException;

    boolean beginBackgroundSave();

    boolean isBackgroundSaveInProgress();
}
