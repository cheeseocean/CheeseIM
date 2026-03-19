package com.cheeseocean.im.postoffice.startup;

import com.cheeseocean.im.common.startup.StartupSummaryContributor;
import com.cheeseocean.im.common.startup.StartupSummaryItem;
import com.cheeseocean.im.postoffice.config.IMServerConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PostOfficeStartupSummaryContributor implements StartupSummaryContributor {

    private final IMServerConfig serverConfig;

    public PostOfficeStartupSummaryContributor(IMServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public List<StartupSummaryItem> contribute() {
        List<StartupSummaryItem> items = new ArrayList<>();
        IMServerConfig.WebSocketConfig websocket = serverConfig.getWebsocket();
        IMServerConfig.TcpConfig tcp = serverConfig.getTcp();

        items.add(new StartupSummaryItem("WebSocket", formatWebSocketEndpoint(websocket)));
        items.add(new StartupSummaryItem("TCP", formatTcpEndpoint(tcp)));
        return items;
    }

    private String formatWebSocketEndpoint(IMServerConfig.WebSocketConfig websocket) {
        if (!websocket.isEnabled()) {
            return "disabled";
        }
        String protocol = websocket.getSsl().isEnabled() ? "wss" : "ws";
        return protocol + "://localhost:" + websocket.getPort() + websocket.getPath();
    }

    private String formatTcpEndpoint(IMServerConfig.TcpConfig tcp) {
        if (!tcp.isEnabled()) {
            return "disabled";
        }
        return "tcp://localhost:" + tcp.getPort();
    }
}
