package com.myredis.benchmark;

import java.time.Duration;
import java.util.List;

/**
 * Fixed workload parameters so runs are comparable across machines and git revisions.
 * Override via {@link BenchmarkMain} CLI flags.
 */
public record BenchmarkConfig(
        String host,
        int port,
        int keySpaceSize,
        int valueSizeBytes,
        Duration warmup,
        Duration duration,
        List<Integer> clientCounts,
        boolean incrOnSharedKey,
        int mixedGetPercent,
        int mixedSetPercent,
        int mixedIncrPercent,
        String resultsDirectory) {

    public static BenchmarkConfig defaults(int port) {
        return new BenchmarkConfig(
                "127.0.0.1",
                port,
                10_000,
                64,
                Duration.ofSeconds(3),
                Duration.ofSeconds(8),
                List.of(1, 4, 16, 32),
                false,
                50,
                40,
                10,
                "benchmark-results");
    }

    public BenchmarkConfig {
        if (keySpaceSize < 1) {
            throw new IllegalArgumentException("keySpaceSize must be >= 1");
        }
        if (valueSizeBytes < 1) {
            throw new IllegalArgumentException("valueSizeBytes must be >= 1");
        }
        if (mixedGetPercent + mixedSetPercent + mixedIncrPercent != 100) {
            throw new IllegalArgumentException("mixed workload percents must sum to 100");
        }
        clientCounts = List.copyOf(clientCounts);
    }
}
