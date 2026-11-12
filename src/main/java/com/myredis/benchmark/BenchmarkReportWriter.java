package com.myredis.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Writes human and machine-readable benchmark output. */
public final class BenchmarkReportWriter {

    private BenchmarkReportWriter() {
    }

    public static Path write(BenchmarkConfig config, List<BenchmarkResult> results) throws IOException {
        Path directory = Path.of(config.resultsDirectory());
        Files.createDirectories(directory);
        String stamp = Instant.now().toString().replace(':', '-');
        Path markdown = directory.resolve("benchmark-" + stamp + ".md");
        Path jsonl = directory.resolve("benchmark-" + stamp + ".jsonl");

        StringBuilder builder = new StringBuilder();
        builder.append("# My-Redis benchmark run\n\n");
        builder.append("Generated: ").append(Instant.now()).append("\n\n");
        builder.append("## Configuration\n\n");
        builder.append("- Host: ").append(config.host()).append(':').append(config.port()).append('\n');
        builder.append("- Key space: ").append(config.keySpaceSize()).append('\n');
        builder.append("- Value size: ").append(config.valueSizeBytes()).append(" bytes\n");
        builder.append("- Warmup: ").append(config.warmup()).append('\n');
        builder.append("- Duration: ").append(config.duration()).append('\n');
        builder.append("- Client counts: ").append(config.clientCounts()).append('\n');
        builder.append("- Mixed split (GET/SET/INCR): ")
                .append(config.mixedGetPercent())
                .append('/')
                .append(config.mixedSetPercent())
                .append('/')
                .append(config.mixedIncrPercent())
                .append("\n\n");
        builder.append("## Results\n\n");
        builder.append("| Scenario | Ops | Throughput ops/s | Avg µs | p50 µs | p95 µs | p99 µs | JVM bytes | Clients |\n");
        builder.append("|----------|-----|------------------|--------|--------|--------|--------|-----------|---------|\n");
        for (BenchmarkResult result : results) {
            builder.append(result.toMarkdownRow()).append('\n');
        }

        Files.writeString(markdown, builder.toString(), StandardCharsets.UTF_8);

        StringBuilder json = new StringBuilder();
        for (BenchmarkResult result : results) {
            json.append(result.toJsonLine()).append('\n');
        }
        Files.writeString(jsonl, json.toString(), StandardCharsets.UTF_8);
        return markdown;
    }
}
