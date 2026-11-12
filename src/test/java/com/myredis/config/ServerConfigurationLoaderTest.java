package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigurationLoaderTest {

    private final Map<String, String> previousProperties = new HashMap<>();

    @AfterEach
    void restoreProperties() {
        for (var entry : previousProperties.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
        previousProperties.clear();
    }

    @Test
    void usesDefaultsWhenNoOverrides() throws InvalidConfigurationException {
        ServerConfiguration configuration = ServerConfigurationLoader.load();
        assertEquals(ServerConfigurationDefaults.HOST, configuration.host());
        assertEquals(6379, configuration.port());
    }

    @Test
    void readsSystemProperties() throws InvalidConfigurationException {
        setProperty("myredis.host", "127.0.0.1");
        setProperty("myredis.port", "6380");
        setProperty("myredis.loggingLevel", "DEBUG");

        ServerConfiguration configuration = ServerConfigurationLoader.load();

        assertEquals("127.0.0.1", configuration.host());
        assertEquals(6380, configuration.port());
        assertEquals(LoggingLevel.DEBUG, configuration.loggingLevel());
    }

    @Test
    void rejectsInvalidInteger() {
        setProperty("myredis.port", "not-a-port");
        assertThrows(InvalidConfigurationException.class, ServerConfigurationLoader::load);
    }

    private void setProperty(String key, String value) {
        previousProperties.putIfAbsent(key, System.getProperty(key));
        System.setProperty(key, value);
    }
}
