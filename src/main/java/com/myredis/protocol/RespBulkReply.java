package com.myredis.protocol;

/**
 * Parsed bulk-string reply, either payload or null bulk.
 */
public sealed interface RespBulkReply extends RespValue permits RespBulkString, RespNullBulk {}
