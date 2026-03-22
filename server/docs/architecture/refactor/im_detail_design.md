Cheese IM 研发落地设计

1. 系统边界

1.1 服务划分

postoffice
	•	接入层
	•	WebSocket / HTTP
	•	online session manager
	•	在线消息下发接口

postbox
	•	发送消息 RPC 入口
	•	ingress producer
	•	history consumer
	•	消息查询 RPC

postman
	•	ingress consumer
	•	消息编排
	•	seq 分配
	•	Redis 热数据写入
	•	history / delivery producer

push
	•	delivery consumer
	•	在线投递编排
	•	offlinepush producer / consumer

⸻

2. Topic 与消费组

2.1 Topic 定义

ingress

用途：消息入站主总线
producer：postbox
consumer：postman
key：conversationId

history

用途：历史持久化
producer：postman
consumer：postbox-history
key：conversationId

delivery

用途：在线投递编排
producer：postman
consumer：push-delivery
key：conversationId

offlinepush

用途：离线推送任务
producer：push-delivery
consumer：push-offline
key：userId

⸻

2.2 Topic 分区建议

初期建议：
	•	ingress: 64
	•	history: 32
	•	delivery: 64
	•	offlinepush: 64

原则：
	•	ingress 与 delivery 更容易成为热点
	•	history 主要是吞吐型写库
	•	offlinepush 更像用户任务队列

⸻

3. 核心对象定义

3.1 SendMessageReq

public class SendMessageReq {
    private String requestId;
    private String senderId;
    private Integer sessionType;   // 1单聊 2群聊 3通知
    private String recvId;
    private String groupId;
    private String clientMsgId;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;
}

3.2 SendMessageResp

public class SendMessageResp {
    private boolean accepted;
    private String requestId;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
}

3.3 MessageOptions

public class MessageOptions {
    private boolean needHistory;
    private boolean needConversation;
    private boolean needUnreadCount;
    private boolean needOnlinePush;
    private boolean needOfflinePush;
    private boolean senderSync;
    private boolean notification;
    private boolean needLastMessage;
}

3.4 IngressEvent

public class IngressEvent {
    private String requestId;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer sessionType;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;
}

3.5 SequencedMessage

public class SequencedMessage {
    private String conversationId;
    private Long seq;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer sessionType;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;
}

3.6 HistoryEvent

public class HistoryEvent {
    private String conversationId;
    private Long beginSeq;
    private Long endSeq;
    private List<SequencedMessage> messages;
}

3.7 DeliveryEvent

public class DeliveryEvent {
    private String conversationId;
    private SequencedMessage message;
    private List<String> targetUserIds;
}

3.8 OfflinePushEvent

public class OfflinePushEvent {
    private String userId;
    private String conversationId;
    private Long seq;
    private String serverMsgId;
    private String title;
    private String content;
    private Map<String, String> ext;
}


⸻

4. ConversationId 生成规范

4.1 单聊

public String genSingleConversationId(String userA, String userB) {
    return userA.compareTo(userB) < 0
        ? "c1:" + userA + ":" + userB
        : "c1:" + userB + ":" + userA;
}

4.2 群聊

public String genGroupConversationId(String groupId) {
    return "c2:" + groupId;
}

4.3 通知

public String genNotificationConversationId(String userId) {
    return "c3:" + userId;
}


⸻

5. Dubbo 接口定义

5.1 postoffice -> postbox

public interface MessageSendRpc {
    SendMessageResp sendMessage(SendMessageReq req);
}

发送接口约束
	•	requestId 必填
	•	clientMsgId 必填
	•	单聊 recvId 必填
	•	群聊 groupId 必填
	•	sendTime 允许客户端传入，但服务端需保底兜正
	•	options 允许为空，服务端补默认值

⸻

5.2 push -> postoffice 在线投递接口

public interface OnlineDispatchRpc {
    DispatchMessageResp dispatchMessage(DispatchMessageReq req);
}

public class DispatchMessageReq {
    private String userId;
    private List<String> connectionIds;
    private DispatchPayload payload;
}

public class DispatchPayload {
    private String conversationId;
    private Long seq;
    private String clientMsgId;
    private String serverMsgId;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private Map<String, String> ext;
}

public class DispatchMessageResp {
    private List<DispatchResult> results;
}

public class DispatchResult {
    private String connectionId;
    private boolean success;
    private String code;
    private String message;
}


⸻

5.3 postbox 查询 RPC

public interface MessageQueryRpc {
    PullBySeqRangeResp pullBySeqRange(PullBySeqRangeReq req);
    PullBySeqListResp pullBySeqList(PullBySeqListReq req);
    ConversationStateResp getConversationState(ConversationStateReq req);
    UserConversationStateResp getUserConversationState(UserConversationStateReq req);
}


⸻

6. Redis Key 设计

6.1 在线连接

online:user:{userId} -> Set(SessionRef)
online:conn:{connectionId} -> SessionMeta

SessionRef 建议内容：

{
  "nodeId": "postoffice-01",
  "connectionId": "conn_xxx",
  "deviceId": "ios_abc",
  "platform": 1,
  "lastSeen": 1710000000000
}


⸻

6.2 会话状态

conv:maxSeq:{conversationId} -> long
conv:minSeq:{conversationId} -> long
conv:lastMsg:{conversationId} -> MessageSummary(json)


⸻

6.3 用户会话状态

uc:read:{userId}:{conversationId} -> long
uc:min:{userId}:{conversationId} -> long
uc:max:{userId}:{conversationId} -> long
uc:unread:{userId}:{conversationId} -> int


⸻

6.4 消息缓存

方案 A：按单条存

msg:{conversationId}:{seq} -> SequencedMessage

适合先快速落地。

方案 B：按 block 存

msg:block:{conversationId}:{blockNo} -> Map<seq, msg>

适合后续优化。

先建议 A，简单直接。

⸻

6.5 幂等键

入口幂等

idem:ingress:{conversationId}:{clientMsgId}

postman 幂等

idem:postman:{conversationId}:{clientMsgId}

投递幂等

idem:delivery:{serverMsgId}:{userId}:{connectionId}


⸻

7. 数据库表设计

7.1 message_id_mapping

CREATE TABLE message_id_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id VARCHAR(128) NOT NULL,
    client_msg_id VARCHAR(128) NOT NULL,
    server_msg_id VARCHAR(128) NOT NULL,
    seq BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_conv_client (conversation_id, client_msg_id),
    UNIQUE KEY uk_server_msg (server_msg_id)
);

用途：
	•	重试查重
	•	根据 clientMsgId 找 seq
	•	支持补偿

⸻

7.2 conversation_state

CREATE TABLE conversation_state (
    conversation_id VARCHAR(128) PRIMARY KEY,
    session_type INT NOT NULL,
    max_seq BIGINT NOT NULL DEFAULT 0,
    min_seq BIGINT NOT NULL DEFAULT 1,
    last_msg_summary JSON NULL,
    updated_at DATETIME NOT NULL
);


⸻

7.3 user_conversation_state

CREATE TABLE user_conversation_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    read_seq BIGINT NOT NULL DEFAULT 0,
    user_min_seq BIGINT NOT NULL DEFAULT 0,
    user_max_seq BIGINT NOT NULL DEFAULT 0,
    unread_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_conv (user_id, conversation_id)
);


⸻

7.4 历史消息表

如果走 MySQL，建议不要一条条硬写热表，最好分表；
如果走 Mongo，更适合 block 方式。

Mongo 文档建议

{
  "_id": "c1:u1:u2:123",
  "conversationId": "c1:u1:u2",
  "blockNo": 123,
  "messages": [
    {
      "seq": 12301,
      "clientMsgId": "...",
      "serverMsgId": "...",
      "senderId": "...",
      "contentType": 101,
      "content": "...",
      "sendTime": 1710000000,
      "options": {}
    }
  ]
}

block 计算

long blockSize = 100;
long blockNo = (seq - 1) / blockSize;


⸻

8. postbox 落地实现

8.1 sendMessage 主流程

public SendMessageResp sendMessage(SendMessageReq req) {
    validate(req);

    String conversationId = buildConversationId(req);
    String serverMsgId = idGenerator.nextId();

    String idemKey = "idem:ingress:" + conversationId + ":" + req.getClientMsgId();
    boolean first = redis.setIfAbsent(idemKey, serverMsgId, Duration.ofDays(2));
    if (!first) {
        return buildAcceptedResp(req, conversationId, serverMsgId);
    }

    IngressEvent event = buildIngressEvent(req, conversationId, serverMsgId);

    kafkaTemplate.send("ingress", conversationId, serialize(event));

    return buildAcceptedResp(req, conversationId, serverMsgId);
}


⸻

8.2 默认消息策略补全

private MessageOptions fillDefaultOptions(SendMessageReq req) {
    MessageOptions opt = req.getOptions();
    if (opt == null) {
        opt = new MessageOptions();
    }

    opt.setNeedHistory(defaultTrue(opt.isNeedHistory()));
    opt.setNeedConversation(defaultTrue(opt.isNeedConversation()));
    opt.setNeedUnreadCount(defaultTrue(opt.isNeedUnreadCount()));
    opt.setNeedOnlinePush(defaultTrue(opt.isNeedOnlinePush()));
    opt.setNeedOfflinePush(defaultTrue(opt.isNeedOfflinePush()));
    opt.setNeedLastMessage(defaultTrue(opt.isNeedLastMessage()));

    return opt;
}

不要把默认策略散落在前后多个服务里，入口统一补一遍，postman 再做最终裁决。

⸻

8.3 history consumer 主流程

public void onHistoryEvent(HistoryEvent event) {
    for (SequencedMessage msg : event.getMessages()) {
        persistHistory(msg);
        persistIdMapping(msg);
    }
}

约束
	•	历史入库必须幂等
	•	映射表必须幂等
	•	历史失败不应污染 seq 分配链路

⸻

9. postman 落地实现

9.1 消费模型

使用批消费，但批内必须按 conversationId 二次分组。

public void onIngressBatch(List<ConsumerRecord<String, byte[]>> records) {
    Map<String, List<IngressEvent>> grouped = new HashMap<>();
    for (ConsumerRecord<String, byte[]> record : records) {
        IngressEvent event = deserialize(record.value());
        grouped.computeIfAbsent(event.getConversationId(), k -> new ArrayList<>()).add(event);
    }

    for (Map.Entry<String, List<IngressEvent>> entry : grouped.entrySet()) {
        handleConversationBatch(entry.getKey(), entry.getValue());
    }
}


⸻

9.2 单会话批处理流程

private void handleConversationBatch(String conversationId, List<IngressEvent> events) {
    List<IngressEvent> deduped = filterDuplicate(conversationId, events);
    if (deduped.isEmpty()) {
        return;
    }

    MessageRouteDecision decision = policyEngine.decideBatch(deduped);

    long baseSeq = seqAllocator.allocate(conversationId, deduped.size());

    List<SequencedMessage> messages = assignSeq(baseSeq, deduped);

    cacheWriter.writeMessages(messages);

    if (decision.isUpdateConversation()) {
        conversationStateManager.updateConversation(messages, decision);
    }

    if (decision.isPersistHistory()) {
        historyProducer.send(buildHistoryEvent(conversationId, messages));
    }

    if (decision.isSendDelivery()) {
        for (DeliveryEvent event : buildDeliveryEvents(messages, decision)) {
            deliveryProducer.send(event);
        }
    }
}


⸻

9.3 Seq 分配器

建议先基于 Redis INCRBY：

public long allocate(String conversationId, int count) {
    Long oldMax = redis.incrBy("conv:maxSeq:" + conversationId, count);
    return oldMax - count;
}

如果返回值是分配前 maxSeq，那么消息 seq 为：

seq = baseSeq + i + 1;


⸻

9.4 编排幂等

private List<IngressEvent> filterDuplicate(String conversationId, List<IngressEvent> events) {
    List<IngressEvent> result = new ArrayList<>();
    for (IngressEvent e : events) {
        String key = "idem:postman:" + conversationId + ":" + e.getClientMsgId();
        boolean first = redis.setIfAbsent(key, e.getServerMsgId(), Duration.ofDays(2));
        if (first) {
            result.add(e);
        }
    }
    return result;
}


⸻

9.5 MessagePolicyEngine

建议把策略判断独立类化。

public interface MessagePolicyEngine {
    MessageRouteDecision decide(IngressEvent event);
}

public class DefaultMessagePolicyEngine implements MessagePolicyEngine {
    @Override
    public MessageRouteDecision decide(IngressEvent event) {
        MessageOptions o = event.getOptions();
        MessageRouteDecision d = new MessageRouteDecision();
        d.setPersistHistory(o.isNeedHistory());
        d.setUpdateConversation(o.isNeedConversation());
        d.setUpdateUnread(o.isNeedUnreadCount());
        d.setSendDelivery(o.isNeedOnlinePush());
        d.setSendOfflineIfFail(o.isNeedOfflinePush());
        d.setSenderSync(o.isSenderSync());
        d.setNotification(o.isNotification());
        d.setUpdateLastMessage(o.isNeedLastMessage());
        return d;
    }
}


⸻

9.6 目标用户集生成

单聊

if (senderSync) {
    targets = List.of(recvId, senderId);
} else {
    targets = List.of(recvId);
}

群聊

来源：
	•	群成员服务查询
	•	过滤退群/被踢
	•	过滤免打扰
	•	过滤禁止接收

⸻

10. push 落地实现

10.1 delivery consumer

public void onDeliveryEvent(DeliveryEvent event) {
    List<String> onlineUsers = onlineQueryService.filterOnline(event.getTargetUserIds());

    for (String userId : onlineUsers) {
        dispatchToOnlineUser(userId, event);
    }

    if (event.getMessage().getOptions().isNeedOfflinePush()) {
        List<String> offlineUsers = diff(event.getTargetUserIds(), onlineUsers);
        for (String userId : offlineUsers) {
            produceOfflinePush(userId, event);
        }
    }
}


⸻

10.2 查询在线连接

push 读取 Redis：

Set<SessionRef> sessions = redis.get("online:user:" + userId);

然后按节点组织，再调 postoffice.dispatchMessage(...)。

⸻

10.3 在线投递调用

private void dispatchToOnlineUser(String userId, DeliveryEvent event) {
    List<String> connectionIds = onlineRegistry.getConnectionIds(userId);

    DispatchMessageReq req = new DispatchMessageReq();
    req.setUserId(userId);
    req.setConnectionIds(connectionIds);
    req.setPayload(buildPayload(event));

    DispatchMessageResp resp = onlineDispatchRpc.dispatchMessage(req);

    handleDispatchResult(userId, event, resp);
}


⸻

10.4 offlinepush producer

private void produceOfflinePush(String userId, DeliveryEvent event) {
    OfflinePushEvent offline = new OfflinePushEvent();
    offline.setUserId(userId);
    offline.setConversationId(event.getConversationId());
    offline.setSeq(event.getMessage().getSeq());
    offline.setServerMsgId(event.getMessage().getServerMsgId());
    offline.setTitle(buildPushTitle(event));
    offline.setContent(buildPushContent(event));
    offline.setExt(buildExt(event));

    kafkaTemplate.send("offlinepush", userId, serialize(offline));
}


⸻

10.5 offlinepush consumer

这个模块只做通知，不改消息主状态。

public void onOfflinePushEvent(OfflinePushEvent event) {
    vendorPushAdapter.push(event);
}


⸻

11. postoffice 落地实现

11.1 连接建立
	•	校验 token
	•	绑定 userId / deviceId / platform
	•	生成 connectionId
	•	写本地连接表
	•	写 Redis online:user:* 和 online:conn:*

⸻

11.2 心跳续租

客户端心跳：
	•	更新本地 session lastSeen
	•	刷新 Redis TTL

⸻

11.3 断连清理
	•	删除本地连接表
	•	删除 Redis online:conn:*
	•	从 online:user:{userId} 中删 connectionId

⸻

11.4 dispatchMessage 实现

public DispatchMessageResp dispatchMessage(DispatchMessageReq req) {
    List<DispatchResult> results = new ArrayList<>();

    for (String connectionId : req.getConnectionIds()) {
        Session session = localSessionManager.get(connectionId);
        if (session == null) {
            results.add(fail(connectionId, "NOT_FOUND", "connection not found"));
            continue;
        }

        boolean ok = session.write(req.getPayload());
        results.add(ok ? success(connectionId) : fail(connectionId, "WRITE_FAIL", "socket write fail"));
    }

    DispatchMessageResp resp = new DispatchMessageResp();
    resp.setResults(results);
    return resp;
}


⸻

12. 拉消息实现

12.1 pullBySeqRange

输入：
	•	userId
	•	conversationId
	•	beginSeq
	•	endSeq
	•	pageSize

处理顺序：
	1.	取 conv:minSeq/maxSeq
	2.	取 uc:min/max/read
	3.	计算该用户真实可见区间
	4.	先查 Redis 热缓存
	5.	缺失部分再查历史库
	6.	返回消息和边界信息

⸻

12.2 可见边界计算

long effectiveMin = Math.max(convMinSeq, userMinSeq > 0 ? userMinSeq : convMinSeq);
long effectiveMax = userMaxSeq > 0 ? Math.min(convMaxSeq, userMaxSeq) : convMaxSeq;


⸻

13. 默认策略建议

13.1 普通聊天消息
	•	needHistory = true
	•	needConversation = true
	•	needUnreadCount = true
	•	needOnlinePush = true
	•	needOfflinePush = true
	•	senderSync = configurable
	•	needLastMessage = true

13.2 输入中
	•	needHistory = false
	•	needConversation = false
	•	needUnreadCount = false
	•	needOnlinePush = true
	•	needOfflinePush = false
	•	needLastMessage = false

13.3 已读回执
	•	needHistory = false 或可配
	•	needConversation = true
	•	needUnreadCount = false
	•	needOnlinePush = true
	•	needOfflinePush = false
	•	needLastMessage = false

13.4 撤回通知
	•	needHistory = true
	•	needConversation = true
	•	needUnreadCount = false
	•	needOnlinePush = true
	•	needOfflinePush = false
	•	senderSync = true
	•	needLastMessage = true

⸻

14. 异常与补偿

14.1 postbox 成功写 ingress，但 postman 消费失败
	•	Kafka 重试
	•	依赖 idem:postman:* 防止重复分配 seq

14.2 postman 成功写 Redis，但 history 失败
	•	不回滚 seq
	•	history topic 重试
	•	历史库唯一键兜底

14.3 delivery 重试导致重复投递
	•	serverMsgId + 客户端去重
	•	可在 push 侧增加短期投递幂等缓存

14.4 online dispatch 成功部分、失败部分
	•	成功连接视为完成
	•	失败连接若用户无其他成功连接且允许离线推送，则转 offlinepush

14.5 离线推送失败
	•	记录日志
	•	按厂商错误码做有限重试
	•	不影响主消息状态

⸻

15. 开发顺序建议

第一阶段先打通主链路：
	1.	postoffice 连接管理
	2.	postbox.sendMessage
	3.	ingress -> postman
	4.	Redis seq + 消息缓存
	5.	delivery -> push -> postoffice.dispatch
	6.	基础单聊收发

第二阶段补历史：
	1.	history -> postbox-history
	2.	Mongo block 存储
	3.	pullBySeqRange

第三阶段补策略：
	1.	MessagePolicyEngine
	2.	输入中 / 已读 / 撤回
	3.	senderSync
	4.	offlinepush

第四阶段补鲁棒性：
	1.	幂等完整化
	2.	补偿日志
	3.	监控与告警
	4.	限流与热点会话治理

⸻

16. 研发侧最终约束

必须守住这几条：
	•	conversationId 是 ingress/history/delivery 唯一主路由键
	•	postoffice 不直接发 Kafka
	•	postman 是唯一 seq 分配者
	•	push 不拥有在线连接，只调用 postoffice 投递
	•	offlinepush 不反向修改消息主状态
	•	消息是否持久化、是否推送，统一由 MessageOptions + MessagePolicyEngine 决定

这版已经可以直接给研发拆任务了。
下一步最有用的是继续把它细化成 模块任务拆分清单 + 接口定义清单 + Redis/表结构 DDL 清单。