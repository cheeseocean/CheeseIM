package com.cheeseocean.im.client.cli;

import com.cheeseocean.im.client.auth.AuthHttpClient;
import com.cheeseocean.im.client.auth.AuthLoginRequest;
import com.cheeseocean.im.client.auth.AuthLoginResponse;
import com.cheeseocean.im.client.auth.WsTicketResponse;
import com.cheeseocean.im.client.protocol.TcpPacket;
import com.cheeseocean.im.client.session.ConnectionState;
import com.cheeseocean.im.client.tcp.IncomingMessageListener;
import com.cheeseocean.im.client.tcp.TcpClientConfig;
import com.cheeseocean.im.client.tcp.TcpImClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CliCommandLoop {

    private final AuthHttpClient authHttpClient;
    private final TcpClientConfig tcpClientConfig;
    private final DemoState demoState;
    private final ConsolePrinter printer;

    public CliCommandLoop(AuthHttpClient authHttpClient,
                          TcpClientConfig tcpClientConfig,
                          DemoState demoState,
                          ConsolePrinter printer) {
        this.authHttpClient = authHttpClient;
        this.tcpClientConfig = tcpClientConfig;
        this.demoState = demoState;
        this.printer = printer;
    }

    public static ParsedCommand parse(String line) {
        if (line == null || line.isBlank()) {
            return new ParsedCommand(ParsedCommand.Type.HELP, null, null, null);
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("login ")) {
            String[] parts = trimmed.split("\\s+", 4);
            if (parts.length < 4) {
                return new ParsedCommand(ParsedCommand.Type.HELP, null, null, null);
            }
            return new ParsedCommand(ParsedCommand.Type.LOGIN, parts[1], parts[2], parts[3]);
        }
        if (trimmed.startsWith("send ")) {
            String[] parts = trimmed.split("\\s+", 3);
            if (parts.length < 3) {
                return new ParsedCommand(ParsedCommand.Type.HELP, null, null, null);
            }
            return new ParsedCommand(ParsedCommand.Type.SEND, parts[1], parts[2], null);
        }
        return switch (trimmed) {
            case "connect" -> new ParsedCommand(ParsedCommand.Type.CONNECT, null, null, null);
            case "heartbeat" -> new ParsedCommand(ParsedCommand.Type.HEARTBEAT, null, null, null);
            case "status" -> new ParsedCommand(ParsedCommand.Type.STATUS, null, null, null);
            case "quit" -> new ParsedCommand(ParsedCommand.Type.QUIT, null, null, null);
            default -> new ParsedCommand(ParsedCommand.Type.HELP, null, null, null);
        };
    }

    public void run() throws IOException {
        printer.printHelp();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    return;
                }
                ParsedCommand command = parse(line);
                if (handle(command)) {
                    return;
                }
            }
        }
    }

    private boolean handle(ParsedCommand command) throws IOException {
        switch (command.type()) {
            case LOGIN -> handleLogin(command);
            case CONNECT -> handleConnect();
            case SEND -> handleSend(command);
            case HEARTBEAT -> handleHeartbeat();
            case STATUS -> printer.printStatus(demoState);
            case HELP -> printer.printHelp();
            case QUIT -> {
                printer.println("bye");
                return true;
            }
        }
        return false;
    }

    private void handleLogin(ParsedCommand command) {
        Integer platformId = Integer.parseInt(command.arg3());
        AuthLoginResponse response = authHttpClient.login(new AuthLoginRequest(
                command.arg1(),
                command.arg2(),
                platformId,
                command.arg1() + "-device-" + platformId,
                "cli-demo-1.0.0"
        ));
        demoState.setLoginResponse(response);
        demoState.session().setUserId(response.userId());
        demoState.session().setPlatformId(platformId);
        demoState.session().setAccessToken(response.accessToken());
        printer.println("login ok: sessionId=" + response.sessionId());
    }

    private void handleConnect() throws IOException {
        if (demoState.session().getAccessToken() == null || demoState.session().getAccessToken().isBlank()) {
            throw new IllegalStateException("login first");
        }
        WsTicketResponse wsTicket = authHttpClient.issueWsTicket(demoState.session().getAccessToken());
        demoState.session().setWsTicket(wsTicket.ticket());
        TcpImClient client = new TcpImClient(
                tcpClientConfig,
                demoState.session(),
                new TcpImClient.SocketTransportFactory(),
                new LoggingListener(printer, demoState)
        );
        demoState.setTcpImClient(client);
        client.connect();
        client.authenticate();
        printer.println("connect requested");
    }

    private void handleSend(ParsedCommand command) throws IOException {
        if (demoState.tcpImClient() == null || demoState.session().getConnectionState() == ConnectionState.DISCONNECTED) {
            throw new IllegalStateException("connect first");
        }
        String operationId = demoState.tcpImClient().sendText(command.arg1(), command.arg2());
        printer.println("send requested: operationId=" + operationId);
    }

    private void handleHeartbeat() throws IOException {
        if (demoState.tcpImClient() == null) {
            throw new IllegalStateException("connect first");
        }
        demoState.tcpImClient().heartbeat();
        printer.println("heartbeat sent");
    }

    static final class LoggingListener implements IncomingMessageListener {

        private final ConsolePrinter printer;
        private final DemoState demoState;

        private LoggingListener(ConsolePrinter printer, DemoState demoState) {
            this.printer = printer;
            this.demoState = demoState;
        }

        @Override
        public void onConnected() {
            printer.println("[tcp] connected");
        }

        @Override
        public void onAuthSuccess() {
            printer.println("[tcp] auth success");
        }

        @Override
        public void onAuthFailed(TcpPacket packet) {
            printer.println("[tcp] auth failed: " + packet.data());
        }

        @Override
        public void onSendAck(TcpPacket packet) {
            demoState.session().setLatestServerMsgId(extractField(packet.data(), "serverMsgID"));
            printer.println("[tcp] send ack: " + packet.data());
        }

        @Override
        public void onMessage(TcpPacket packet) {
            printer.println("[tcp] inbound: " + formatInboundMessage(packet.data()));
        }

        @Override
        public void onDisconnected() {
            demoState.session().setConnectionState(ConnectionState.DISCONNECTED);
            printer.println("[tcp] disconnected");
        }

        @Override
        public void onError(Throwable error) {
            printer.println("[tcp] error: " + error.getMessage());
        }

        private static String extractField(String json, String field) {
            String pattern = "\"" + field + "\":\"";
            int start = json.indexOf(pattern);
            if (start < 0) {
                return null;
            }
            int valueStart = start + pattern.length();
            int valueEnd = json.indexOf('"', valueStart);
            if (valueEnd < 0) {
                return null;
            }
            return json.substring(valueStart, valueEnd);
        }

        static String formatInboundMessage(String json) {
            String from = valueOrDash(extractField(json, "sendID"));
            String to = valueOrDash(extractField(json, "recvID"));
            String content = valueOrDash(extractField(json, "content"));
            String serverMsgId = valueOrDash(firstNonBlank(
                    extractField(json, "serverMsgID"),
                    extractField(json, "serverMsgId")
            ));
            String seq = valueOrDash(extractNumericField(json, "seq"));
            return "from=" + from
                    + " to=" + to
                    + " content=" + content
                    + " serverMsgId=" + serverMsgId
                    + " seq=" + seq;
        }

        private static String extractNumericField(String json, String field) {
            String pattern = "\"" + field + "\":";
            int start = json.indexOf(pattern);
            if (start < 0) {
                return null;
            }
            int valueStart = start + pattern.length();
            int valueEnd = valueStart;
            while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
                valueEnd++;
            }
            if (valueEnd == valueStart) {
                return null;
            }
            return json.substring(valueStart, valueEnd);
        }

        private static String valueOrDash(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
    }
}
