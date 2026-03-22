package com.cheeseocean.im.client.cli;

public class ConsolePrinter {

    public void println(String message) {
        System.out.println(message);
    }

    public void printHelp() {
        println("Commands:");
        println("  login <userId> <password> <platformId>");
        println("  connect");
        println("  send <peerUserId> <text>");
        println("  heartbeat");
        println("  status");
        println("  quit");
    }

    public void printStatus(DemoState state) {
        println("userId=" + state.session().getUserId());
        println("platformId=" + state.session().getPlatformId());
        println("tokenPresent=" + (state.session().getAccessToken() != null && !state.session().getAccessToken().isBlank()));
        println("connectionState=" + state.session().getConnectionState());
        println("latestClientMsgId=" + state.session().getLatestClientMsgId());
        println("latestServerMsgId=" + state.session().getLatestServerMsgId());
    }
}
