package com.myredis.command;

import com.myredis.exception.CommandException;

import java.util.List;

/**
 * Validates argument counts for Redis commands.
 */
public final class CommandArity {

    private CommandArity() {
    }

    public static void requireExact(List<byte[]> arguments, int expected, String commandName)
            throws CommandException {
        if (arguments.size() != expected) {
            throw wrongArity(commandName);
        }
    }

    public static void requireBetween(List<byte[]> arguments, int min, int max, String commandName)
            throws CommandException {
        if (arguments.size() < min || arguments.size() > max) {
            throw wrongArity(commandName);
        }
    }

    public static void requireAtLeast(List<byte[]> arguments, int min, String commandName)
            throws CommandException {
        if (arguments.size() < min) {
            throw wrongArity(commandName);
        }
    }

    private static CommandException wrongArity(String commandName) {
        return new CommandException("ERR wrong number of arguments for '" + commandName + "' command");
    }
}
