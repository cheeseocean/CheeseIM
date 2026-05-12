正式可落地的设计文档初稿。
本版固定以下前提：
	•	postoffice：Gateway + online session manager
	•	postbox：消息入口 RPC + history consumer + 消息查询 RPC
	•	postman：消息编排中心
	•	push：delivery consumer + offlinepush producer/consumer
	•	在线投递方式：方案 B
	•	push 调用 postoffice 的投递接口，把消息投递到具体在线连接
	•	topic 映射：
	•	ingress
	•	history
	•	delivery
	•	offlinepush

options 驱动流转的核心思想：消息并不是固定“存历史 + 推送”，而是依据策略决定是否入历史、是否计未读、是否在线投递、是否离线推送、是否发送方同步等。

⸻

Cheese IM 架构设计文档

1. 背景

系统采用 Java + Dubbo + Kafka + Redis + 历史存储构建 IM 消息系统。
整体设计参考 OpenIM 的消息流转模型，但结合当前系统命名与职责进行了重新抽象：
	•	postoffice 对应接入层与在线会话管理
	•	postbox 对应消息入口与历史存取中心
	•	postman 对应消息编排中心
	•	push 对应消息送达与离线通知中心

目标不是简单复刻原实现，而是保留其核心设计优点：
	•	会话维度顺序控制
	•	Redis 热路径 + 历史异步落库
	•	消息策略驱动流转
	•	在线 / 离线送达分流
	•	支持幂等与扩展

CheeseIM 当前实现中 postman 消费入口 topic 后，会按消息 options 分类处理，再写缓存、更新 seq 与会话状态，并 fanout 到 Mongo 和 Push；Push 再根据消息选项决定是否执行 offline push。

⸻

2. 设计目标

本系统设计目标如下：
	•	保证同一 conversationId 内消息顺序稳定
	•	将接入层、消息入口、编排层、推送层、历史层职责分离
	•	支持消息按策略流转，而非固定路径
	•	支持在线投递与离线推送分流
	•	支持入口幂等、编排幂等、历史幂等、客户端去重
	•	允许后续扩展更多消息类型与路由策略

⸻

3. 系统模块划分

3.1 postoffice

postoffice 是接入层和在线会话管理层。

职责：
	•	HTTP / WebSocket / TCP 接入
	•	用户鉴权与连接绑定
	•	连接生命周期管理
	•	在线状态维护
	•	维护用户连接路由
	•	调用 postbox 的发送消息 Dubbo RPC
	•	对外提供在线消息投递接口，供 push 调用

不负责：
	•	不直接发 Kafka
	•	不分配 seq
	•	不写历史库
	•	不决定消息是否持久化

⸻

3.2 postbox

postbox 是消息入口与历史存取中心。

职责：
	•	接收 postoffice 的 Dubbo 发消息请求
	•	计算 conversationId
	•	生成消息主键
	•	做入口幂等预处理
	•	将消息写入 ingress
	•	消费 history
	•	持久化历史消息
	•	提供拉消息 / 查消息 / 查会话状态等 RPC

⸻

3.3 postman

postman 是消息编排中心，是整个系统的核心。

职责：
	•	消费 ingress
	•	按 conversationId 聚合消息
	•	依据消息策略决定流转路径
	•	分配会话内 seq
	•	写 Redis 热消息缓存
	•	更新会话状态
	•	更新已读与边界状态
	•	初始化新会话
	•	将消息 fanout 到 history / delivery

⸻

3.4 push

push 是送达编排中心。

职责：
	•	消费 delivery
	•	查询接收用户在线状态
	•	调用 postoffice 的投递接口做在线消息投递
	•	对投递失败或离线用户生成 offlinepush
	•	消费 offlinepush
	•	调用 APNs / FCM / 厂商推送

⸻

4. Topic 规划

Topic	Producer	Consumer	key	用途
ingress	postbox	postman	conversationId	消息入站主总线
history	postman	postbox-history-consumer	conversationId	历史持久化
delivery	postman	push-delivery-consumer	conversationId	在线投递编排
offlinepush	push-delivery-consumer	push-offlinepush-consumer	userId	离线推送任务队列

设计原则：
	•	ingress/history/delivery 都使用 conversationId，保证会话级顺序与处理一致性
	•	offlinepush 使用 userId，因为它的目标是用户设备通知，不再强调会话内严格顺序

入口消息在 postman 中继续沿用 key fanout 到 History 和 delivery但内部处理又默认整批消息应共享 conversation 语义，因此在新设计里直接把 conversationId 固定为主 key 更稳妥。

⸻

5. 总体架构图

flowchart LR
    A[Client]
    B[postoffice]
    C[postbox]
    K1[Kafka ingress]
    D[postman]
    R[Redis]
    K2[Kafka history]
    K3[Kafka delivery]
    E[postbox-history-consumer]
    M[History DB]
    F[push-delivery-consumer]
    K4[Kafka offlinepush]
    G[push-offlinepush-consumer]
    P[APNs/FCM/厂商通道]

    A --> B
    B -->|Dubbo RPC SendMessage| C
    C --> K1
    K1 --> D
    D --> R
    D --> K2
    D --> K3
    K2 --> E
    E --> M
    K3 --> F
    F -->|RPC Dispatch| B
    F --> K4
    K4 --> G
    G --> P


⸻

6. 在线会话管理设计

6.1 归属

在线会话管理归属 postoffice。

原因：
	•	postoffice 持有真实连接
	•	postoffice 最清楚用户在哪个节点、哪个连接、哪个设备在线
	•	在线状态属于“连接域”，不是“推送域”

push 侧本质上也是读取在线缓存后再决定 online push / offline push，而不是自己维护连接。 ￼

⸻

6.2 管理内容

本机内存状态

connectionId -> SessionContext
userId -> Set<connectionId>

Redis 分布式状态

online:user:{userId} -> [nodeId, connectionId, deviceId, platform, lastSeen]
online:conn:{connectionId} -> SessionMetadata

会话元数据建议

class SessionContext {
    String connectionId;
    String userId;
    String deviceId;
    Integer platform;
    String nodeId;
    long connectTime;
    long lastHeartbeatTime;
}


⸻

6.3 push 调用 postoffice 的在线投递接口

在线投递方式固定为 方案：

push 调用 postoffice 的投递接口，把消息投递到指定在线连接。

建议接口：

DispatchMessageReq {
    String userId;
    List<String> connectionIds;
    DispatchPayload payload;
}

DispatchMessageResp {
    List<DispatchResult> results;
}

DispatchResult {
    String connectionId;
    boolean success;
    String code;
    String message;
}

这样 push 不需要了解具体长连接实现，只需要：
	1.	查询在线连接
	2.	调 postoffice.dispatch(...)
	3.	收集成功/失败结果
	4.	决定是否转 offlinepush

⸻

7. 会话模型设计

7.1 conversationId 规范

单聊

c1:{minUserId}:{maxUserId}

群聊

c2:{groupId}

通知会话

c3:{userId}

原则：
	•	conversationId 必须稳定
	•	单聊不能因发送方向不同而变化
	•	可直接用作 Kafka key、Redis key、历史分区键

⸻

7.2 会话状态

ConversationState

class ConversationState {
    String conversationId;
    Integer chatType;
    Long maxSeq;
    Long minSeq;
    MessageSummary lastMessage;
}

UserConversationState

class UserConversationState {
    String userId;
    String conversationId;
    Long readSeq;
    Long userMinSeq;
    Long userMaxSeq;
    Integer unreadCount;
}


⸻

7.3 字段语义
	•	maxSeq：会话当前最大 seq
	•	minSeq：会话当前最小可见 seq
	•	readSeq：用户已读到的 seq
	•	userMinSeq：该用户在该会话允许读取的下界
	•	userMaxSeq：该用户在该会话允许读取的上界
	•	unreadCount：未读数

这种“会话状态 + 用户会话边界”的设计与 OpenIM 当前思路一致，后者在读取消息时会同时参考 conversation 的 min/max seq 与 user 维度的 min/max/read seq。 ￼

⸻

8. 消息模型设计

8.1 入口消息模型

class IngressEvent {
    String requestId;
    String conversationId;
    String clientMsgId;
    String serverMsgId;
    String senderId;
    String recvId;
    String groupId;
    Integer chatType;
    Integer contentType;
    byte[] content;
    Long sendTime;
    MessageOptions options;
}


⸻

8.2 编排后消息模型

class SequencedMessage {
    String conversationId;
    String clientMsgId;
    String serverMsgId;
    String senderId;
    String recvId;
    String groupId;
    Integer chatType;
    Integer contentType;
    byte[] content;
    Long sendTime;
    Long seq;
    MessageOptions options;
}


⸻

8.3 history 事件模型

class HistoryEvent {
    String conversationId;
    Long beginSeq;
    Long endSeq;
    List<SequencedMessage> messages;
}


⸻

8.4 delivery 事件模型

class DeliveryEvent {
    String conversationId;
    SequencedMessage message;
    List<String> targetUserIds;
    DeliveryOptions deliveryOptions;
}


⸻

8.5 offlinepush 事件模型

class OfflinePushEvent {
    String userId;
    String conversationId;
    String serverMsgId;
    Long seq;
    String title;
    String content;
    Map<String, String> ext;
}


⸻

9. 消息策略模型

这是整个系统必须显式定义的一层。
消息并不总是统一走“入历史 + 在线投递 + 离线推送”，而是由策略决定。

CheeseIM 在 postman 中会根据消息 options 分类出：
	•	存历史消息
	•	不存历史消息
	•	存历史通知
	•	不存历史通知

Push 侧还会根据消息选项判断是否允许离线推送。

⸻

9.1 MessageOptions

class MessageOptions {
    boolean needHistory;
    boolean needConversation;
    boolean needUnreadCount;
    boolean needOnlinePush;
    boolean needOfflinePush;
    boolean senderSync;
    boolean isNotification;
    boolean needLastMessage;
}


⸻

9.2 字段定义

needHistory

是否进入 history

典型场景：
	•	普通聊天消息：true
	•	输入中、录音中等临时态消息：false

needConversation

是否更新会话状态

needUnreadCount

是否增加未读数

needOnlinePush

是否进入 delivery

needOfflinePush

在线投递失败或接收方离线时，是否进入 offlinepush

senderSync

是否向发送方其他终端同步

OpenIM 单聊推送里会根据 sender sync 选项决定是否把发送方加入推送目标。 ￼

isNotification

是否作为通知消息处理

needLastMessage

是否更新会话 lastMessage

⸻

9.3 推荐规则表

消息类型	needHistory	needConversation	needUnreadCount	needOnlinePush	needOfflinePush	senderSync	isNotification	needLastMessage
普通单聊消息	Y	Y	Y	Y	Y	可配	N	Y
普通群聊消息	Y	Y	Y	Y	Y	N	N	Y
已读回执	可选，默认 N	Y	N	Y	N	N	N	N
输入中	N	N	N	Y	N	N	N	N
撤回通知	Y	Y	N	Y	N	Y	Y	Y
系统通知（展示型）	按产品定义，通常 Y	Y	按产品定义	按产品定义	N	N	Y	Y
系统通知（静默控制型）	N	N	N	N	N	N	Y	N
入群退群通知	Y	Y	通常 N	Y	可选	N	Y	Y

补充约束：
	•	isNotification=Y 的消息，表示按“通知消息”路径处理，可与普通聊天消息在 UI 展示和会话聚合上区分。
	•	needLastMessage 用来控制是否刷新会话卡片预览。输入中、已读回执这类瞬时/状态类消息应为 N；普通消息、撤回通知、展示型系统通知、入群退群通知应为 Y。
	•	系统通知不能只写成笼统“可选”，至少要拆成“展示型”和“静默控制型”两类，否则 postman、push、客户端会对是否建会话、是否计未读、是否刷新 lastMessage 产生歧义。
	•	已读回执的 needHistory 保持“默认 N、审计场景可开”；这与详细设计里的默认策略建议保持一致。


⸻

10. 消息发送时序

sequenceDiagram
    participant Client
    participant Office as postoffice
    participant Box as postbox
    participant K1 as ingress
    participant Man as postman
    participant Redis
    participant K2 as history
    participant K3 as delivery
    participant Push as push
    participant K4 as offlinepush

    Client->>Office: SendMessage
    Office->>Box: Dubbo RPC SendMessage
    Box->>Box: compute conversationId + idem check
    Box->>K1: produce ingress(key=conversationId)

    K1->>Man: consume
    Man->>Man: group by conversationId
    Man->>Man: apply MessagePolicyEngine
    Man->>Redis: allocate seq
    Man->>Redis: write message cache
    Man->>Redis: update conversation state/read state

    alt needHistory
        Man->>K2: produce history
    end

    alt needOnlinePush
        Man->>K3: produce delivery
    end

    K3->>Push: consume
    Push->>Office: dispatch online message
    alt offline or dispatch failed and needOfflinePush
        Push->>K4: produce offlinepush
    end


⸻

11. postbox 设计

11.1 消息入口

postbox 是唯一 ingress producer。

处理步骤：
	1.	接收 postoffice 的 SendMessageReq
	2.	计算 conversationId
	3.	生成 serverMsgId
	4.	做入口幂等检查
	5.	构造 IngressEvent
	6.	发送到 ingress

返回值应表示“已受理”，不表示“已完成落库”。

⸻

11.2 history consumer

消费 history：
	1.	反序列化 HistoryEvent
	2.	按 conversationId + seq block 组织数据
	3.	批量写历史库
	4.	写消息 ID 映射
	5.	ack

建议建立映射表：

message_id_mapping
- conversation_id
- client_msg_id
- seq
- server_msg_id
UNIQUE(conversation_id, client_msg_id)


⸻

11.3 查询 RPC

postbox 提供以下 RPC：
	•	pullBySeqRange
	•	pullBySeqList
	•	getConversationMaxSeq
	•	getConversationState
	•	getUserConversationState
	•	searchMessages
	•	getLastMessage

⸻

12. postman 设计

12.1 定位

postman 是消息事实顺序与流转决策的唯一生成器。

真正让消息具备以下属性的地方都在 postman：
	•	会话内 seq
	•	热缓存可读
	•	会话状态可更新
	•	是否进历史
	•	是否进 delivery

⸻

12.2 核心处理流程
	1.	消费 ingress
	2.	按 Kafka batch 收取消息
	3.	批内按 conversationId 二次分组
	4.	做编排幂等
	5.	读取消息策略
	6.	分配 seq
	7.	写 Redis 消息缓存
	8.	更新会话状态
	9.	更新用户会话状态
	10.	必要时初始化会话
	11.	发送 history
	12.	发送 delivery

⸻

12.3 MessagePolicyEngine

建议在 postman 内部显式引入策略引擎。

interface MessagePolicyEngine {
    MessageRouteDecision decide(IngressEvent event);
}

class MessageRouteDecision {
    boolean persistHistory;
    boolean updateConversation;
    boolean updateUnread;
    boolean sendDelivery;
    boolean sendOfflineIfFail;
    boolean senderSync;
    boolean notification;
    boolean updateLastMessage;
}

这样能把策略逻辑集中，而不是散落在多个 consumer 和 service 里。

⸻

12.4 Seq 分配原则

建议原则：

只要消息进入会话流转并可能被客户端感知顺序，就分配 seq。

即使某些消息不入历史，也可以分配 seq，但不进入 history。

这能保证：
	•	客户端顺序稳定
	•	会话 maxSeq 连续
	•	多端对齐更容易

OpenIM 当前实现中 seq 就是在缓存/编排阶段分配，而不是入口分配。 ￼

⸻

13. push 设计

13.1 delivery consumer

职责：
	•	消费 delivery
	•	计算目标用户集
	•	查询在线状态
	•	调用 postoffice.dispatch(...)
	•	根据结果决定是否转 offlinepush

⸻

13.2 在线投递逻辑

单聊

默认目标为接收方；若 senderSync=true，则把发送方其他终端也加入目标集。

这与 OpenIM 当前单聊推送逻辑一致。 ￼

群聊

目标为群成员集合，之后过滤：
	•	已退群 / 已被踢成员
	•	免打扰成员
	•	不需要离线推送成员

⸻

13.3 offlinepush producer

当满足以下条件时转入 offlinepush：
	•	needOfflinePush=true
	•	用户离线
	•	或在线投递失败

⸻

13.4 offlinepush consumer

职责：
	•	消费 offlinepush
	•	调用 APNs / FCM / 厂商通道
	•	记录推送结果日志
	•	重试失败任务

⸻

14. Redis 状态设计

建议至少维护以下键：

会话状态

conv:maxSeq:{conversationId}
conv:minSeq:{conversationId}
conv:lastMsg:{conversationId}

用户会话状态

uc:read:{userId}:{conversationId}
uc:min:{userId}:{conversationId}
uc:max:{userId}:{conversationId}
uc:unread:{userId}:{conversationId}

消息缓存

msg:{conversationId}:{seq}

幂等

idem:ingress:{conversationId}:{clientMsgId}
idem:postman:{conversationId}:{clientMsgId}

在线状态

online:user:{userId}
online:conn:{connectionId}


⸻

15. 幂等设计

15.1 分层幂等

层	幂等键	目标
postbox 入口	conversationId + clientMsgId	防重复入 ingress
postman	conversationId + clientMsgId	防重复分配 seq
postbox-history	conversationId + seq	防重复写历史
历史映射	conversationId + clientMsgId	重试可找回 seq
push	serverMsgId	防重复投递
客户端	clientMsgId/serverMsgId	防重复展示


⸻

15.2 推荐实现

postbox

SETNX idem:ingress:{conversationId}:{clientMsgId}

postman

SETNX idem:postman:{conversationId}:{clientMsgId}

历史库

UNIQUE(conversation_id, seq)
UNIQUE(conversation_id, client_msg_id)


⸻

16. 历史存储设计

若历史库使用 Mongo，建议采用会话 + seq block 模型：

docId = conversationId + ":" + blockNo

每个文档包含固定数量的消息槽位，例如 100 或 200 条。

这种方式与 OpenIM 当前的按 conversationID + seq block 存储方式一致，优点是：
	•	批量落库效率高
	•	按 seq 查找可定位
	•	会话内连续消息容易组织。

⸻

17. 异常处理与补偿

17.1 ingress 成功但 postman 消费失败

Kafka 重试，依赖 postman 幂等避免重复分配 seq。

17.2 history 消费失败

Kafka 重试，依赖历史唯一键避免重复写。

17.3 delivery 消费失败

Kafka 重试，依赖 serverMsgId 与客户端去重控制重复投递影响。

17.4 在线投递失败

若允许离线推送，则生成 offlinepush。

17.5 历史成功但 delivery 失败

不回滚历史，delivery 通过重试或离线推送补偿。

⸻

18. 扩展性设计

未来扩展方向：
	•	增加更多消息类型，只需扩展 MessagePolicyEngine
	•	增加更多推送通道，只需扩展 offlinepush adapter
	•	增加消息审计、风控、内容审核，可在 postbox -> ingress 前增加 hook
	•	增加消息回执统计，可在 push 与 postoffice 间扩展投递反馈链路

⸻

19. 总结

本系统采用：
	•	postoffice 负责接入与在线会话管理
	•	postbox 负责消息入口与历史存取
	•	postman 负责消息编排与顺序生成
	•	push 负责在线投递与离线推送

消息流转主链路为：

postoffice -> postbox -> ingress -> postman -> history/delivery -> postbox-history/push -> offlinepush

核心原则有三条：
	1.	全链路以 conversationId 作为主路由键
	2.	在线连接状态归属 postoffice，push 通过 RPC 调用 postoffice 执行在线投递
	3.	消息流转由统一 MessageOptions / MessagePolicyEngine 决定，而不是固定路径
