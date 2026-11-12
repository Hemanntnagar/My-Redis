package com.myredis.protocol;

/**
 * RESP2 integer reply ({@code :...}).
 */
public record RespInteger(long value) {}
