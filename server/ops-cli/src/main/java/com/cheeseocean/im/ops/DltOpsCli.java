package com.cheeseocean.im.ops;

import com.cheeseocean.im.common.core.config.CommonJacksonConfig;
import com.cheeseocean.im.common.core.queue.dlt.DltOperations;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CheeseIM 独立运维 CLI。
 *
 * <p>当前只提供 Kafka DLT 非破坏性查询与单条受控 redrive。它不开放 HTTP 端口，
 * Kafka/Mongo 凭据与运行主机权限构成运维授权边界。</p>
 */
@SpringBootApplication
@Import({
        CommonJacksonConfig.class
})
public class DltOpsCli {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DltOpsCli.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.config.name", "application-ops",
                "cheeseim.runtime.mode", "cluster",
                "cheeseim.queue.type", "kafka"));
        int exitCode = 1;
        try (ConfigurableApplicationContext context = application.run(args)) {
            execute(
                    context.getBean(DltOperations.class),
                    context.getBean(ObjectMapper.class),
                    args,
                    System.getenv());
            exitCode = 0;
        } catch (RuntimeException exception) {
            System.err.println("DLT operation failed: " + exception.getMessage());
        }
        System.exit(exitCode);
    }

    static void execute(DltOperations operations,
                        ObjectMapper objectMapper,
                        String[] args,
                        Map<String, String> environment) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException(usage());
        }
        String action = args[0];
        Map<String, String> options = parseOptions(args);
        try {
            if ("list".equals(action)) {
                Object result = operations.list(
                        required(options, "topic"),
                        integer(options, "partition", 0),
                        longValue(options, "after", -1L),
                        integer(options, "limit", 50));
                System.out.println(objectMapper.writeValueAsString(result));
                return;
            }
            if ("redrive".equals(action)) {
                String operatorId = environment.get("CHEESEIM_DLT_OPERATOR_ID");
                if (operatorId == null || operatorId.isBlank()) {
                    throw new IllegalArgumentException(
                            "CHEESEIM_DLT_OPERATOR_ID is required for redrive");
                }
                DltRedriveCommand command = new DltRedriveCommand(
                        required(options, "operation-id"),
                        required(options, "topic"),
                        integer(options, "partition", 0),
                        longValue(options, "offset", null),
                        required(options, "checksum"),
                        operatorId,
                        required(options, "reason"));
                System.out.println(objectMapper.writeValueAsString(operations.redrive(command)));
                return;
            }
            throw new IllegalArgumentException(usage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize DLT operation result", exception);
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
                throw new IllegalArgumentException(
                        "Options must use --name=value format: " + argument);
            }
            int separator = argument.indexOf('=');
            String name = argument.substring(2, separator);
            String value = argument.substring(separator + 1);
            if (name.isBlank() || value.isBlank() || options.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Invalid or duplicate option: " + argument);
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required");
        }
        return value;
    }

    private static int integer(Map<String, String> options, String name, int defaultValue) {
        String value = options.get(name);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static long longValue(Map<String, String> options,
                                  String name,
                                  Long defaultValue) {
        String value = options.get(name);
        if (value == null) {
            if (defaultValue == null) {
                throw new IllegalArgumentException("--" + name + " is required");
            }
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private static String usage() {
        return "Usage: list --topic=<topic> --partition=<n> [--after=-1] [--limit=50] "
                + "| redrive --operation-id=<id> --topic=<topic> --partition=<n> "
                + "--offset=<n> --checksum=<sha256> --reason=<text>";
    }
}
