这部分会延续前面的架构约束：
	•	postbox 消费 history
	•	postman 是唯一 seq 分配者
	•	history topic 的 key 仍然是 conversationId
	•	历史库只负责持久化，不参与实时编排

CheeseIM 当前实现里，历史落库就是把消息按 conversationID + seq block 写 Mongo，且通过 GetDocID(conversationID, seq)、GetMsgIndex(seq) 做块定位；重复写时采用“先更新、再创建、冲突再回退更新”的方式处理幂等。

⸻

1. Mongo 历史存储总体设计

1.1 设计目标

Mongo 历史层的目标不是做实时主链路，而是做：
	•	会话消息的长期持久化
	•	按 seq 高效查询
	•	支持撤回、删除、已读投影
	•	支持按 block 批量写入
	•	支持幂等重试

所以历史层的基本思路是：

一个会话按 seq 切成固定大小 block，每个 block 存成一个 Mongo 文档。

⸻

1.2 为什么采用 block 文档模型

如果一条消息一个文档，会有几个问题：
	•	写入量太碎
	•	顺序查询要扫很多文档
	•	同一会话大量消息时索引和 IO 压力大
	•	历史批量写入效率低

block 模型的优点：
	•	一次 history event 可以批量写同一个 block
	•	查询某个 seq 时可以直接定位 block
	•	Mongo 文档天然适合“固定槽位数组”
	•	与会话 seq 模型天然契合

⸻

2. Mongo 集合设计

建议拆成两个集合，而不是一个全塞进去。

2.1 message_block 集合

存会话历史主数据。

文档结构建议

{
  "_id": "c1:u100:u200:0",
  "conversationId": "c1:u100:u200",
  "blockNo": 0,
  "startSeq": 1,
  "endSeq": 100,
  "messages": [
    null,
    {
      "seq": 2,
      "clientMsgId": "cmsg_xxx",
      "serverMsgId": "smsg_xxx",
      "senderId": "u100",
      "recvId": "u200",
      "groupId": null,
      "chatType": 1,
      "contentType": 101,
      "content": { },
      "sendTime": 1710000000000,
      "status": 1,
      "options": {
        "needHistory": true,
        "needConversation": true,
        "needUnreadCount": true,
        "needOnlinePush": true,
        "needOfflinePush": true,
        "senderSync": true,
        "notification": false,
        "needLastMessage": true
      },
      "revoke": null,
      "delList": [],
      "isRead": false,
      "ext": {}
    }
  ],
  "createdAt": ISODate("2026-03-21T00:00:00Z"),
  "updatedAt": ISODate("2026-03-21T00:00:00Z")
}


⸻

2.2 message_id_mapping 集合

存消息主键映射，用于幂等、重试、补偿、快速回查。

文档结构建议

{
  "_id": "c1:u100:u200:cmsg_xxx",
  "conversationId": "c1:u100:u200",
  "clientMsgId": "cmsg_xxx",
  "serverMsgId": "smsg_xxx",
  "seq": 12345,
  "senderId": "u100",
  "sendTime": 1710000000000,
  "createdAt": ISODate("2026-03-21T00:00:00Z")
}

这个集合非常重要，建议必须有。

用途：
	•	入口重试后通过 clientMsgId 找已有 seq
	•	撤回、补偿、问题排查
	•	避免重复入历史

⸻

3. Block 切分规则

3.1 blockSize

建议：
	•	初期：100
	•	如果历史特别大且单条消息内容偏小，可调到 200
	•	不建议太大，比如 1000，文档更新会更重

先定：

public static final int BLOCK_SIZE = 100;


⸻

3.2 blockNo 计算

public long calcBlockNo(long seq) {
    return (seq - 1) / BLOCK_SIZE;
}


⸻

3.3 block 内 index 计算

public int calcIndex(long seq) {
    return (int) ((seq - 1) % BLOCK_SIZE);
}


⸻

3.4 文档 ID 生成

public String buildDocId(String conversationId, long seq) {
    return conversationId + ":" + calcBlockNo(seq);
}

这个逻辑要在 postbox-history 模块里固化成工具类。

⸻

4. Mongo 文档模型定义

4.1 Java POJO

MessageBlockDoc

@Document("message_block")
public class MessageBlockDoc {
    @Id
    private String id;

    private String conversationId;
    private Long blockNo;
    private Long startSeq;
    private Long endSeq;

    private List<MessageSlot> messages;

    private Date createdAt;
    private Date updatedAt;
}

MessageSlot

public class MessageSlot {
    private Long seq;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer chatType;
    private Integer contentType;
    private Object content;
    private Long sendTime;
    private Integer status;
    private MessageOptions options;

    private RevokeInfo revoke;
    private List<String> delList;
    private Boolean isRead;

    private Map<String, String> ext;
}

RevokeInfo

public class RevokeInfo {
    private String revokerId;
    private Integer revokerRole;
    private String revokerNickname;
    private Long revokeTime;
}


⸻

4.2 为什么 messages 用固定长度数组

建议 messages 固定长度为 BLOCK_SIZE。

例如 BLOCK_SIZE=100，则：
	•	blockNo=0 存 seq 1~100
	•	blockNo=1 存 seq 101~200

这样：
	•	seq -> block + index 映射是 O(1)
	•	更新单条消息时容易直接 $set messages.{index}
	•	批量落库容易组装

⸻

5. history consumer 落地流程

5.1 消费主流程

postbox-history 消费到 HistoryEvent 后：
	1.	反序列化
	2.	按 blockNo 分组
	3.	对每个 block 构造更新操作
	4.	执行 Mongo upsert
	5.	写 message_id_mapping
	6.	ack

⸻

5.2 伪代码

public void onHistoryEvent(HistoryEvent event) {
    Map<Long, List<SequencedMessage>> byBlock = event.getMessages()
        .stream()
        .collect(Collectors.groupingBy(msg -> calcBlockNo(msg.getSeq())));

    for (Map.Entry<Long, List<SequencedMessage>> entry : byBlock.entrySet()) {
        long blockNo = entry.getKey();
        List<SequencedMessage> msgs = entry.getValue();

        upsertMessageBlock(event.getConversationId(), blockNo, msgs);
        upsertMessageMappings(msgs);
    }
}


⸻

5.3 block upsert 逻辑

核心原则
	•	block 不存在则创建
	•	block 存在则更新对应 index
	•	重复消费不产生重复消息
	•	同一个 seq 只覆盖同一个槽位

推荐实现方式

使用 Mongo upsert + $set

public void upsertMessageBlock(String conversationId, long blockNo, List<SequencedMessage> msgs) {
    String docId = buildDocId(conversationId, blockNo);

    Update update = new Update()
        .setOnInsert("_id", docId)
        .setOnInsert("conversationId", conversationId)
        .setOnInsert("blockNo", blockNo)
        .setOnInsert("startSeq", blockNo * BLOCK_SIZE + 1)
        .setOnInsert("endSeq", (blockNo + 1) * BLOCK_SIZE)
        .setOnInsert("createdAt", new Date())
        .set("updatedAt", new Date());

    for (SequencedMessage msg : msgs) {
        int index = calcIndex(msg.getSeq());
        update.set("messages." + index, toMessageSlot(msg));
    }

    mongoTemplate.upsert(
        Query.query(Criteria.where("_id").is(docId)),
        update,
        MessageBlockDoc.class
    );
}


⸻

6. 历史幂等设计

Mongo 历史层要做两层幂等。

6.1 第一层：block 槽位幂等

依赖：
	•	conversationId + seq 唯一映射到某个 block + index
	•	重复写只是覆盖同一槽位，不会新增第二条

这就是 block 模型最大的优势之一。

⸻

6.2 第二层：message_id_mapping 幂等

upsert 逻辑

public void upsertMessageMapping(SequencedMessage msg) {
    String id = msg.getConversationId() + ":" + msg.getClientMsgId();

    Query query = Query.query(Criteria.where("_id").is(id));
    Update update = new Update()
        .setOnInsert("_id", id)
        .setOnInsert("conversationId", msg.getConversationId())
        .setOnInsert("clientMsgId", msg.getClientMsgId())
        .setOnInsert("serverMsgId", msg.getServerMsgId())
        .setOnInsert("seq", msg.getSeq())
        .setOnInsert("senderId", msg.getSenderId())
        .setOnInsert("sendTime", msg.getSendTime())
        .setOnInsert("createdAt", new Date());

    mongoTemplate.upsert(query, update, "message_id_mapping");
}

作用
	•	重复消费不会重复插入
	•	同一个 clientMsgId 只会绑定一个 seq
	•	出问题时可以反查

⸻

7. Mongo 索引设计

7.1 message_block

必备索引

_id 自带

可选索引

db.message_block.createIndex({ conversationId: 1, blockNo: 1 }, { unique: true })

虽然 _id 已经唯一，但这个联合索引在某些按 conversationId + blockNo 查询场景下更直观。

⸻

7.2 message_id_mapping

db.message_id_mapping.createIndex(
  { conversationId: 1, clientMsgId: 1 },
  { unique: true }
)

db.message_id_mapping.createIndex(
  { serverMsgId: 1 },
  { unique: true }
)

db.message_id_mapping.createIndex(
  { conversationId: 1, seq: 1 }
)


⸻

8. 查询模型设计

Mongo 历史层的查询不要走全文扫描，而是全部基于 seq -> block -> index。

8.1 按 seq 区间拉消息

例如：
	•	conversationId = c1:u1:u2
	•	beginSeq = 101
	•	endSeq = 180

步骤
	1.	算出 block 范围
	2.	查询这些 block
	3.	在内存中过滤出 seq 区间内消息
	4.	根据 user 可见边界再过滤
	5.	返回结果

⸻

8.2 伪代码

public List<MessageSlot> queryBySeqRange(String conversationId, long beginSeq, long endSeq) {
    long startBlock = calcBlockNo(beginSeq);
    long endBlock = calcBlockNo(endSeq);

    Query query = Query.query(
        Criteria.where("conversationId").is(conversationId)
            .and("blockNo").gte(startBlock).lte(endBlock)
    );

    List<MessageBlockDoc> docs = mongoTemplate.find(query, MessageBlockDoc.class);

    List<MessageSlot> result = new ArrayList<>();
    for (MessageBlockDoc doc : docs) {
        for (MessageSlot slot : doc.getMessages()) {
            if (slot == null) {
                continue;
            }
            if (slot.getSeq() >= beginSeq && slot.getSeq() <= endSeq) {
                result.add(slot);
            }
        }
    }

    result.sort(Comparator.comparing(MessageSlot::getSeq));
    return result;
}


⸻

8.3 按 seq 列表查消息

适合：
	•	客户端补洞
	•	定点拉取
	•	引用消息回查

方案

先按 seq 分组到 block，再批量读 block，最后取对应 index。

⸻

9. Redis 与 Mongo 的边界

这里一定要定清楚。

9.1 Redis 负责什么

Redis 负责：
	•	热消息缓存
	•	conversation maxSeq/minSeq
	•	user readSeq/minSeq/maxSeq
	•	unreadCount
	•	在线状态

9.2 Mongo 负责什么

Mongo 负责：
	•	长期历史
	•	冷数据查询
	•	删除/撤回/已读投影后的历史呈现基础
	•	消息主键映射

9.3 查询优先级

建议：
	1.	先查 Redis 热缓存
	2.	Redis miss 再查 Mongo
	3.	Redis 不是 Mongo 的精确镜像，不做强一致要求
	4.	历史以 Mongo 为准

⸻

10. 撤回、删除、已读的 Mongo 落地

这块要提前设计，不然后面会重构。

10.1 撤回

建议不物理删除消息，而是在原消息槽位上写 revoke 信息。

更新示例

public void revokeMessage(String conversationId, long seq, RevokeInfo revokeInfo) {
    String docId = buildDocId(conversationId, seq);
    int index = calcIndex(seq);

    Update update = new Update()
        .set("messages." + index + ".revoke", revokeInfo)
        .set("updatedAt", new Date());

    mongoTemplate.updateFirst(
        Query.query(Criteria.where("_id").is(docId)),
        update,
        MessageBlockDoc.class
    );
}

查询时表现

读取时如果发现 revoke != null，则把消息组装成“撤回通知态”。

CheeseIM 当前也是保留消息槽位，并在读取时把 revoke 投影成撤回消息内容。 ￼

⸻

10.2 用户删除

用户删除不是物理删，而是把 userId 放进 delList。

更新示例

public void deleteForUser(String conversationId, long seq, String userId) {
    String docId = buildDocId(conversationId, seq);
    int index = calcIndex(seq);

    Update update = new Update()
        .addToSet("messages." + index + ".delList", userId)
        .set("updatedAt", new Date());

    mongoTemplate.updateFirst(
        Query.query(Criteria.where("_id").is(docId)),
        update,
        MessageBlockDoc.class
    );
}

查询时表现

如果当前 userId 在 delList 中，则返回“已删除占位消息”或直接过滤。

⸻

10.3 已读

已读主状态仍建议放 Redis / user_conversation_state。
Mongo 里的 isRead 不建议做成强实时主状态。

建议
	•	单聊已读：用户维度读到 seq 即可，不必逐条落 Mongo
	•	只有在确实需要 message 级已读投影时，才补写某些槽位字段

这样能避免 Mongo 被高频已读更新打爆。

⸻

11. 历史查询服务细化

11.1 pullBySeqRange

处理逻辑建议：
	1.	从 Redis 读 conv:min/max
	2.	从 Redis 或 DB 读 userMin/userMax/read
	3.	计算用户可见边界
	4.	先从 Redis 热缓存取
	5.	对缺失 seq 范围按 block 去 Mongo 查询
	6.	在服务层做：
	•	撤回投影
	•	用户删除投影
	•	引用消息修正
	7.	返回按 seq 排序后的结果

⸻

11.2 引用消息回查

如果消息内容是“引用消息”，建议引用体里带 quoteSeq。
查询时如果引用消息已被撤回，就把引用体投影成“引用的消息已撤回”。

CheeseIM 当前读取消息时也会对引用消息做二次修正。 ￼

⸻

12. 历史消费异常处理

12.1 block upsert 成功，mapping 失败

策略：
	•	不回滚 block
	•	直接重试 mapping upsert
	•	因为 mapping 是幂等的，可安全补写

12.2 mapping 成功，block upsert 失败

策略：
	•	Kafka 重试
	•	block 重复 upsert 仍安全
	•	不需要人工回滚 mapping

12.3 部分 block 成功、部分 block 失败

策略：
	•	当前批次失败重试
	•	依赖 block 槽位幂等
	•	消费逻辑必须允许重复处理

⸻

13. Mongo 分片建议

如果后续量大，Mongo 需要提前考虑 shard key。

13.1 推荐 shard key

优先建议：

{ conversationId: 1 }

或者：

{ conversationId: "hashed" }

取舍
	•	conversationId:1 适合会话内范围查询
	•	hashed 分布更均匀，但范围 locality 差

如果你的主要查询模式是“按 conversation 拉历史”，更建议普通 {conversationId:1}。

⸻

14. 研发实现拆分建议

14.1 postbox-history 子模块类划分

Repository
	•	MessageBlockMongoRepository
	•	MessageIdMappingRepository

Service
	•	HistoryPersistService
	•	HistoryQueryService
	•	MessageProjectionService

Util
	•	MessageBlockUtil
	•	MessageContentCodec

⸻

14.2 建议的工具类

MessageBlockUtil

public final class MessageBlockUtil {
    public static final int BLOCK_SIZE = 100;

    public static long blockNo(long seq) { ... }
    public static int index(long seq) { ... }
    public static String docId(String conversationId, long seq) { ... }
    public static long startSeq(long blockNo) { ... }
    public static long endSeq(long blockNo) { ... }
}


⸻

15. 性能建议

15.1 历史写入
	•	history 消费使用批量消费
	•	同一 conversationId 的 event 尽量合并写
	•	同一 block 的多个 seq 一次 upsert

15.2 历史读取
	•	先查 Redis，再查 Mongo
	•	一次尽量按 block 批量取，不要逐条 seq 查 Mongo
	•	查询结果在服务层排序

15.3 大会话优化

超大群会话可能成为热点：
	•	conversationId 仍然必须是主路由键
	•	但历史消费线程池要允许多个会话并行
	•	超大群查询可考虑分页严格按 seq block 分页

⸻

16. 最终落地原则

Mongo 历史层必须遵守这几条：
	1.	历史按 conversationId + seq block 存储
	2.	postman 是唯一 seq 分配者，历史层不得自行生成 seq
	3.	历史写入必须幂等，block 槽位覆盖 + mapping upsert 双保险
	4.	撤回/删除优先做逻辑投影，不做物理删除
	5.	查询按 seq -> block -> index 路径走，不做全表扫描
	6.	Redis 是热路径，Mongo 是历史基座，历史以 Mongo 为准

这版已经够研发直接开始写 postbox-history 了。