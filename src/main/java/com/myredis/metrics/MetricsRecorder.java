package com.myredis.metrics;

/**
 * Thread-safe recording of named metrics.
 */
public interface MetricsRecorder {

    void increment(String name);

    long getCounter(String name);
}
