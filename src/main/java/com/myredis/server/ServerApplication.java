package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.config.ConfigurationSource;
import com.myredis.config.ServerConfigurationLoader;
import com.myredis.exception.ConnectionException;
import com.myredis.exception.InvalidConfigurationException;
import com.myredis.exception.PersistenceException;
import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.util.Objects;

/**
 * Orchestrates configuration loading, component initialization, and process lifecycle.
 */
public final class ServerApplication {

    private final ConfigurationSource configurationSource;
    private final ServerBootstrap bootstrap;
    private volatile GracefulShutdownCoordinator shutdownCoordinator;

    public ServerApplication() {
        this(ServerConfigurationLoader::load, new ServerBootstrap());
    }

    ServerApplication(ConfigurationSource configurationSource, ServerBootstrap bootstrap) {
        this.configurationSource = Objects.requireNonNull(configurationSource, "configurationSource");
        this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
    }

    public void run()
            throws InvalidConfigurationException, InterruptedException, ConnectionException, PersistenceException {
        ServerConfiguration configuration = configurationSource.load();
        ServerContext context = bootstrap.bootstrap(configuration);
        StructuredServerLogger logger = context.logger();

        try {
            context.persistence().load();
        } catch (PersistenceException failure) {
            logger.logFailure(failure, LogContext.builder().put("phase", "persistence_load").build());
            context.errorHandler().handleFailure(failure, LogContext.empty());
            throw failure;
        }

        ServerStartupReporter startupReporter = new ServerStartupReporter(logger);
        startupReporter.report(configuration);

        context.expirationCleanup().start();
        context.databaseServer().start();

        GracefulShutdownCoordinator shutdownCoordinator =
                new GracefulShutdownCoordinator(
                        context.databaseServer(),
                        logger,
                        () -> {
                            context.expirationCleanup().close();
                            try {
                                context.persistence().flush();
                            } catch (PersistenceException failure) {
                                logger.logFailure(failure, LogContext.builder().put("phase", "persistence_flush").build());
                            }
                            if (context.persistence() instanceof com.myredis.persistence.CompositePersistenceManager composite) {
                                composite.close();
                            }
                            if (context.collaborators() != null) {
                                context.collaborators().close();
                            }
                        });

        this.shutdownCoordinator = shutdownCoordinator;
        shutdownCoordinator.registerShutdownHook();
        logger.logInfo(
                "server_running",
                "awaiting shutdown signal",
                LogContext.builder().put("pid", String.valueOf(ProcessHandle.current().pid())).build());

        shutdownCoordinator.awaitShutdown();
    }

    void requestShutdown(String reason) {
        GracefulShutdownCoordinator coordinator = shutdownCoordinator;
        if (coordinator != null) {
            coordinator.shutdown(reason);
        }
    }
}
