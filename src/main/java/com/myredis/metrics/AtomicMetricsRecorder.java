package com.myredis.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process counter registry used for INFO and internal observability.
 */
public final class AtomicMetricsRecorder implements MetricsRecorder {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void increment(String name) {
        counter(name).incrementAndGet();
    }

    public void incrementBy(String name, long delta) {
        if (delta <= 0) {
            return;
        }
        counter(name).addAndGet(delta);
    }

    @Override
    public long getCounter(String name) {
        AtomicLong value = counters.get(name);
        return value == null ? 0L : value.get();
    }

    public Map<String, Long> snapshotCounters() {
        ConcurrentHashMap<String, Long> snapshot = new ConcurrentHashMap<>();
        counters.forEach((name, value) -> snapshot.put(name, value.get()));
        return Map.copyOf(snapshot);
    }

    private AtomicLong counter(String name) {
        return counters.computeIfAbsent(name, ignored -> new AtomicLong());
    }
}
