package com.myredis.server;

import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers a JVM shutdown hook and coordinates idempotent graceful teardown.
 */
public final class GracefulShutdownCoordinator {

    private final DatabaseServer databaseServer;
    private final StructuredServerLogger logger;
    private final Runnable onShutdown;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicBoolean shutDown = new AtomicBoolean();
    private volatile Thread shutdownHook;

    public GracefulShutdownCoordinator(
            DatabaseServer databaseServer, StructuredServerLogger logger, Runnable onShutdown) {
        this.databaseServer = Objects.requireNonNull(databaseServer, "databaseServer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.onShutdown = Objects.requireNonNull(onShutdown, "onShutdown");
    }

    public void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        shutdownHook =
                new Thread(
                        () -> {
                            shutdown("shutdown_hook");
                        },
                        "myredis-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    public void shutdown(String reason) {
        if (!shutDown.compareAndSet(false, true)) {
            return;
        }
        logger.logInfo(
                "server_shutdown",
                "graceful shutdown initiated",
                LogContext.builder().put("reason", reason).build());
        try {
            databaseServer.stop();
        } finally {
            onShutdown.run();
            removeHookIfRegistered();
            shutdownLatch.countDown();
        }
        logger.logInfo("server_shutdown_complete", "server stopped", LogContext.empty());
    }

    private void removeHookIfRegistered() {
        Thread hook = shutdownHook;
        if (hook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
        shutdownHook = null;
    }
}
