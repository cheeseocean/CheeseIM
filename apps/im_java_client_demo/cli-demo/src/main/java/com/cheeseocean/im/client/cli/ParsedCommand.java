package com.cheeseocean.im.client.cli;

public record ParsedCommand(Type type, String arg1, String arg2, String arg3) {

    public enum Type {
        LOGIN,
        CONNECT,
        SEND,
        HEARTBEAT,
        STATUS,
        QUIT,
        HELP
    }
}
