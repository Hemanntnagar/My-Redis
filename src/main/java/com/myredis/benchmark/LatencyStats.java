package com.myredis.benchmark;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

/** Aggregates per-operation latencies and reports throughput-oriented percentiles. */
public final class LatencyStats {

    private final long[] samples;
    private final int capacity;
    private int size;
    private final LongAdder operationCount = new LongAdder();
    private volatile long totalLatencyNanos;

    public LatencyStats(int expectedSamples) {
        this.capacity = Math.max(1, expectedSamples);
        this.samples = new long[capacity];
    }

    public synchronized void record(long latencyNanos) {
        operationCount.increment();
        totalLatencyNanos += latencyNanos;
        if (size < capacity) {
            samples[size++] = latencyNanos;
        }
    }

    public long operations() {
        return operationCount.sum();
    }

    public BenchmarkResult toResult(String label, long elapsedNanos, long memoryBytes, int connectedClients) {
        long ops = operations();
        double seconds = elapsedNanos / 1_000_000_000.0;
        double throughput = seconds > 0 ? ops / seconds : 0.0;
        double averageMicros = ops > 0 ? (totalLatencyNanos / (double) ops) / 1_000.0 : 0.0;

        long[] snapshot = snapshotSamples();
        Arrays.sort(snapshot);

        return new BenchmarkResult(
                label,
                ops,
                throughput,
                averageMicros,
                percentileMicros(snapshot, 50),
                percentileMicros(snapshot, 95),
                percentileMicros(snapshot, 99),
                memoryBytes,
                connectedClients);
    }

    private long[] snapshotSamples() {
        synchronized (this) {
            return Arrays.copyOf(samples, size);
        }
    }

    private static double percentileMicros(long[] sorted, int percentile) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index] / 1_000.0;
    }
}
