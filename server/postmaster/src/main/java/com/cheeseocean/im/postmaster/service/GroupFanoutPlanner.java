package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 群消息扩散规划器。
 *
 * <p>对于 {@link com.cheeseocean.im.common.api.enums.GroupTypeEnum#NORMAL_GROUP} 的群消息，
 * postmaster 在 ingress 热路径将一条群消息以写扩散方式投递给每位成员：
 * <ol>
 *   <li>按 {@code batchSize} 将成员列表切片，避免过长群单次 publish 阻塞队列</li>
 *   <li>每片对应一次批量 publish 调用，由 {@link com.cheeseocean.im.postmaster.sender.MessageProducer}
 *       在内部对每个具体成员再生成一个 keyed DeliveryEvent</li>
 * </ol>
 *
 * <p>超级群（{@link com.cheeseocean.im.common.api.enums.GroupTypeEnum#SUPER_GROUP}）走读扩散，
 * 不调用本规划器，仅持久化后由客户端按 seq 拉取。
 */
@Component
public class GroupFanoutPlanner {

    private final int batchSize;

    public GroupFanoutPlanner(@Value("${cheeseim.delivery.group-fanout.batch-size:500}") int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * 将成员列表按 {@code batchSize} 切片，返回的每个子列表即一次"批量 publish"的单位。
     *
     * <p>切片只切片成员，不复制消息模板——消息复制由 {@code MessageProducer} 在序列化时通过
     * protobuf builder 替换 {@code receiverId} 完成，避免 Java 侧深拷贝，参见
     * {@link com.cheeseocean.im.postmaster.sender.MessageProducer#publishForMember}。
     */
    public List<List<String>> partition(List<String> memberIds) {
        List<List<String>> batches = new ArrayList<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return batches;
        }
        for (int start = 0; start < memberIds.size(); start += batchSize) {
            int end = Math.min(start + batchSize, memberIds.size());
            batches.add(new ArrayList<>(memberIds.subList(start, end)));
        }
        return batches;
    }

    /**
     * 计算单条群消息的投递 partition key。
     *
     * <p>使用 {@code g:{groupId}:{memberId}} 形式：既保留群维度可观测性，
     * 又让同一成员在该群内的消息落到同一 Kafka 分区，保证按序投递。
     */
    public String deliveryKey(String groupId, String memberId) {
        return "g:" + groupId + ":" + memberId;
    }
}