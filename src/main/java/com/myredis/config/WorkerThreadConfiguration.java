package com.myredis.config;

/**
 * Settings for the command-processing worker pool.
 *
 * @param threadCount number of worker threads (must be at least 1)
 */
public record WorkerThreadConfiguration(int threadCount) {}
