package com.cheeseocean.im.infra.queue.dlt;

import com.cheeseocean.im.common.core.queue.dlt.DltOperations;
import com.cheeseocean.im.common.core.queue.dlt.DltPage;
import com.cheeseocean.im.common.core.queue.dlt.DltRecordSummary;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveAuditStore;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveCommand;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveResult;

import com.cheeseocean.im.common.api.enums.DltRedriveStatus;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Kafka DLT 非破坏性查询与受控重放实现。
 */
public class KafkaDltOperations implements DltOperations {

    private static final String REDRIVE_OPERATION_HEADER =
            "cheeseim_redrive_operation_id";
    private static final String REDRIVE_DLT_IDENTITY_HEADER =
            "cheeseim_redrive_dlt_identity";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final KafkaProperties kafkaProperties;
    private final DltRedriveAuditStore auditStore;
    private final int maxQueryRecords;
    private final long pollTimeoutMillis;
    private final long operationLeaseMillis;

    public KafkaDltOperations(KafkaTemplate<String, byte[]> kafkaTemplate,
                              KafkaProperties kafkaProperties,
                              DltRedriveAuditStore auditStore,
                              int maxQueryRecords,
                              long pollTimeoutMillis,
                              long operationLeaseMillis) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
        this.auditStore = auditStore;
        this.maxQueryRecords = Math.min(500, Math.max(1, maxQueryRecords));
        this.pollTimeoutMillis = Math.min(10_000L, Math.max(100L, pollTimeoutMillis));
        this.operationLeaseMillis = Math.min(300_000L, Math.max(10_000L, operationLeaseMillis));
    }

    @Override
    public DltPage list(String sourceTopic,
                        int partition,
                        long afterOffset,
                        int limit) {
        long startedAt = ImMetrics.startTimer();
        boolean success = false;
        try {
            DltPage result = listInternal(sourceTopic, partition, afterOffset, limit);
            success = true;
            return result;
        } finally {
            ImMetrics.dltOperation("list", metricTopic(sourceTopic), success, startedAt);
        }
    }

    private DltPage listInternal(String sourceTopic,
                                 int partition,
                                 long afterOffset,
                                 int limit) {
        requireManagedTopic(sourceTopic);
        requireNonNegativePartition(partition);
        int pageSize = Math.min(maxQueryRecords, Math.max(1, limit));
        TopicPartition topicPartition = new TopicPartition(sourceTopic + ".DLT", partition);
        try (KafkaConsumer<String, byte[]> consumer = newConsumer()) {
            requirePartition(consumer, topicPartition);
            consumer.assign(List.of(topicPartition));
            long beginning = consumer.beginningOffsets(List.of(topicPartition)).get(topicPartition);
            long end = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
            long start = Math.max(beginning, afterOffset < 0L ? beginning : afterOffset + 1L);
            if (start >= end) {
                return new DltPage(
                        sourceTopic, partition, beginning, end, afterOffset, List.of());
            }
            consumer.seek(topicPartition, start);
            List<DltRecordSummary> records = new ArrayList<>(pageSize);
            long deadline = System.currentTimeMillis() + pollTimeoutMillis;
            while (records.size() < pageSize && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> polled =
                        consumer.poll(Duration.ofMillis(Math.min(250L, pollTimeoutMillis)));
                for (ConsumerRecord<String, byte[]> record : polled.records(topicPartition)) {
                    if (record.offset() >= end || records.size() >= pageSize) {
                        break;
                    }
                    records.add(toSummary(sourceTopic, record));
                }
                if (consumer.position(topicPartition) >= end) {
                    break;
                }
            }
            long nextAfter = records.isEmpty()
                    ? afterOffset
                    : records.get(records.size() - 1).offset();
            return new DltPage(
                    sourceTopic,
                    partition,
                    beginning,
                    end,
                    nextAfter,
                    List.copyOf(records));
        }
    }

    @Override
    public DltRedriveResult redrive(DltRedriveCommand command) {
        String sourceTopic = command == null ? null : command.sourceTopic();
        long startedAt = ImMetrics.startTimer();
        boolean success = false;
        try {
            DltRedriveResult result = redriveInternal(command);
            success = true;
            return result;
        } finally {
            ImMetrics.dltOperation("redrive", metricTopic(sourceTopic), success, startedAt);
        }
    }

    private DltRedriveResult redriveInternal(DltRedriveCommand command) {
        validateCommand(command);
        ConsumerRecord<String, byte[]> record =
                fetchExact(command.sourceTopic(), command.partition(), command.offset());
        validateOriginalTopic(command.sourceTopic(), record);
        String checksum = checksum(record);
        if (!checksum.equals(command.expectedChecksum())) {
            throw new IllegalArgumentException("DLT record checksum changed or does not match");
        }
        String ownerToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        DltRedriveAuditStore.Claim claim = auditStore.claim(
                command, checksum, ownerToken, now, operationLeaseMillis);
        if (claim.status() == DltRedriveAuditStore.ClaimStatus.COMPLETED) {
            return result(command, checksum, DltRedriveStatus.COMPLETED);
        }
        if (claim.status() != DltRedriveAuditStore.ClaimStatus.ACQUIRED) {
            throw new IllegalStateException(
                    "DLT redrive operation is already in progress: " + command.operationId());
        }
        try {
            // redrive 是一次新发布，不能沿用旧 CreateTime；否则 broker 可能按旧时间立即清理记录。
            ProducerRecord<String, byte[]> redrive = new ProducerRecord<>(
                    command.sourceTopic(), record.key(), record.value());
            redrive.headers().add(
                    REDRIVE_OPERATION_HEADER,
                    command.operationId().getBytes(StandardCharsets.UTF_8));
            redrive.headers().add(
                    REDRIVE_DLT_IDENTITY_HEADER,
                    dltIdentity(record).getBytes(StandardCharsets.UTF_8));
            awaitBrokerAck(redrive);
            if (!auditStore.complete(
                    command.operationId(),
                    ownerToken,
                    claim.generation(),
                    System.currentTimeMillis())) {
                throw new IllegalStateException(
                        "DLT redrive broker ACK succeeded but audit lease was lost");
            }
            return result(command, checksum, DltRedriveStatus.COMPLETED);
        } catch (RuntimeException exception) {
            auditStore.fail(
                    command.operationId(),
                    ownerToken,
                    claim.generation(),
                    System.currentTimeMillis(),
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
            throw exception;
        }
    }

    private ConsumerRecord<String, byte[]> fetchExact(
            String sourceTopic,
            int partition,
            long offset) {
        requireManagedTopic(sourceTopic);
        if (offset < 0L) {
            throw new IllegalArgumentException("DLT offset must be non-negative");
        }
        TopicPartition topicPartition = new TopicPartition(sourceTopic + ".DLT", partition);
        try (KafkaConsumer<String, byte[]> consumer = newConsumer()) {
            requirePartition(consumer, topicPartition);
            consumer.assign(List.of(topicPartition));
            long beginning = consumer.beginningOffsets(List.of(topicPartition)).get(topicPartition);
            long end = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
            if (offset < beginning || offset >= end) {
                throw new IllegalArgumentException(
                        "DLT offset is outside retained range: " + beginning + ".." + end);
            }
            consumer.seek(topicPartition, offset);
            long deadline = System.currentTimeMillis() + pollTimeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records =
                        consumer.poll(Duration.ofMillis(Math.min(250L, pollTimeoutMillis)));
                for (ConsumerRecord<String, byte[]> record : records.records(topicPartition)) {
                    if (record.offset() == offset) {
                        return record;
                    }
                    if (record.offset() > offset) {
                        break;
                    }
                }
            }
            throw new IllegalStateException("Timed out reading exact DLT record");
        }
    }

    private KafkaConsumer<String, byte[]> newConsumer() {
        Map<String, Object> properties =
                new java.util.HashMap<>(kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "cheeseim-dlt-ops-" + UUID.randomUUID());
        properties.remove(ConsumerConfig.GROUP_ID_CONFIG);
        return new KafkaConsumer<>(properties);
    }

    private void requirePartition(
            KafkaConsumer<String, byte[]> consumer,
            TopicPartition topicPartition) {
        boolean exists = consumer.partitionsFor(topicPartition.topic())
                .stream()
                .anyMatch(info -> info.partition() == topicPartition.partition());
        if (!exists) {
            throw new IllegalArgumentException(
                    "Unknown DLT partition: " + topicPartition);
        }
    }

    private DltRecordSummary toSummary(
            String sourceTopic,
            ConsumerRecord<String, byte[]> record) {
        return new DltRecordSummary(
                sourceTopic,
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                fingerprint(record.key()),
                record.value() == null ? 0 : record.value().length,
                checksum(record),
                headerText(record, KafkaHeaders.DLT_EXCEPTION_FQCN, 256),
                headerText(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, 256));
    }

    private void validateOriginalTopic(
            String expectedSourceTopic,
            ConsumerRecord<String, byte[]> record) {
        String originalTopic =
                headerText(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, 256);
        if (originalTopic != null && !originalTopic.equals(expectedSourceTopic)) {
            throw new IllegalArgumentException(
                    "DLT original topic header does not match requested source topic");
        }
    }

    private String headerText(
            ConsumerRecord<String, byte[]> record,
            String name,
            int maxLength) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String checksum(ConsumerRecord<String, byte[]> record) {
        MessageDigest digest = sha256();
        digest.update(record.topic().getBytes(StandardCharsets.UTF_8));
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(record.partition()).array());
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(record.offset()).array());
        if (record.key() != null) {
            digest.update(record.key().getBytes(StandardCharsets.UTF_8));
        }
        if (record.value() != null) {
            digest.update(record.value());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String fingerprint(String value) {
        if (value == null) {
            return null;
        }
        return HexFormat.of().formatHex(
                sha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String dltIdentity(ConsumerRecord<String, byte[]> record) {
        return record.topic() + ":" + record.partition() + ":" + record.offset();
    }

    private void awaitBrokerAck(ProducerRecord<String, byte[]> record) {
        try {
            kafkaTemplate.send(record).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while redriving DLT record", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Kafka rejected DLT redrive", cause);
        }
    }

    private DltRedriveResult result(
            DltRedriveCommand command,
            String checksum,
            DltRedriveStatus status) {
        return new DltRedriveResult(
                command.operationId(),
                command.sourceTopic(),
                command.partition(),
                command.offset(),
                checksum,
                status);
    }

    private void validateCommand(DltRedriveCommand command) {
        if (command == null
                || isBlank(command.operationId())
                || isBlank(command.sourceTopic())
                || isBlank(command.expectedChecksum())
                || isBlank(command.operatorId())
                || isBlank(command.reason())) {
            throw new IllegalArgumentException(
                    "operationId, sourceTopic, checksum, operatorId and reason are required");
        }
        if (command.operationId().length() > 128
                || command.operatorId().length() > 128
                || command.reason().length() > 512) {
            throw new IllegalArgumentException("DLT redrive command field is too long");
        }
        requireNonNegativePartition(command.partition());
        if (command.offset() < 0L) {
            throw new IllegalArgumentException("DLT offset must be non-negative");
        }
        if (!command.expectedChecksum().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "DLT checksum must be a lowercase SHA-256 value");
        }
        requireManagedTopic(command.sourceTopic());
    }

    private void requireNonNegativePartition(int partition) {
        if (partition < 0) {
            throw new IllegalArgumentException("DLT partition must be non-negative");
        }
    }

    private void requireManagedTopic(String sourceTopic) {
        if (!TopicNames.isManagedTopic(sourceTopic)) {
            throw new IllegalArgumentException(
                    "DLT operations only allow managed source topics");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String metricTopic(String sourceTopic) {
        return TopicNames.isManagedTopic(sourceTopic) ? sourceTopic : "invalid";
    }
}
