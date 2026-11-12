package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigurationTest {

    @Test
    void defaultsUseExpectedValues() throws InvalidConfigurationException {
        ServerConfiguration configuration = ServerConfiguration.defaults();

        assertEquals(ServerConfigurationDefaults.HOST, configuration.host());
        assertEquals(6379, configuration.port());
        assertEquals(ServerConfigurationDefaults.MAX_CLIENT_CONNECTIONS, configuration.maxClientConnections());
        assertNotNull(configuration.workerThreads());
        assertEquals(
                ServerConfigurationDefaults.defaultWorkerThreadCount(),
                configuration.workerThreads().threadCount());
        assertEquals(ServerConfigurationDefaults.defaultPersistenceDirectory(), configuration.persistenceDirectory());
        assertEquals(LoggingLevel.INFO, configuration.loggingLevel());
        assertEquals(ServerConfigurationDefaults.TCP_BACKLOG, configuration.tcpBacklog());
        assertEquals(Duration.ZERO, configuration.clientIdleTimeout());
        assertEquals(ServerConfigurationDefaults.AOF_ENABLED, configuration.aofEnabled());
        assertEquals(ServerConfigurationDefaults.AOF_FSYNC_POLICY, configuration.aofFsyncPolicy());
    }

    @Test
    void builderProducesOverriddenValues() throws InvalidConfigurationException {
        ServerConfiguration configuration = ServerConfiguration.builder()
                .host("127.0.0.1")
                .port(6380)
                .maxClientConnections(500)
                .workerThreads(8)
                .persistenceDirectory("/var/lib/myredis")
                .loggingLevel(LoggingLevel.DEBUG)
                .tcpBacklog(128)
                .clientIdleTimeout(Duration.ofSeconds(30))
                .build();

        assertEquals("127.0.0.1", configuration.host());
        assertEquals(6380, configuration.port());
        assertEquals(500, configuration.maxClientConnections());
        assertEquals(8, configuration.workerThreads().threadCount());
        assertEquals("/var/lib/myredis", configuration.persistenceDirectory());
        assertEquals(LoggingLevel.DEBUG, configuration.loggingLevel());
        assertEquals(128, configuration.tcpBacklog());
        assertEquals(Duration.ofSeconds(30), configuration.clientIdleTimeout());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 65_536, -1})
    void invalidPortIsRejected(int port) {
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().port(port).build());
    }

    @Test
    void blankHostIsRejected() {
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().host("   ").build());
    }

    @Test
    void invalidMaxClientConnectionsIsRejected() {
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().maxClientConnections(0).build());
    }

    @Test
    void invalidWorkerThreadCountIsRejected() {
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().workerThreads(0).build());
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().workerThreads(10_000).build());
    }

    @Test
    void blankPersistenceDirectoryIsRejected() {
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().persistenceDirectory("").build());
    }

    @Test
    void nullLoggingLevelIsRejected() {
        assertThrows(
                InvalidConfigurationException.class,
                () -> ServerConfiguration.builder().loggingLevel(null).build());
    }
}
