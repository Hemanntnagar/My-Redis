package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerApplicationTest {

    @Test
    void startAndShutdownReleasesShutdownLatch() throws Exception {
        ServerConfiguration configuration = localTestConfiguration();
        ServerBootstrap bootstrap = new ServerBootstrap();
        ServerContext context = bootstrap.bootstrap(configuration);

        GracefulShutdownCoordinator coordinator =
                new GracefulShutdownCoordinator(context.databaseServer(), context.logger(), () -> {});
        coordinator.registerShutdownHook();

        LifecycleDatabaseServer server = (LifecycleDatabaseServer) context.databaseServer();
        server.start();
        assertTrue(server.isRunning());

        Thread awaiter =
                new Thread(
                        () -> {
                            try {
                                coordinator.awaitShutdown();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
        awaiter.start();

        coordinator.shutdown("test");
        awaiter.join(2_000);

        assertFalse(server.isRunning());
        assertFalse(awaiter.isAlive());
    }

    @Test
    void runCompletesAfterGracefulShutdown()
            throws Exception {
        ServerApplication application =
                new ServerApplication(() -> localTestConfiguration(), new ServerBootstrap());

        Thread runner =
                new Thread(
                        () -> {
                            try {
                                application.run();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
        runner.start();

        Thread.sleep(300);
        application.requestShutdown("test");
        runner.join(5_000);

        assertFalse(runner.isAlive());
    }

    @Test
    void bootstrapStartsWithStoppedServer() throws InvalidConfigurationException {
        ServerContext context = new ServerBootstrap().bootstrap(ServerConfiguration.defaults());
        LifecycleDatabaseServer server = (LifecycleDatabaseServer) context.databaseServer();
        assertFalse(server.isRunning());
    }

    static ServerConfiguration localTestConfiguration() throws InvalidConfigurationException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("failed to allocate ephemeral port", failure);
        }
        return ServerConfiguration.builder().host("127.0.0.1").port(port).build();
    }
}
