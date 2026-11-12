package com.myredis.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable key-value dimensions attached to a log line (session, command, etc.).
 */
public final class LogContext {

    private final Map<String, String> fields;

    private LogContext(Map<String, String> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static LogContext empty() {
        return new LogContext(Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, String> fields() {
        return fields;
    }

    public static final class Builder {

        private final Map<String, String> fields = new LinkedHashMap<>();

        public Builder sessionId(String sessionId) {
            return put("session_id", sessionId);
        }

        public Builder command(String command) {
            return put("command", command);
        }

        public Builder remoteAddress(String remoteAddress) {
            return put("remote_address", remoteAddress);
        }

        public Builder put(String key, String value) {
            if (value != null) {
                fields.put(key, value);
            }
            return this;
        }

        public LogContext build() {
            return new LogContext(fields);
        }
    }
}
