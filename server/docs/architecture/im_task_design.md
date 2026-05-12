下面给你一版可直接用于排期和拆任务的清单，按你当前确认的架构来写：
	•	postoffice：接入层 + online session manager
	•	postbox：消息入口 RPC + ingress producer + history consumer + 查询 RPC
	•	postman：消息编排
	•	push：delivery / offlinepush
	•	历史：Mongo block 模型
	•	在线投递：push -> postoffice.dispatch(...)
	•	消息策略：MessageOptions + MessagePolicyEngine

⸻

1. 模块任务拆分清单

1.1 postoffice 任务清单

A. 接入层
	1.	WebSocket 连接建立
	2.	Token 鉴权
	3.	用户与连接绑定
	4.	心跳续租
	5.	断连清理
	6.	多端登录策略
	7.	单机连接表管理

B. 在线状态管理
	1.	Redis 在线状态写入
	2.	Redis 在线状态删除
	3.	userId -> connections 查询
	4.	connectionId -> sessionMeta 查询
	5.	节点维度在线连接统计

C. 对 postbox 的发送调用
	1.	Dubbo client 封装
	2.	发送消息 controller / ws command handler
	3.	参数校验
	4.	异常码映射

D. 给 push 的在线投递接口
	1.	dispatchMessage Dubbo provider
	2.	按 connectionId 批量投递
	3.	投递结果返回
	4.	节点不在本机时的处理策略
	5.	写失败码标准化

E. 监控与日志
	1.	在线人数
	2.	连接数
	3.	连接建立/断开 QPS
	4.	dispatch 成功率
	5.	平均投递耗时

⸻

1.2 postbox 任务清单

A. 消息入口 RPC
	1.	MessageSendRpc.sendMessage
	2.	conversationId 生成器
	3.	serverMsgId 生成器
	4.	默认 MessageOptions 补齐
	5.	入口幂等
	6.	IngressEvent 构建
	7.	Kafka ingress producer

B. history consumer
	1.	Kafka history consumer
	2.	HistoryEvent 反序列化
	3.	block 分组
	4.	Mongo message_block upsert
	5.	Mongo message_id_mapping upsert
	6.	消费失败重试处理
	7.	死信策略

C. 历史查询 RPC
	1.	pullBySeqRange
	2.	pullBySeqList
	3.	getConversationState
	4.	getUserConversationState
	5.	searchMessages，后续可选
	6.	Redis 热缓存优先 + Mongo 补洞

D. 历史投影处理
	1.	撤回消息投影
	2.	用户删除投影
	3.	引用消息修正
	4.	deleted / revoked / normal 三种统一返回态

E. 管理与排障
	1.	根据 clientMsgId 查消息
	2.	根据 serverMsgId 查消息
	3.	根据 conversationId + seq 查消息
	4.	历史补写工具

⸻

1.3 postman 任务清单

A. ingress consumer
	1.	Kafka 批消费
	2.	批内按 conversationId 二次分组
	3.	消费 offset 提交策略
	4.	批量异常隔离

B. 消息编排
	1.	MessagePolicyEngine
	2.	编排幂等过滤
	3.	seq 分配器
	4.	SequencedMessage 构建
	5.	DeliveryEvent 构建
	6.	HistoryEvent 构建

C. Redis 热状态写入
	1.	消息缓存写入
	2.	conv:maxSeq
	3.	conv:minSeq
	4.	conv:lastMsg
	5.	uc:read
	6.	uc:min
	7.	uc:max
	8.	uc:unread

D. 会话初始化
	1.	首条消息建会话判断
	2.	单聊会话初始化
	3.	群聊会话初始化
	4.	通知会话初始化

E. Fanout
	1.	发送 history
	2.	发送 delivery
	3.	history 与 delivery 的发送顺序约束
	4.	失败补偿

F. 监控
	1.	seq 分配耗时
	2.	redis 写入耗时
	3.	history fanout 成功率
	4.	delivery fanout 成功率
	5.	编排幂等命中率

⸻

1.4 push 任务清单

A. delivery consumer
	1.	Kafka delivery consumer
	2.	目标用户集解析
	3.	senderSync 处理
	4.	群成员投递过滤
	5.	在线用户筛选

B. 在线投递编排
	1.	读取在线连接
	2.	按 userId 聚合 connectionIds
	3.	调 postoffice.dispatchMessage
	4.	解析 dispatch 结果
	5.	失败连接统计

C. offlinepush producer
	1.	判定是否需要离线推送
	2.	构建 OfflinePushEvent
	3.	发送 offlinepush
	4.	离线推送幂等键

D. offlinepush consumer
	1.	Kafka offlinepush consumer
	2.	调厂商推送 adapter
	3.	推送结果记录
	4.	限次重试

E. 厂商适配
	1.	APNs adapter
	2.	FCM adapter
	3.	国内厂商 adapter，后续可扩

F. 监控
	1.	在线投递成功率
	2.	离线推送成功率
	3.	dispatch RPC 耗时
	4.	每用户平均连接数
	5.	厂商错误码分布

⸻

2. 里程碑拆分建议

M1：主链路打通
	•	postoffice 建链 + 在线状态
	•	postbox 发送 RPC + ingress
	•	postman 消费 + seq + Redis
	•	push 在线投递
	•	单聊文本消息通

M2：历史补齐
	•	history consumer
	•	Mongo block 存储
	•	pullBySeqRange
	•	pullBySeqList

M3：消息策略补齐
	•	MessagePolicyEngine
	•	senderSync
	•	needHistory / needOnlinePush / needOfflinePush
	•	输入中 / 已读 / 撤回

M4：鲁棒性
	•	幂等完整化
	•	异常补偿
	•	死信与回放
	•	监控告警
	•	限流与热点治理

⸻

3. 接口定义清单

下面按“研发真正要建的接口”列。

3.1 postoffice 对外接入接口

WebSocket 发送消息命令

{
  "cmd": "sendMessage",
  "data": {
    "requestId": "req_xxx",
    "clientMsgId": "cmsg_xxx",
    "chatType": 1,
    "recvId": "u200",
    "groupId": null,
    "contentType": 101,
    "content": {},
    "sendTime": 1710000000000,
    "options": {
      "needHistory": true,
      "needConversation": true,
      "needUnreadCount": true,
      "needOnlinePush": true,
      "needOfflinePush": true,
      "senderSync": true,
      "notification": false,
      "needLastMessage": true
    }
  }
}

WebSocket 回执

{
  "cmd": "sendMessageAck",
  "data": {
    "accepted": true,
    "requestId": "req_xxx",
    "clientMsgId": "cmsg_xxx",
    "serverMsgId": "smsg_xxx",
    "conversationId": "c1:u100:u200"
  }
}


⸻

3.2 postoffice -> postbox

Dubbo：MessageSendRpc

public interface MessageSendRpc {
    SendMessageResp sendMessage(SendMessageReq req);
}

SendMessageReq

public class SendMessageReq {
    private String requestId;
    private String senderId;
    private Integer chatType;
    private String recvId;
    private String groupId;
    private String clientMsgId;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;
}

SendMessageResp

public class SendMessageResp {
    private boolean accepted;
    private String requestId;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
    private String code;
    private String message;
}


⸻

3.3 push -> postoffice

Dubbo：OnlineDispatchRpc

public interface OnlineDispatchRpc {
    DispatchMessageResp dispatchMessage(DispatchMessageReq req);
}

DispatchMessageReq

public class DispatchMessageReq {
    private String userId;
    private List<String> connectionIds;
    private DispatchPayload payload;
}

DispatchPayload

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

DispatchMessageResp

public class DispatchMessageResp {
    private List<DispatchResult> results;
}

DispatchResult

public class DispatchResult {
    private String connectionId;
    private boolean success;
    private String code;
    private String message;
}


⸻

3.4 postbox 查询接口

Dubbo：MessageQueryRpc

public interface MessageQueryRpc {
    PullBySeqRangeResp pullBySeqRange(PullBySeqRangeReq req);
    PullBySeqListResp pullBySeqList(PullBySeqListReq req);
    ConversationStateResp getConversationState(ConversationStateReq req);
    UserConversationStateResp getUserConversationState(UserConversationStateReq req);
}

PullBySeqRangeReq

public class PullBySeqRangeReq {
    private String userId;
    private String conversationId;
    private Long beginSeq;
    private Long endSeq;
    private Integer pageSize;
}

PullBySeqRangeResp

public class PullBySeqRangeResp {
    private String conversationId;
    private Long effectiveMinSeq;
    private Long effectiveMaxSeq;
    private Long readSeq;
    private List<MessageDTO> messages;
}

PullBySeqListReq

public class PullBySeqListReq {
    private String userId;
    private String conversationId;
    private List<Long> seqList;
}

PullBySeqListResp

public class PullBySeqListResp {
    private String conversationId;
    private List<MessageDTO> messages;
}

ConversationStateReq

public class ConversationStateReq {
    private String conversationId;
}

ConversationStateResp

public class ConversationStateResp {
    private String conversationId;
    private Integer chatType;
    private Long maxSeq;
    private Long minSeq;
    private MessageSummaryDTO lastMessage;
}

UserConversationStateReq

public class UserConversationStateReq {
    private String userId;
    private String conversationId;
}

UserConversationStateResp

public class UserConversationStateResp {
    private String userId;
    private String conversationId;
    private Long readSeq;
    private Long userMinSeq;
    private Long userMaxSeq;
    private Integer unreadCount;
}


⸻

3.5 Kafka 事件清单

IngressEvent

public class IngressEvent {
    private String requestId;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer chatType;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;
}

HistoryEvent

public class HistoryEvent {
    private String conversationId;
    private Long beginSeq;
    private Long endSeq;
    private List<SequencedMessage> messages;
}

DeliveryEvent

public class DeliveryEvent {
    private String conversationId;
    private SequencedMessage message;
    private List<String> targetUserIds;
}

OfflinePushEvent

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

4. Redis DDL 清单

Redis 没有传统 DDL，这里给你“键模型定义清单”。

4.1 在线状态

online:user:{userId}

类型：SET
成员内容建议直接存 JSON 或 nodeId|connectionId|deviceId|platform|lastSeen

示例：

online:user:u100

值：

postoffice-01|conn_1|iphone_1|1|1710000000000
postoffice-02|conn_9|ipad_1|1|1710000001000

online:conn:{connectionId}

类型：STRING

示例 key：

online:conn:conn_1

示例 value：

{
  "userId":"u100",
  "nodeId":"postoffice-01",
  "deviceId":"iphone_1",
  "platform":1,
  "lastSeen":1710000000000
}


⸻

4.2 会话状态

conv:maxSeq:{conversationId}

类型：STRING

conv:minSeq:{conversationId}

类型：STRING

conv:lastMsg:{conversationId}

类型：STRING(JSON)

示例：

{
  "seq": 123,
  "serverMsgId": "smsg_xxx",
  "senderId": "u100",
  "contentType": 101,
  "sendTime": 1710000000000
}


⸻

4.3 用户会话状态

uc:read:{userId}:{conversationId}

类型：STRING

uc:min:{userId}:{conversationId}

类型：STRING

uc:max:{userId}:{conversationId}

类型：STRING

uc:unread:{userId}:{conversationId}

类型：STRING

⸻

4.4 热消息缓存

简化版

msg:{conversationId}:{seq}
类型：STRING(JSON)

示例：

msg:c1:u100:u200:123

值：

{
  "conversationId":"c1:u100:u200",
  "seq":123,
  "clientMsgId":"cmsg_xxx",
  "serverMsgId":"smsg_xxx",
  "senderId":"u100",
  "recvId":"u200",
  "chatType":1,
  "contentType":101,
  "content":{},
  "sendTime":1710000000000,
  "options":{}
}

TTL 建议：
	•	最近消息可以不设 TTL
	•	若做热缓存，可设 3~7 天

⸻

4.5 幂等键

idem:ingress:{conversationId}:{clientMsgId}

类型：STRING
value：serverMsgId
TTL：2d

idem:postman:{conversationId}:{clientMsgId}

类型：STRING
value：serverMsgId
TTL：2d

idem:delivery:{serverMsgId}:{userId}:{connectionId}

类型：STRING
value：1
TTL：1d

⸻

5. Mongo DDL 清单

Mongo 用 createCollection + createIndex。

5.1 message_block

collection

db.createCollection("message_block")

索引

db.message_block.createIndex(
  { conversationId: 1, blockNo: 1 },
  { unique: true, name: "uk_conv_block" }
)

db.message_block.createIndex(
  { conversationId: 1 },
  { name: "idx_conv" }
)

文档结构约束
	•	_id = conversationId:blockNo
	•	messages 长度固定 BLOCK_SIZE
	•	messages[i] 可为 null

⸻

5.2 message_id_mapping

collection

db.createCollection("message_id_mapping")

索引

db.message_id_mapping.createIndex(
  { conversationId: 1, clientMsgId: 1 },
  { unique: true, name: "uk_conv_client" }
)

db.message_id_mapping.createIndex(
  { serverMsgId: 1 },
  { unique: true, name: "uk_server_msg" }
)

db.message_id_mapping.createIndex(
  { conversationId: 1, seq: 1 },
  { name: "idx_conv_seq" }
)


⸻

6. MySQL DDL 清单

虽然历史是 Mongo，但会话状态、用户会话状态若要落 MySQL，建议如下。

6.1 conversation_state

CREATE TABLE conversation_state (
    conversation_id VARCHAR(128) NOT NULL PRIMARY KEY,
    session_type INT NOT NULL,
    max_seq BIGINT NOT NULL DEFAULT 0,
    min_seq BIGINT NOT NULL DEFAULT 1,
    last_msg_summary JSON NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

6.2 user_conversation_state

CREATE TABLE user_conversation_state (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    read_seq BIGINT NOT NULL DEFAULT 0,
    user_min_seq BIGINT NOT NULL DEFAULT 0,
    user_max_seq BIGINT NOT NULL DEFAULT 0,
    unread_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_conv (user_id, conversation_id),
    KEY idx_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

如果这些状态你决定只放 Redis，也可以先不建这两张表。
但为了重建、排障、离线修复，通常建议最终还是有持久化。

⸻

7. 模块交付物清单

7.1 postoffice 交付物
	•	WebSocket server
	•	OnlineSessionManager
	•	OnlineRegistryRepository
	•	OnlineDispatchRpcImpl
	•	发送 controller / ws handler
	•	connection cleanup job
	•	metrics & logs

7.2 postbox 交付物
	•	MessageSendRpcImpl
	•	ConversationIdFactory
	•	ServerMsgIdGenerator
	•	IngressProducer
	•	HistoryConsumer
	•	MessageBlockMongoRepository
	•	MessageIdMappingRepository
	•	MessageQueryRpcImpl

7.3 postman 交付物
	•	IngressBatchConsumer
	•	ConversationBatchDispatcher
	•	MessagePolicyEngine
	•	SeqAllocator
	•	ConversationStateWriter
	•	RedisMessageCacheWriter
	•	HistoryProducer
	•	DeliveryProducer

7.4 push 交付物
	•	DeliveryConsumer
	•	OnlineTargetResolver
	•	OnlineDispatchClient
	•	OfflinePushProducer
	•	OfflinePushConsumer
	•	VendorPushAdapter

⸻

8. 任务拆分到人天建议

给你一版粗颗粒估算，方便排期。

postoffice
	•	接入与连接管理：5~8 天
	•	Redis 在线状态：2~3 天
	•	dispatch RPC：2~3 天
	•	监控日志：1~2 天

postbox
	•	sendMessage RPC + ingress producer：3~5 天
	•	history consumer：4~6 天
	•	Mongo repository：3~5 天
	•	query RPC：5~8 天

postman
	•	ingress consumer + 分组：3~4 天
	•	seq allocator + Redis 状态：4~6 天
	•	policy engine + fanout：3~5 天

push
	•	delivery consumer：3~4 天
	•	dispatch 编排：3~5 天
	•	offlinepush producer/consumer：3~5 天
	•	厂商适配：按厂商数量增加

⸻

9. 研发验收清单

单聊主链路
	•	发送消息后 postbox 返回 accepted
	•	postman 正确分配 seq
	•	Redis conv:maxSeq 增长
	•	在线用户收到消息
	•	history 入 Mongo
	•	message_id_mapping 可查

重试幂等
	•	相同 clientMsgId 重发，不出现两个 seq
	•	history 重复消费不出现两条历史
	•	delivery 重复消费客户端不重复展示

离线链路
	•	用户不在线时生成 offlinepush
	•	推送成功日志可查

查询链路
	•	pullBySeqRange 正确返回
	•	Redis miss 可从 Mongo 补洞
	•	撤回 / 删除投影正确
