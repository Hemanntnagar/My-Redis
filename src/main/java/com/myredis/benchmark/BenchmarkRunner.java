package com.myredis.benchmark;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Drives concurrent clients for a single workload profile. */
final class BenchmarkRunner {

    BenchmarkRunner(BenchmarkConfig config) {
    }

    BenchmarkResult runWorkload(
            BenchmarkWorkload workload,
            int clientCount,
            BenchmarkConfig settings,
            LongSupplier memoryProbe,
            long keysPreloaded)
            throws Exception {
        String label =
                workload.name().toLowerCase()
                        + "_c"
                        + clientCount
                        + "_keys"
                        + settings.keySpaceSize();

        int expectedSamples = (int) Math.min(
                500_000L,
                settings.duration().toMillis() * clientCount * 2_000L);
        LatencyStats stats = new LatencyStats(Math.max(1_024, expectedSamples));

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(clientCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        long warmupEnd = System.nanoTime() + settings.warmup().toNanos();
        long measureEnd = warmupEnd + settings.duration().toNanos();

        for (int clientIndex = 0; clientIndex < clientCount; clientIndex++) {
            int id = clientIndex;
            pool.submit(
                    () -> {
                        try (RespBenchClient client =
                                new RespBenchClient(settings.host(), settings.port())) {
                            client.sendNoReply(RespBenchClient.pingCommand());
                            byte[] value = BenchmarkEnvironment.patternedValue(settings.valueSizeBytes());
                            byte[] incrKey = incrKey(settings, id);
                            ThreadLocalRandom random = ThreadLocalRandom.current();
                            start.await();
                            while (System.nanoTime() < measureEnd) {
                                long now = System.nanoTime();
                                boolean measuring = now >= warmupEnd;
                                byte[] command =
                                        commandFor(workload, settings, id, random, value, incrKey);
                                long latency = client.roundTrip(command);
                                if (measuring) {
                                    stats.record(latency);
                                }
                            }
                        } catch (Throwable error) {
                            failure.compareAndSet(null, error);
                        } finally {
                            finished.countDown();
                        }
                    });
        }

        start.countDown();
        finished.await(settings.duration().plus(settings.warmup()).plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        long measuredNanos = settings.duration().toNanos();
        pool.shutdownNow();

        if (failure.get() != null) {
            throw new Exception("benchmark client failed", failure.get());
        }

        long memory = memoryProbe.getAsLong();
        return stats.toResult(label, measuredNanos, memory, clientCount);
    }

    private static byte[] commandFor(
            BenchmarkWorkload workload,
            BenchmarkConfig settings,
            int clientId,
            ThreadLocalRandom random,
            byte[] value,
            byte[] incrKey) {
        return switch (workload) {
            case GET -> {
                int keyIndex = random.nextInt(settings.keySpaceSize());
                yield RespBenchClient.getCommand(BenchmarkEnvironment.keyBytes(keyIndex));
            }
            case SET -> {
                int keyIndex = random.nextInt(settings.keySpaceSize());
                yield RespBenchClient.setCommand(BenchmarkEnvironment.keyBytes(keyIndex), value);
            }
            case INCR -> RespBenchClient.incrCommand(incrKey);
            case MIXED -> mixedCommand(settings, random, value, incrKey);
        };
    }

    private static byte[] mixedCommand(
            BenchmarkConfig settings, ThreadLocalRandom random, byte[] value, byte[] incrKey) {
        int roll = random.nextInt(100);
        if (roll < settings.mixedGetPercent()) {
            int keyIndex = random.nextInt(settings.keySpaceSize());
            return RespBenchClient.getCommand(BenchmarkEnvironment.keyBytes(keyIndex));
        }
        if (roll < settings.mixedGetPercent() + settings.mixedSetPercent()) {
            int keyIndex = random.nextInt(settings.keySpaceSize());
            return RespBenchClient.setCommand(BenchmarkEnvironment.keyBytes(keyIndex), value);
        }
        return RespBenchClient.incrCommand(incrKey);
    }

    private static byte[] incrKey(BenchmarkConfig settings, int clientId) {
        if (settings.incrOnSharedKey()) {
            return "bench:incr:shared".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        }
        return ("bench:incr:" + clientId).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
