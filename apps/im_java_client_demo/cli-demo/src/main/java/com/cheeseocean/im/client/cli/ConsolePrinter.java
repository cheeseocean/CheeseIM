package com.cheeseocean.im.client.cli;

public class ConsolePrinter {

    private static final int SCREEN_WIDTH = 88;
    private static final int BUBBLE_WIDTH = 34;

    public void println(String message) {
        System.out.println(message);
    }

    public void printHelp() {
        println("Commands:");
        println("  login <userId> <password> <platformId>");
        println("  connect");
        println("  send <peerUserId> <text>");
        println("  typing <peerUserId>");
        println("  read <peerUserId>");
        println("  revoke <peerUserId> [serverMsgId]");
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
        println("activePeer=" + state.activePeerUserId());
    }

    public void printConversation(DemoState state, String peerUserId) {
        println(formatConversation(state, peerUserId));
    }

    public String formatConversation(DemoState state, String peerUserId) {
        StringBuilder builder = new StringBuilder();
        builder.append("Conversation with ").append(peerUserId).append('\n');
        for (DemoState.ChatMessage message : state.messages(peerUserId)) {
            builder.append(renderBubble(message, peerUserId)).append('\n');
        }
        if (state.isPeerTyping(peerUserId)) {
            builder.append(peerUserId).append(" typing...");
        }
        return builder.toString().trim();
    }

    private String renderBubble(DemoState.ChatMessage message, String peerUserId) {
        String label = message.outgoing() ? "you" : peerUserId;
        String[] bodyLines = wrap(message.displayContent(), BUBBLE_WIDTH - 4);
        String meta = message.outgoing()
                ? message.statusLabel()
                : (message.seq() == null ? "" : "#" + message.seq());
        int width = contentWidth(bodyLines, meta);
        String horizontal = "+" + "-".repeat(width + 2) + "+";
        StringBuilder bubble = new StringBuilder();
        appendLine(bubble, align(label, message.outgoing(), 0));
        appendLine(bubble, align(horizontal, message.outgoing(), width + 4));
        for (String line : bodyLines) {
            appendLine(bubble, align("| " + padRight(line, width) + " |", message.outgoing(), width + 4));
        }
        appendLine(bubble, align("| " + padRight(meta, width) + " |", message.outgoing(), width + 4));
        bubble.append(align(horizontal, message.outgoing(), width + 4));
        return bubble.toString();
    }

    private static void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String align(String line, boolean outgoing, int contentWidth) {
        if (!outgoing) {
            return line;
        }
        int pad = Math.max(0, SCREEN_WIDTH - Math.max(contentWidth, line.length()));
        return " ".repeat(pad) + line;
    }

    private static int contentWidth(String[] bodyLines, String meta) {
        int width = meta == null ? 0 : meta.length();
        for (String line : bodyLines) {
            width = Math.max(width, line.length());
        }
        return Math.max(width, 12);
    }

    private static String[] wrap(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return new String[]{""};
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        String remaining = text;
        while (remaining.length() > maxWidth) {
            lines.add(remaining.substring(0, maxWidth));
            remaining = remaining.substring(maxWidth);
        }
        lines.add(remaining);
        return lines.toArray(String[]::new);
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }
}
