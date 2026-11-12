package com.myredis.exception;

/**
 * Maps throwables to client-safe RESP error text without leaking internal diagnostics.
 */
public final class ErrorResponseMapper {

    /** Generic reply when the failure is not a known domain exception. */
    public static final String GENERIC_CLIENT_ERROR = "internal server error";

    private static final int MAX_CLIENT_MESSAGE_LENGTH = 512;

    private ErrorResponseMapper() {
    }

    /**
     * @return a single-line error string suitable for {@code encodeError}
     */
    public static String toClientErrorMessage(Throwable throwable) {
        if (throwable instanceof MyRedisException domain) {
            return domain.clientMessage();
        }
        return GENERIC_CLIENT_ERROR;
    }

    public static String sanitizeForClient(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC_CLIENT_ERROR;
        }
        String singleLine = raw.replace('\r', ' ').replace('\n', ' ').trim();
        if (containsStackTraceFragment(singleLine)) {
            return GENERIC_CLIENT_ERROR;
        }
        if (singleLine.length() > MAX_CLIENT_MESSAGE_LENGTH) {
            return singleLine.substring(0, MAX_CLIENT_MESSAGE_LENGTH);
        }
        return singleLine;
    }

    private static boolean containsStackTraceFragment(String text) {
        if (text.contains("Exception in thread")) {
            return true;
        }
        if (text.contains("Caused by:")) {
            return true;
        }
        return text.contains("\tat ");
    }
}
