package com.cheeseocean.im.client.cli;

import com.cheeseocean.im.client.auth.AuthHttpClient;
import com.cheeseocean.im.client.tcp.TcpClientConfig;

public class CliApplication {

    public static void main(String[] args) throws Exception {
        CliArgs cliArgs = CliArgs.parse(args);
        ConsolePrinter printer = new ConsolePrinter();
        if (cliArgs.help()) {
            printer.printHelp();
            printer.println("Options:");
            printer.println("  --host <host>");
            printer.println("  --tcp-port <port>");
            printer.println("  --base-url <http://host:port>");
            return;
        }

        AuthHttpClient authHttpClient = new AuthHttpClient(cliArgs.baseUrl());
        TcpClientConfig tcpClientConfig = new TcpClientConfig(cliArgs.host(), cliArgs.tcpPort());
        DemoState demoState = new DemoState();
        new CliCommandLoop(authHttpClient, tcpClientConfig, demoState, printer).run();
    }

    private record CliArgs(String host, int tcpPort, String baseUrl, boolean help) {

        private static CliArgs parse(String[] args) {
            String host = "127.0.0.1";
            int tcpPort = 5148;
            String baseUrl = "http://127.0.0.1:8080";
            boolean help = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help" -> help = true;
                    case "--host" -> host = args[++i];
                    case "--tcp-port" -> tcpPort = Integer.parseInt(args[++i]);
                    case "--base-url" -> baseUrl = args[++i];
                    default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
                }
            }

            return new CliArgs(host, tcpPort, baseUrl, help);
        }
    }
}
