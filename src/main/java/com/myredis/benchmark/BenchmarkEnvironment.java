package com.myredis.benchmark;

import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.exception.InvalidConfigurationException;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.network.TcpListenServer;
import com.myredis.storage.InMemoryKeyValueStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Boots an isolated in-memory server for benchmarks (no AOF, no replication). */
public final class BenchmarkEnvironment implements AutoCloseable {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final TcpListenServer server;
    private final int port;

    public BenchmarkEnvironment(int port) throws IOException, ConnectionException, InvalidConfigurationException {
        this.port = port;
        server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(store));
        server.start();
    }

    public int port() {
        return port;
    }

    public long approximateMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long keyCount() {
        return store.keyCount();
    }

    public void preloadKeys(int keySpace, int valueSize) throws IOException {
        byte[] value = patternedValue(valueSize);
        try (RespBenchClient client = new RespBenchClient("127.0.0.1", port)) {
            client.sendNoReply(RespBenchClient.pingCommand());
            for (int index = 0; index < keySpace; index++) {
                client.sendNoReply(RespBenchClient.setCommand(keyBytes(index), value));
            }
        }
    }

    static byte[] keyBytes(int index) {
        String key = "bench:key:" + index;
        return key.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] patternedValue(int size) {
        byte[] value = new byte[size];
        for (int index = 0; index < size; index++) {
            value[index] = (byte) ('a' + (index % 26));
        }
        return value;
    }

    public List<BenchmarkResult> runSuite(BenchmarkConfig config) throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(config);
        List<BenchmarkResult> results = new ArrayList<>();
        for (BenchmarkWorkload workload : BenchmarkWorkload.values()) {
            prepareWorkload(workload, config);
            for (int clients : config.clientCounts()) {
                results.add(
                        runner.runWorkload(
                                workload,
                                clients,
                                config,
                                this::approximateMemoryBytes,
                                keyCount()));
            }
        }
        return results;
    }

    private void prepareWorkload(BenchmarkWorkload workload, BenchmarkConfig config) throws IOException {
        flushAll();
        if (workload == BenchmarkWorkload.GET || workload == BenchmarkWorkload.MIXED) {
            preloadKeys(config.keySpaceSize(), config.valueSizeBytes());
        }
        if (workload == BenchmarkWorkload.INCR && config.incrOnSharedKey()) {
            try (RespBenchClient client = new RespBenchClient("127.0.0.1", port)) {
                client.sendNoReply(
                        RespBenchClient.setCommand(
                                "bench:incr:shared".getBytes(StandardCharsets.US_ASCII),
                                "0".getBytes(StandardCharsets.US_ASCII)));
            }
        }
    }

    private void flushAll() throws IOException {
        try (RespBenchClient client = new RespBenchClient("127.0.0.1", port)) {
            client.sendNoReply(
                    RespBenchClient.respCommand(
                            "FLUSHDB".getBytes(StandardCharsets.US_ASCII), new byte[0][]));
        }
    }

    @Override
    public void close() {
        server.stop();
    }

    static int freeLocalPort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
