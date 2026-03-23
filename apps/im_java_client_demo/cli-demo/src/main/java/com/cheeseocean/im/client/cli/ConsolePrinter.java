package com.cheeseocean.im.client.cli;

public class ConsolePrinter {

    private static final int SCREEN_WIDTH = 96;
    private static final int SIDEBAR_WIDTH = 28;
    private static final int BUBBLE_WIDTH = 42;

    public void println(String message) {
        System.out.println(message);
    }

    public void printHelp() {
        println("CHEESEIM TERMINAL");
        println("commands:");
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
        println("CHEESEIM TERMINAL");
        println("status:");
        println("  userId=" + state.session().getUserId());
        println("  platformId=" + state.session().getPlatformId());
        println("  tokenPresent=" + (state.session().getAccessToken() != null && !state.session().getAccessToken().isBlank()));
        println("  connectionState=" + state.session().getConnectionState());
        println("  latestClientMsgId=" + state.session().getLatestClientMsgId());
        println("  latestServerMsgId=" + state.session().getLatestServerMsgId());
        println("  activePeer=" + state.activePeerUserId());
    }

    public void printConversation(DemoState state, String peerUserId) {
        println(formatConversation(state, peerUserId));
    }

    public String formatConversation(DemoState state, String peerUserId) {
        StringBuilder builder = new StringBuilder();
        builder.append(line(" CHEESEIM TERMINAL ", '=')).append('\n');
        builder.append(twoColumn(
                renderSidebar(state, peerUserId),
                renderHeader(state, peerUserId)
        ));
        for (DemoState.ChatMessage message : state.messages(peerUserId)) {
            builder.append(renderMessageRow(message, peerUserId)).append('\n');
        }
        if (state.isPeerTyping(peerUserId)) {
            builder.append(twoColumn("", peerUserId + " typing...")).append('\n');
        }
        builder.append(twoColumn("", line(" INPUT ", '-'))).append('\n');
        builder.append(twoColumn("", "send " + peerUserId + " <text>")).append('\n');
        builder.append(twoColumn("", "typing " + peerUserId + " | read " + peerUserId + " | revoke " + peerUserId));
        return builder.toString().trim();
    }

    private String renderSidebar(DemoState state, String activePeerUserId) {
        StringBuilder sidebar = new StringBuilder();
        sidebar.append("RECENTS").append('\n');
        for (String peer : state.conversationPeers()) {
            String prefix = peer.equals(activePeerUserId) ? "> " : "  ";
            sidebar.append(prefix).append(peer);
            if (state.isPeerTyping(peer)) {
                sidebar.append(" *");
            }
            sidebar.append('\n');
        }
        if (state.conversationPeers().isEmpty()) {
            sidebar.append("  no conversations").append('\n');
        }
        sidebar.append('\n');
        sidebar.append("SESSION").append('\n');
        sidebar.append("  user ").append(nullSafe(state.session().getUserId())).append('\n');
        sidebar.append("  conn ").append(String.valueOf(state.session().getConnectionState())).append('\n');
        return sidebar.toString().trim();
    }

    private String renderHeader(DemoState state, String peerUserId) {
        return "ACTIVE CHAT\n"
                + "  peer  " + peerUserId + "\n"
                + "  state " + String.valueOf(state.session().getConnectionState()) + "\n"
                + "  token " + ((state.session().getAccessToken() == null || state.session().getAccessToken().isBlank()) ? "missing" : "ready");
    }

    private String renderMessageRow(DemoState.ChatMessage message, String peerUserId) {
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
        return twoColumn("", bubble.toString());
    }

    private static void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String align(String line, boolean outgoing, int contentWidth) {
        if (!outgoing) {
            return line;
        }
        int mainWidth = SCREEN_WIDTH - SIDEBAR_WIDTH - 3;
        int pad = Math.max(0, mainWidth - Math.max(contentWidth, line.length()));
        return " ".repeat(pad) + line;
    }

    private static String twoColumn(String left, String right) {
        String[] leftLines = left.split("\\R", -1);
        String[] rightLines = right.split("\\R", -1);
        int lineCount = Math.max(leftLines.length, rightLines.length);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            String leftLine = i < leftLines.length ? leftLines[i] : "";
            String rightLine = i < rightLines.length ? rightLines[i] : "";
            builder.append(padRight(trimToWidth(leftLine, SIDEBAR_WIDTH), SIDEBAR_WIDTH))
                    .append(" | ")
                    .append(rightLine);
            if (i + 1 < lineCount) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String line(String label, char fill) {
        int totalWidth = SCREEN_WIDTH;
        int side = Math.max(2, (totalWidth - label.length()) / 2);
        String prefix = String.valueOf(fill).repeat(side);
        String suffix = String.valueOf(fill).repeat(Math.max(2, totalWidth - prefix.length() - label.length()));
        return prefix + label + suffix;
    }

    private static String trimToWidth(String text, int width) {
        if (text.length() <= width) {
            return text;
        }
        return text.substring(0, Math.max(0, width - 1)) + ".";
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
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
