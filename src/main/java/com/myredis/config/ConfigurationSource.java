package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;

/**
 * Supplies validated configuration for bootstrap without tying callers to a loader implementation.
 */
@FunctionalInterface
public interface ConfigurationSource {

    ServerConfiguration load() throws InvalidConfigurationException;
}
