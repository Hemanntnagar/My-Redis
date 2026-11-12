package com.myredis.benchmark;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Entry point for repeatable TCP benchmarks of GET, SET, INCR, and mixed workloads.
 *
 * <pre>{@code
 * ./mvnw -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes:$(cat target/cp.txt)" com.myredis.benchmark.BenchmarkMain
 * }</pre>
 */
public final class BenchmarkMain {

    public static void main(String[] args) throws Exception {
        int port = BenchmarkEnvironment.freeLocalPort();
        Duration warmup = Duration.ofSeconds(3);
        Duration duration = Duration.ofSeconds(8);
        int keySpace = 10_000;
        int valueSize = 64;
        List<Integer> clients = new ArrayList<>(List.of(1, 4, 16, 32));
        String resultsDir = "benchmark-results";

        for (int index = 0; index < args.length; index++) {
            String flag = args[index];
            switch (flag) {
                case "--port" -> port = Integer.parseInt(requiredArg(args, ++index, flag));
                case "--warmup-seconds" -> warmup = Duration.ofSeconds(Long.parseLong(requiredArg(args, ++index, flag)));
                case "--duration-seconds" -> duration = Duration.ofSeconds(Long.parseLong(requiredArg(args, ++index, flag)));
                case "--keyspace" -> keySpace = Integer.parseInt(requiredArg(args, ++index, flag));
                case "--value-size" -> valueSize = Integer.parseInt(requiredArg(args, ++index, flag));
                case "--clients" -> clients = parseClients(requiredArg(args, ++index, flag));
                case "--results-dir" -> resultsDir = requiredArg(args, ++index, flag);
                case "--help" -> {
                    printUsage();
                    return;
                }
                default -> throw new IllegalArgumentException("unknown flag: " + flag);
            }
        }

        BenchmarkConfig config =
                new BenchmarkConfig(
                        "127.0.0.1",
                        port,
                        keySpace,
                        valueSize,
                        warmup,
                        duration,
                        clients,
                        false,
                        50,
                        40,
                        10,
                        resultsDir);

        try (BenchmarkEnvironment environment = new BenchmarkEnvironment(config.port())) {
            List<BenchmarkResult> results = environment.runSuite(config);
            Path report = BenchmarkReportWriter.write(config, results);
            System.out.println("Benchmark complete. Report: " + report.toAbsolutePath());
            for (BenchmarkResult result : results) {
                System.out.printf(
                        Locale.ROOT,
                        "%s → %.0f ops/s (p99 %.2f µs, %d clients)%n",
                        result.scenario(),
                        result.throughputOpsPerSec(),
                        result.p99LatencyMicros(),
                        result.concurrentClients());
            }
        }
    }

    private static List<Integer> parseClients(String csv) {
        String[] parts = csv.split(",");
        List<Integer> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            values.add(Integer.parseInt(part.trim()));
        }
        return values;
    }

    private static String requiredArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("missing value for " + flag);
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println(
                """
                Usage: BenchmarkMain [options]
                  --port <n>              listen port (default: ephemeral)
                  --warmup-seconds <n>    warmup per workload (default: 3)
                  --duration-seconds <n>  measured window (default: 8)
                  --keyspace <n>          distinct keys (default: 10000)
                  --value-size <n>        SET payload bytes (default: 64)
                  --clients <csv>         concurrency levels (default: 1,4,16,32)
                  --results-dir <path>    output directory (default: benchmark-results)
                """);
    }
}
