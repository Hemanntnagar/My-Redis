package com.myredis.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainExceptionTest {

    @Test
    void eachTypeReportsExpectedCategory() {
        assertEquals(ErrorCategory.PROTOCOL, new ProtocolException("bad bulk").category());
        assertEquals(ErrorCategory.COMMAND, new CommandException("wrong arity").category());
        assertEquals(ErrorCategory.STORAGE, new StorageException("WRONGTYPE").category());
        assertEquals(ErrorCategory.CONNECTION, new ConnectionException("read timed out").category());
        assertEquals(ErrorCategory.PERSISTENCE, new PersistenceException("load failed").category());
        assertEquals(ErrorCategory.CONFIGURATION, new InvalidConfigurationException("port invalid").category());
    }

    @Test
    void persistenceWrapPreservesCause() {
        var cause = new java.io.IOException("denied");
        PersistenceException ex = PersistenceException.wrap("failed to flush", cause);
        assertEquals(ErrorCategory.PERSISTENCE, ex.category());
        assertEquals("failed to flush", ex.clientMessage());
        assertEquals(cause, ex.getCause());
    }
}
