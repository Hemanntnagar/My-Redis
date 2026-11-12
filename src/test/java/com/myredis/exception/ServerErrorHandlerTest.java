package com.myredis.exception;

import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerErrorHandlerTest {

    @Test
    void returnsSanitizedClientMessageWithoutLoggingFailure() {
        var handler = new ServerErrorHandler(new StructuredServerLogger());
        String reply = handler.handleFailure(
                new ProtocolException("protocol desync"),
                LogContext.builder().sessionId("s1").build());
        assertEquals("protocol desync", reply);
    }

    @Test
    void masksUnexpectedFailures() {
        var handler = new ServerErrorHandler(new StructuredServerLogger());
        String reply = handler.handleFailure(
                new IllegalStateException("thread dump follows"),
                LogContext.empty());
        assertEquals(ErrorResponseMapper.GENERIC_CLIENT_ERROR, reply);
    }
}
