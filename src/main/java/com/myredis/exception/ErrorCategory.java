package com.myredis.exception;

/**
 * Stable classification for metrics, logging, and client error mapping.
 */
public enum ErrorCategory {
    PROTOCOL,
    COMMAND,
    STORAGE,
    CONNECTION,
    PERSISTENCE,
    CONFIGURATION
}
