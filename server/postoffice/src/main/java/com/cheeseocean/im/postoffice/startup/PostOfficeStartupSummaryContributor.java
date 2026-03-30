package com.cheeseocean.im.postoffice.startup;

import com.cheeseocean.im.common.core.startup.StartupSummaryContributor;
import com.cheeseocean.im.common.core.startup.StartupSummaryItem;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PostOfficeStartupSummaryContributor implements StartupSummaryContributor {

    private final ServerProperties serverConfig;

    public PostOfficeStartupSummaryContributor(ServerProperties serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public List<StartupSummaryItem> contribute() {
        List<StartupSummaryItem>         items     = new ArrayList<>();
        ServerProperties.WebSocketConfig websocket = serverConfig.getWebsocket();
        ServerProperties.TcpConfig       tcp       = serverConfig.getTcp();

        items.add(new StartupSummaryItem("WebSocket", formatWebSocketEndpoint(websocket)));
        items.add(new StartupSummaryItem("TCP", formatTcpEndpoint(tcp)));
        return items;
    }

    private String formatWebSocketEndpoint(ServerProperties.WebSocketConfig websocket) {
        if (!websocket.isEnabled()) {
            return "disabled";
        }
        String protocol = websocket.getSsl().isEnabled() ? "wss" : "ws";
        return protocol + "://localhost:" + websocket.getPort() + websocket.getPath();
    }

    private String formatTcpEndpoint(ServerProperties.TcpConfig tcp) {
        if (!tcp.isEnabled()) {
            return "disabled";
        }
        return "tcp://localhost:" + tcp.getPort();
    }
}
