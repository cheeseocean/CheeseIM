package com.cheeseocean.im.common.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StartupSummaryRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryRunner.class);

    private final Environment environment;
    private final List<StartupSummaryContributor> contributors;

    public StartupSummaryRunner(Environment environment, List<StartupSummaryContributor> contributors) {
        this.environment = environment;
        this.contributors = contributors == null ? List.of() : contributors;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<StartupSummaryItem> items = new ArrayList<>();
        items.add(new StartupSummaryItem("Application", resolveApplicationName()));
        items.add(new StartupSummaryItem("Profile", resolveProfiles()));

        String mode = environment.getProperty("app.runtime.mode");
        if (StringUtils.hasText(mode)) {
            items.add(new StartupSummaryItem("Mode", mode));
        }

        String httpEndpoint = resolveHttpEndpoint();
        if (StringUtils.hasText(httpEndpoint)) {
            items.add(new StartupSummaryItem("HTTP", httpEndpoint));
        }

        String dubboRegistry = resolveDubboRegistry();
        if (StringUtils.hasText(dubboRegistry)) {
            items.add(new StartupSummaryItem("Dubbo", dubboRegistry));
        }

        contributors.stream()
                .sorted(Comparator.comparing(contributor -> contributor.getClass().getName()))
                .map(StartupSummaryContributor::contribute)
                .forEach(items::addAll);

        int labelWidth = items.stream()
                .map(StartupSummaryItem::label)
                .mapToInt(String::length)
                .max()
                .orElse(11);

        StringBuilder builder = new StringBuilder()
                .append("\n")
                .append("==========================================================\n")
                .append(" CheeseIM Startup Summary\n");

        for (StartupSummaryItem item : items) {
            builder.append("  ")
                    .append(String.format("%-" + labelWidth + "s", item.label()))
                    .append(" : ")
                    .append(item.value())
                    .append('\n');
        }

        builder.append("==========================================================");
        log.info(builder.toString());
    }

    private String resolveApplicationName() {
        return environment.getProperty(
                "spring.application.name",
                environment.getProperty("dubbo.application.name", "application"));
    }

    private String resolveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 ? "default" : String.join(", ", activeProfiles);
    }

    private String resolveHttpEndpoint() {
        String port = environment.getProperty("server.port");
        if (!StringUtils.hasText(port)) {
            return null;
        }

        String protocol = StringUtils.hasText(environment.getProperty("server.ssl.key-store")) ? "https" : "http";
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        return protocol + "://" + resolveHostAddress() + ":" + port + contextPath;
    }

    private String resolveDubboRegistry() {
        boolean register = environment.getProperty("dubbo.registry.register", Boolean.class, true);
        boolean subscribe = environment.getProperty("dubbo.registry.subscribe", Boolean.class, true);
        boolean injvm = environment.getProperty("dubbo.consumer.injvm", Boolean.class, false)
                || environment.getProperty("dubbo.protocol.injvm", Boolean.class, false);

        if (!register && !subscribe) {
            return injvm ? "injvm-only" : "registry-disabled";
        }

        String address = environment.getProperty("dubbo.registry.address");
        if (!StringUtils.hasText(address)) {
            return injvm ? "injvm-preferred" : null;
        }
        return injvm ? address + " (injvm-preferred)" : address;
    }

    private String resolveHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            log.warn("Failed to resolve local host address, fallback to localhost");
            return "localhost";
        }
    }
}
