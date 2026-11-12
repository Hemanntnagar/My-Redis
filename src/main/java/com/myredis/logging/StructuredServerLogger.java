package com.myredis.logging;

import com.myredis.config.LoggingLevel;
import com.myredis.exception.ErrorCategory;
import com.myredis.exception.MyRedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Objects;

/**
 * Emits fixed-field structured log lines for operators; throwables are logged with stack traces
 * server-side only.
 */
public final class StructuredServerLogger {

    private static final Logger LOG = LoggerFactory.getLogger("com.myredis.server");

    public void applyLoggingLevel(LoggingLevel level) {
        Objects.requireNonNull(level, "level");
        String slf4jLevel = switch (level) {
            case TRACE -> "trace";
            case DEBUG -> "debug";
            case INFO -> "info";
            case WARN -> "warn";
            case ERROR -> "error";
        };
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", slf4jLevel);
        System.setProperty("org.slf4j.simpleLogger.log.com.myredis", slf4jLevel);
    }

    public void logInfo(String event, String message, LogContext context) {
        emit(Level.INFO, event, message, null, null, context);
    }

    public void logWarning(String event, String message, LogContext context) {
        emit(Level.WARN, event, message, null, null, context);
    }

    public void logFailure(Throwable failure, LogContext context) {
        Objects.requireNonNull(failure, "failure");
        ErrorCategory category = failure instanceof MyRedisException domain ? domain.category() : null;
        String event = "server_failure";
        String operatorMessage = failure instanceof MyRedisException domain
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
        Level level = failure instanceof MyRedisException ? Level.WARN : Level.ERROR;
        emit(level, event, operatorMessage, category, failure, context);
    }

    private void emit(
            Level level,
            String event,
            String message,
            ErrorCategory category,
            Throwable cause,
            LogContext context) {
        String line = formatLine(level, event, message, category, context);
        switch (level) {
            case TRACE -> {
                if (cause != null) {
                    LOG.trace(line, cause);
                } else {
                    LOG.trace(line);
                }
            }
            case DEBUG -> {
                if (cause != null) {
                    LOG.debug(line, cause);
                } else {
                    LOG.debug(line);
                }
            }
            case INFO -> {
                if (cause != null) {
                    LOG.info(line, cause);
                } else {
                    LOG.info(line);
                }
            }
            case WARN -> {
                if (cause != null) {
                    LOG.warn(line, cause);
                } else {
                    LOG.warn(line);
                }
            }
            case ERROR -> {
                if (cause != null) {
                    LOG.error(line, cause);
                } else {
                    LOG.error(line);
                }
            }
        }
    }

    static String formatLine(
            Level level,
            String event,
            String message,
            ErrorCategory category,
            LogContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("level=").append(level.name());
        sb.append(" event=").append(quote(event));
        if (category != null) {
            sb.append(" category=").append(category.name());
        }
        if (message != null && !message.isBlank()) {
            sb.append(" message=").append(quote(message));
        }
        for (var entry : context.fields().entrySet()) {
            sb.append(' ').append(entry.getKey()).append('=').append(quote(entry.getValue()));
        }
        return sb.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
