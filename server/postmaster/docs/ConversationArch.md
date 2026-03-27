这份设计文档详细描述了 IM 系统中 会话 (Conversation) 模块的架构、数据模型及其核心生命周期。会话是 IM 系统中连接“消息内容”与“用户交互界面”的关键纽带。

  ---

IM 会话 (Conversation) 模块详细设计文档

1. 模块定义与职责

1.1 核心定义
* Message (消息)：客观存在的消息内容，存储在消息库中（如 MongoDB）。
* Conversation (会话)：用户对聊天的主观视角和状态封装。一个单聊物理上只有一套消息，但在逻辑上有两个会话记录（发送者和接收者各一份）。

1.2 核心职责
1. UI 驱动：为客户端“最近联系人列表”提供数据支持。
2. 状态存储：记录未读数、置顶状态、免打扰设置、最后一条消息缩略图等。
3. 聚合索引：作为消息的索引容器，记录当前会话的最大 Seq (MaxSeq) 和已读 Seq (ReadSeq)。

  ---

2. 数据模型设计 (Schema)

会话通常存储在关系型数据库（如 MySQL）中，以便于进行复杂的列表查询。


┌──────────────────┬───────────┬───────────────────────────────────────────────────┐
│ 字段名 (Field)   │ 类型      │ 说明                                              │
├──────────────────┼───────────┼───────────────────────────────────────────────────┤
│ OwnerUserID      │ String    │ 该会话记录的所有者（索引键）                      │
│ ConversationID   │ String    │ 物理会话唯一标识 (si_A_B 或 g_GID)                │
│ ConversationType │ Int       │ 1: 单聊, 2: 群聊, 3: 通知                         │
│ UserID / GroupID │ String    │ 目标对象 ID (单聊为对方 ID，群聊为群 ID)          │
│ RecvMsgOpt       │ Int       │ 接收选项 (0: 正常, 1: 免打扰/不提示, 2: 彻底屏蔽) │
│ UnreadCount      │ Int       │ 当前用户的未读消息数                              │
│ LatestMsgSeq     │ Long      │ 该会话中最后一条消息的序号                        │
│ LatestMsg        │ Text/JSON │ 最后一条消息的摘要（用于列表展示）                │
│ IsPinned         │ Boolean   │ 是否置顶                                          │
│ DraftText        │ Text      │ 聊天输入框草稿                                    │
│ AttachedInfo     │ Text/JSON │ 扩展信息（如：是否被 @，群公告更新标记等）        │
└──────────────────┴───────────┴───────────────────────────────────────────────────┘

  ---

3. 生命周期管理 (Lifecycle)

3.1 懒加载创建 (Lazy Creation)
* 触发点: msgtransfer 模块中的 handleMsg 函数。
* 判定条件: isNewConversation == true (即该会话的 Seq 从 0 变为 1)。
* 执行逻辑:
    1. 单聊: RPC 调用同时为发送者和接收者创建（或更新）会话。
    2. 群聊: RPC 调用为群内所有成员创建会话。
* 意义: 极大减少了无效会话（从未说话的好友/群组）对数据库的压力。

3.2 动态更新 (Real-time Update)
每当新消息产生并定序后：
1. MaxSeq 更新: LatestMsgSeq 更新为当前分配的最大 Seq。
2. 摘要更新: LatestMsg 更新为新消息的文本缩略图。
3. 未读数递增:
    * 对于接收者：如果 RecvMsgOpt 为正常，则 UnreadCount ++。
    * 对于发送者：UnreadCount 保持 0。

3.3 已读同步 (Read Receipt Sync)
1. 用户点击进入聊天窗口。
2. 客户端上报 MarkAsRead 请求。
3. 服务端将 UnreadCount 清零，并将 ReadSeq 更新为当前 MaxSeq。

  ---

4. 核心逻辑流转 (Mermaid)

    1 graph TD
    2     subgraph "消息处理 (MsgTransfer)"
    3         A[Message Arrived] --> B{Seq == 1?}
    4         B -- Yes --> C[RPC: CreateConversation]
    5         B -- No --> D[Update Conversation State]
    6     end
    7
    8     subgraph "会话服务 (Conversation Service)"
    9         C --> C1[Insert MySQL Record for Owner]
10         C1 --> C2[Set Initial UnreadCount=1]
11         D --> D1[Incr UnreadCount]
12         D1 --> D2[Update LatestMsg & Time]
13     end
14
15     subgraph "客户端表现 (Client UI)"
16         C2 --> E[Add to Recent List]
17         D2 --> F[Move to Top & Show Red Dot]
18     end

  ---

5. 关键技术细节分析

5.1 写扩散问题 (Write Diffusion)
* 挑战: 在一个 2000 人的大群中发一条消息，理论上需要更新 2000 个人的会话记录。
* 优化策略:
    * 异步化: msgtransfer 不直接写数据库，而是通过 MQ 或异步 RPC 通知会话服务。
    * 聚合写: 针对同一个群的多次更新，在极短时间内合并为一次数据库操作。
    * 热点群优化: 对极活跃的大群，采用“拉模式”或“混合模式”，不实时更新每个人的未读数，而是当用户上线时再计算。

5.2 状态一致性保证
* 问题: 消息先存入 MongoDB，会话更新失败怎么办？
* 解决: 客户端在同步消息时，如果发现消息的 Seq 大于会话中记录的 LatestMsgSeq，会自动触发本地会话补齐逻辑，确保 UI 最终一致。

5.3 消息摘要的安全性
* 加密处理: 如果开启了端到端加密，LatestMsg 字段在服务端应存储为加密串，仅由客户端在本地解密展示，保护隐私。

  ---

6. 总结 (AI 分析要点)
* 耦合度: 会话模块与消息定序模块强耦合，定序结果（Seq）直接决定了会话的生命周期起点。
* 性能瓶颈: IM 系统中最密集的数据库写操作通常不在消息表，而在会话表的“未读数”与“最后消息时间”更新。
* 设计精髓: 懒加载机制是处理海量用户关系时，保持系统轻量化的核心手段。