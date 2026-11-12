package com.myredis;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the modular package layout introduced in Phase 02.
 */
class PackageArchitectureTest {

    private static final String BASE = "com.myredis";

    private static final Set<String> REQUIRED_PACKAGES = Set.of(
            "server",
            "network",
            "protocol",
            "command",
            "storage",
            "datatype",
            "persistence",
            "expiration",
            "transaction",
            "replication",
            "pubsub",
            "config",
            "metrics",
            "exception",
            "logging");

    private static final Set<String> FOUNDATIONAL_TYPES = Stream.of(
                    "com.myredis.server.DatabaseServer",
                    "com.myredis.network.ClientSession",
                    "com.myredis.protocol.RespDecoder",
                    "com.myredis.protocol.RespEncoder",
                    "com.myredis.command.Command",
                    "com.myredis.command.CommandDispatcher",
                    "com.myredis.storage.KeyValueStore",
                    "com.myredis.datatype.RedisValue",
                    "com.myredis.persistence.PersistenceManager",
                    "com.myredis.expiration.ExpirationService",
                    "com.myredis.transaction.TransactionManager",
                    "com.myredis.replication.ReplicationCoordinator",
                    "com.myredis.pubsub.PubSubBroker",
                    "com.myredis.config.ServerConfiguration",
                    "com.myredis.metrics.MetricsRecorder",
                    "com.myredis.exception.MyRedisException")
            .collect(Collectors.toUnmodifiableSet());

    @Test
    void requiredPackagesAreDocumented() throws ClassNotFoundException {
        for (String pkg : REQUIRED_PACKAGES) {
            Class<?> packageInfo = Class.forName(BASE + "." + pkg + ".package-info");
            assertTrue(packageInfo.getPackageName().equals(BASE + "." + pkg));
        }
    }

    @Test
    void foundationalInterfacesAreLoadable() throws ClassNotFoundException {
        for (String typeName : FOUNDATIONAL_TYPES) {
            Class<?> type = Class.forName(typeName);
            assertFalse(type.isPrimitive(), typeName);
        }
    }
}
