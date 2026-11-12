package com.myredis;

import com.myredis.exception.ConnectionException;
import com.myredis.exception.InvalidConfigurationException;
import com.myredis.exception.PersistenceException;
import com.myredis.server.ServerApplication;

/**
 * JVM entry point for the My-Redis server process.
 */
public final class JavaRedis {

    public static void main(String[] args) {
        try {
            new JavaRedis().start();
        } catch (InvalidConfigurationException e) {
            System.err.println("Invalid configuration: " + e.getMessage());
            System.exit(1);
        } catch (ConnectionException | PersistenceException e) {
            System.err.println("Startup failed: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted during shutdown");
            System.exit(1);
        }
    }

    void start()
            throws InvalidConfigurationException, InterruptedException, ConnectionException, PersistenceException {
        new ServerApplication().run();
    }
}
