package com.myredis.metrics;

/**
 * Placeholder metrics sink used until observability wiring is implemented.
 */
public final class NoOpMetricsRecorder implements MetricsRecorder {

    @Override
    public void increment(String name) {
        // intentionally empty
    }

    @Override
    public long getCounter(String name) {
        return 0L;
    }
}
