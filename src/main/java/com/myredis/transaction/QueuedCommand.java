package com.myredis.transaction;

import java.util.List;

/** One command captured during {@code MULTI} before {@code EXEC}. */
public record QueuedCommand(String name, List<byte[]> arguments) {}
