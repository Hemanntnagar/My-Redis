package com.myredis.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorResponseMapperTest {

    @Test
    void mapsDomainExceptionsToClientMessage() {
        var ex = new CommandException("ERR unknown command 'foo'");
        assertEquals("ERR unknown command 'foo'", ErrorResponseMapper.toClientErrorMessage(ex));
    }

    @Test
    void hidesUnexpectedThrowables() {
        var ex = new RuntimeException("secret db password leaked");
        assertEquals(ErrorResponseMapper.GENERIC_CLIENT_ERROR, ErrorResponseMapper.toClientErrorMessage(ex));
    }

    @Test
    void sanitizeStripsStackLikeContent() {
        String contaminated = "bad input\n\tat com.myredis.Foo.bar(Foo.java:1)";
        assertEquals(ErrorResponseMapper.GENERIC_CLIENT_ERROR, ErrorResponseMapper.sanitizeForClient(contaminated));
    }

    @Test
    void sanitizeCollapsesNewlines() {
        assertEquals("line one line two", ErrorResponseMapper.sanitizeForClient("line one\nline two"));
    }

    @Test
    void domainExceptionClientMessageOmitsInternalDetail() {
        var ex = new PersistenceException(
                "unable to persist dataset",
                "disk full on /var/data",
                new IllegalStateException("No space left"));
        assertEquals("unable to persist dataset", ex.clientMessage());
        assertEquals("disk full on /var/data", ex.getMessage());
        String client = ErrorResponseMapper.toClientErrorMessage(ex);
        assertFalse(client.contains("No space left"));
        assertFalse(client.contains("\tat "));
    }
}
