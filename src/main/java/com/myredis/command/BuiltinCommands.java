package com.myredis.command;

import com.myredis.pubsub.PubSubBroker;
import com.myredis.transaction.TransactionManager;
import com.myredis.transaction.WatchRegistry;

/** Wired built-in command dispatcher and cross-cutting command collaborators. */
public record BuiltinCommands(
        RegistryCommandDispatcher dispatcher,
        TransactionManager transactionManager,
        PubSubBroker pubSubBroker,
        WatchRegistry watchRegistry) {}
