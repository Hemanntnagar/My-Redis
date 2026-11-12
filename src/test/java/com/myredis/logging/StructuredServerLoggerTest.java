package com.myredis.logging;

import com.myredis.exception.ErrorCategory;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuredServerLoggerTest {

    @Test
    void formatsStructuredFields() {
        LogContext context = LogContext.builder()
                .sessionId("abc")
                .command("GET")
                .put("key", "user:1")
                .build();

        String line = StructuredServerLogger.formatLine(
                Level.WARN,
                "server_failure",
                "wrong number of arguments",
                ErrorCategory.COMMAND,
                context);

        assertEquals(
                "level=WARN event=\"server_failure\" category=COMMAND message=\"wrong number of arguments\" "
                        + "session_id=\"abc\" command=\"GET\" key=\"user:1\"",
                line);
    }
}
