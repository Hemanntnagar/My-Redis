package com.myredis.benchmark;

/** Repeatable benchmark scenarios against a live TCP listener. */
public enum BenchmarkWorkload {
    GET,
    SET,
    INCR,
    MIXED
}
