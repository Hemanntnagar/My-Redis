package com.myredis.benchmark;

/** One benchmark scenario outcome. */
public record BenchmarkResult(
        String scenario,
        long operations,
        double throughputOpsPerSec,
        double avgLatencyMicros,
        double p50LatencyMicros,
        double p95LatencyMicros,
        double p99LatencyMicros,
        long memoryBytesEstimate,
        int concurrentClients) {

    public String toMarkdownRow() {
        return String.format(
                "| %s | %d | %.0f | %.2f | %.2f | %.2f | %.2f | %,d | %d |",
                scenario,
                operations,
                throughputOpsPerSec,
                avgLatencyMicros,
                p50LatencyMicros,
                p95LatencyMicros,
                p99LatencyMicros,
                memoryBytesEstimate,
                concurrentClients);
    }

    public String toJsonLine() {
        return "{"
                + "\"scenario\":\"" + escape(scenario) + "\","
                + "\"operations\":" + operations + ","
                + "\"throughput_ops_per_sec\":" + format(throughputOpsPerSec) + ","
                + "\"avg_latency_micros\":" + format(avgLatencyMicros) + ","
                + "\"p50_latency_micros\":" + format(p50LatencyMicros) + ","
                + "\"p95_latency_micros\":" + format(p95LatencyMicros) + ","
                + "\"p99_latency_micros\":" + format(p99LatencyMicros) + ","
                + "\"memory_bytes_estimate\":" + memoryBytesEstimate + ","
                + "\"concurrent_clients\":" + concurrentClients
                + "}";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
