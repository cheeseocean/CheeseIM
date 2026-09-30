# Review 修复任务与验收标准（2026-09-30）

> 需求来源：[整体 review](code-review-2026-09-30.md)。本文件是本次修复工作的任务/验收账本；架构事实仍维护在 ASSESSMENT 与各模块 ARCH。
> 任务稳定关联 R01–R25；虚实现和模块治理另列 V/M 编号，避免遗漏 review 表外的问题。

## 1. 完成定义

- **待处理**：尚未开始；**进行中**：正在实现或验证；**代码验收通过**：实现、相关回归、编译/边界检查通过，已记录证据。
- 环境验收独立登记：mock/内存测试不替代真实 broker、Redis Cluster、Mongo replica/sharding、厂商或长压结果。
- 每项代码验收必须有失败场景与成功场景；状态/并发问题必须控制故障位置或交错顺序，不能只断言 mock 调用或配置文件存在。
- 修改持久化身份、Redis key、cursor、TTL、公共模型时，须记录兼容/migration/回滚；公共协议字段变化另按 AGENTS 评估与生成。
- 不能用一项局部成功关闭另一条旁路；不能因接口返回成功就认为 DB、事件与恢复状态均成功。
- 本轮不自动提交或创建远程 issue，任务在此账本持续推进。

### 必须保持的设计不变量

- seq只走ConversationSeqAllocator；允许空洞、不允许重复/回退，重构不能另建INCRBY路径。
- 发送/ingress稳定身份、payload冲突检测、seq binding和owner/generation fencing不能被普通SETNX替代。
- broker受理、ChannelFuture写成功、设备送达和已读仍是不同状态；去重仅在正确终态提交。
- TCP/WS仍为唯一proto源的typed二进制协议；HTTP DTO不下沉，domain/port不依赖Mongo/BSON，构造器注入与四类架构门禁持续通过。
- 普通群worker写扩散、超级群读扩散以及成员epoch分页保留；路由仍按真实gatewayNode投递，旧connectionId/generation不能清理新连接。
- 可信assertion、refresh family重放检测及ticket原子消费保留；不打印token/密钥；cluster不回退本地状态。
- executor、节点队列、writer和重试保持有界；业务IO不新增到Netty EventLoop；指标不引入用户/会话高基数。

## 2. 修复队列与可执行验收条件

### A. 授权、装配与生命周期（当前批次）

| 任务 | 优先级/状态 | 代码验收：给定 → 操作 → 必须结果 | 环境验收 |
| --- | --- | --- | --- |
| R01 真实会话访问权限 | P0 / 代码验收通过 | 单聊参与者/第三者、个人通知本人/他人、群/群通知当前成员/非成员、空/非法 ID → 权限检查 → 仅合法归属允许；群仓储失败拒绝。仓内只有一个真实 provider，位于 business；历史、撤回增量、typing 等 consumer 使用同一契约，RPC失败不能复用旧 allow | 待验收：独立 postbox/postmaster 调用 business 的越权与成员退出场景 |
| R02 视图铸造与 sync 旁路 | P0 / 代码验收通过 | 用户提交他人会话、伪群、混合授权批次 → settings → 任何写入前拒绝，不能创建视图/偏好/日志；合法已有会话仅更新可选设置。预置历史伪视图或陈旧退出群视图 → IDs/detail/batch/maxSeq/readSnapshot/sync pull → 不返回内容、不调用历史/全局水位；合法参与者仍正常同步 | 待验收：HTTP 设置→sync/pull完整攻击链；代码测试已证明旧伪视图无需先清空 |
| R03 独立 API consumer 装配 | P1 / 代码验收通过 | 仅生产 ApiServerApplication + 无 provider 的 context → 启动 → 所有构造器契约存在唯一 Dubbo代理，不加载 provider；含写契约 retries=0，查询继承全局策略；Controller/Facade正常依赖同一代理 | 待验收：Nacos发现、远程RPC以及all-in-one injvm实际调用 |
| R04 所有命令 session 复核 | P1 / 代码验收通过 | 认证连接只发chat/read/revoke/typing/delivery、不发heartbeat → 租约到期 → 复核 invalid/RPC失败时不续租、不执行业务；租约内复用，跨命令并发到期单飞；heartbeat回归 | 待验收：kickoff丢失/Redis短断；租约内保留最长一个复核间隔窗口 |
| R19 认证晚返回不得复活 | P1 / 代码验收通过 | 阻塞AUTH RPC→断线移除→RPC成功返回 → 不写身份/索引/路由、不claim lease、不踢有效连接、不二次释放计数；旧对象与同ID新对象交错同样拒绝。合法认证和同身份重复AUTH仍幂等 | 待验收：多节点重连风暴与global lease fencing；本地锁不是分布式事务 |

### B. 消息身份、批次与历史

| 任务 | 优先级/状态 | 代码验收 | 环境验收/兼容约束 |
| --- | --- | --- | --- |
| R05 Kafka事务与可见性 | P0 / 代码验收通过（首批） | 无调用方事务的单条发送成功提交；ACK前不返回；commit失败不返回成功；批次任一失败中止。业务与DLT consumer强制read_committed | 真实broker检查abort不可见、提交故障与DLT；保留既有topic/key |
| R06 canonical批次分组 | P1 / 代码验收通过 | PRIVATE A→B + NOTIFICATION A→B、NOTIFICATION A→B + B→A、GROUP + 群通知混批 → 各条seq/历史/投递归属正确；调换首条顺序结果不变；重放复用seq；部分失败释放未完成组的租约 | 待验收：Kafka/Chronicle实际批次；保留queue key，旧错误归属数据另行审计 |
| R07 mapping身份一致 | P1 / 待处理 | 同会话不同sender同clientMsgId→两条历史及mapping都可查询；同sender重放不重复；partial bulk failure重试后block/mapping收敛 | Mongo副本/分片检验唯一性；存量ID迁移、冲突检测与回滚 |
| R08 未读语义 | P1 / 待处理 | seq空洞、双向混批、群多sender、重复消费、乱序read ACK→只按真实未读收件消息计数、不计自身消息、不重复、不负数；read水位仍单调 | 明确精确计数及Redis/Mongo恢复方案，不用seq差近似冒充精确值 |
| R11 持久化/重放门槛 | P1 / 待处理 | history持续失败、mapping成功block失败、HISTORY成功DELIVERY失败、inbox过期后旧ingress重放→身份和seq稳定，故障可恢复，最终历史可查询；明确broker受理与可见持久化语义 | Kafka+Redis+Mongo故障窗口和DLT redrive；保留期与最长重放窗口兼容 |
| R12 有界gap repair查询 | P1/P2 / 待处理 | 极大seq区间、小limit、稀疏空洞、空页→Mongo读量受界限控制；continuation/Completed真实反映已覆盖区间，分页不漏消息 | explain/读取量验证；查询port与SDK continuation兼容 |

### C. 推送、状态、事务和缓存

| 任务 | 优先级/状态 | 代码验收 | 环境验收/兼容约束 |
| --- | --- | --- | --- |
| R09 vendor attempt可恢复 | P1 / 待处理 | 厂商retryable失败、executor拒绝/超时、claim后崩溃→租约恢复后可重试；成功不重复；最终失败明确进入终态/DLT；重新上线不误推 | 厂商sandbox与Redis故障；模糊发送结果不以无条件删去重处理 |
| R10 推送canonical身份 | P1 / 待处理 | s:/g:/n:/ng:离线事件→event/request/编排/provider payload→身份与seq原样保留，群ID正确，无single_/group_null | 厂商payload与客户端点击补拉；旧payload兼容 |
| R13 安全缓存单调 | P1 / 待处理 | 旧回源阻塞→ban/tokenVersion写入→旧回源完成，或写者逆序回填→不能覆盖新安全版本；故障不能延长旧授权 | Redis重启/主从切换与Mongo恢复；版本化专用Store，不能仅删缓存 |
| R14 设备所有凭证撤销 | P1 / 待处理 | 同设备两次登录→kickoff device→所有旧access/session/refresh family拒绝；并发login/revoke有明确先后边界；其他设备不受影响 | Redis原子边界、generation/key migration及滚动升级 |
| R15 同步版本与窗口 | P1 / 待处理 | 同owner并发append、晚提交、部分日志TTL过期、客户端版本超前/idHash不符→不漏变化；超可用窗口强制全量；版本不重复/回退 | replica transaction/并发验收；owner锚点迁移与保留epoch |
| R16 统一事务开关 | P1/P2 / 待处理 | 单机关闭事务→所有业务路径不建立Mongo事务；cluster开启→多集合写任一失败全部回滚；不存在绕过开关的注解路径 | standalone与replica分别启动/写入；配置语义一致 |
| R17 提交后缓存补偿 | P1/P2 / 待处理 | DB提交→第一个cache失效失败→其他区域仍失效，失败任务可重试且有指标；不假装回滚已提交业务，不重做非幂等写 | Redis短断后恢复、补偿backlog上界与重启恢复 |
| R18 已读事件补齐 | P1 / 待处理 | hot read推进→version-log/outbox失败→相同ACK重试→水位不变仍补齐缺失事件，稳定eventId不重复；在线通知失败可通过outbox/cursor恢复 | Mongo/Redis故障、客户端多端增量补齐 |

### D. SDK、应用和网关压力边界

| 任务 | 优先级/状态 | 代码验收 | 环境验收/兼容约束 |
| --- | --- | --- | --- |
| R20 可靠事件不静默丢失 | P1 / 待处理 | 两级事件队列灌满→message/ACK/revoke/disconnect/forceLogout→可靠交付或显式溢出并触发可证明收敛；末尾消息无后续seq也恢复；Close不死锁 | 慢消费与突发；typing可合并；SDK API兼容 |
| R21 连接代际隔离 | P1 / 待处理 | 新连接安装→旧readLoop/heartbeat延迟退出→新done/conn不被关闭、旧disconnect不污染新会话；并发Connect/Close race通过 | 重连风暴、慢旧连接与TCP断连 |
| R22 同步覆盖区间 | P1 / 待处理 | 空store+bootstrap max100→实时101→打开会话→历史仍catch-up；多页/部分repair/合法空洞→覆盖游标正确，未完成不提前推进 | catch-up下沉SDK，存储adapter注入；UI不阻塞Update做HTTP |
| R23 控制快照恢复 | P1 / 待处理 | 控制事件先到→cursor保存→重启→消息后到→delivery/read/revoke效果恢复；快照写失败cursor不提前提交；本地历史真实导入 | 磁盘故障及旧格式migration、回滚/降级 |
| R24 TCP头部限界 | P1 / 代码验收通过（首批） | 仅非法magic/version/超大uint32头→即时拒绝、不读body；正常、截断、分片及认证/readLoop入口回归 | TCP服务端兼容联调；无wire变更 |
| R25 生命周期与响应背压 | P1/P2 / 待处理 | Redis阻塞→channelInactive→EventLoop仍可处理其他连接；旧远端清理不删新路由；unwritable→命令响应不无限堆积；拒绝/关闭策略一致 | 慢读、断线风暴、Redis故障长压；记录线程阻塞/heap/backlog |

### E. 虚实现、旧代码与模块治理

| 任务 | 状态 | 验收条件 |
| --- | --- | --- |
| V01 群列表明确失败 | 代码验收通过（首批） | 首步失败及已累计一项后失败均非2xx；正常/真实空列表仍200，成功response shape不变 |
| V02 用户设置PUT | 待处理 | 有效选项写领域/DB后GET可读，多副本缓存一致，非法选项拒绝；实现前明确未支持，不再返回204假成功 |
| V03 在线通知空钩子 | 待处理 | 真发送并有接收方，或删空钩子/准确日志；不能仅凭日志宣称通知已发 |
| V04 推送统计双轨 | 待处理 | 真实发送成功/失败落既有Micrometer；断开的累计“实时”统计和定时读取退役；标签低基数 |
| V05 options支持矩阵 | 待处理 | 逐字段列实际执行方/语义/兼容；客户端不可误认为保留字段生效；公共字段不无评估删除 |
| V06 无引用清理 | 部分验收 | ChannelPolicy和私有转换已清理；其余FriendRealtimeNotifier/ConnectionManager/Puller/EventKind候选逐一证实调用边界再处理，公共API保留兼容或给迁移 |
| M01 业务内部职责收敛 | 待处理 | conversation settings/access/sync/control明确所有权，不新增跨服务调用；service不暴露持久化模型 |
| M02 adapter与图级门禁 | 待处理 | feature通过port访问驱动；迁移例外逐项归属；Gradle依赖图/源码门禁捕获反向依赖，all-in-one/standalone均可装配 |
| M03 包名机械迁移 | 待处理 | package所有权与模块一致；集合/ID/key/协议/事务不变，独立机械diff和全编译 |
| M04 SDK与UI唯一状态源 | 待处理 | SDK拥有通用同步、应用拥有UI；消息只由明确存储入口写入和恢复，重启/多页/实时交错通过 |
| M05 长压/chaos | 待处理（环境） | 根据perf阈值提交原始配置、规模、时长、唯一接收/重复/ACK/lag/恢复数据；容量只按实测结论，不从脚本推导 |

## 3. 执行顺序与依赖

1. 当前批次：R01→R02；R03、R04/R19可按独立模块并行。
2. 消息正确性：R06→R07/R11；R08需与R18及客户端水位语义一起验收。
3. 安全状态与同步：R13/R14、R15/R16/R17、R18。
4. 推送与客户端恢复：R09/R10，R21→R20，R12→R22→R23/M04。
5. 模块/遗留收敛与真实环境：V/M任务及R25；已通过代码任务继续保留环境验收状态。

## 4. 验收证据与文件登记

首批代码证据承接review第7节：R05真实KafkaTemplate+MockProducer/commit失败/consumer配置回归，R24 reader+net.Pipe，V01真实HTTP异常advice。
本次通过代码验收的证据：

| 任务 | 回归证据 | 范围 |
| --- | --- | --- |
| R01 | `ConversationPermissionServiceImplTest`（16项）、`HistoryQueryAuthorizationTest`（2项） | 真实领域规则 + mock成员事实；provider曾allow后故障不再读历史 |
| R02 | `ConversationAuthorizationTest`（11项）、`ConversationControllerTest.deniedSettingsShouldReturnForbiddenWithStableCodeInsteadOfSuccess` | 真实ConversationService与Sync组合，预置伪视图/缓存、全量/增量读取、矛盾type/target归一化、混合owner批次预检；HTTP403稳定码 |
| R03 | `ApiServerConsumerContextTest` | 生产Application、真实Spring/Dubbo MVC consumer-only context，13个唯一代理，8个含写契约retries=0，5个查询继承retries=2/timeout5000 |
| R04/R19 | `CommandSessionValidationTest`（17项）、`ConnectionBindServiceTest`（11项） | 真实guard/manager，确定性单飞及AUTH/断线交错；远端route/lease为mock |
| R06 | `IngressCanonicalBatchTest`（20项） | 真实Protobuf/JSON及内存inbox状态机；三类混批、语义交错、绑定/发布/完成失败与重放 |

统一验证已通过（`server`）：

```bash
./gradlew compileJava :business:test :postbox:test :api-server:test :postoffice:test :postmaster:test
```

四项架构边界门禁通过；postmaster模块47项测试已实际执行通过，统一验证时结果为UP-TO-DATE；无新增真实中间件依赖。

## 5. 本批兼容与发布边界

- R01/R02 不改proto、RPC方法签名、Mongo集合/ID或Redis key；设置接口有意收紧为仅更新已有视图。
- 新增稳定 `ErrorCode.CONVERSATION_ACCESS_DENIED(1201)`，HTTP映射403；各Java进程需使用一致common-api版本。
- 旧postbox权限provider必须退出注册；确认ConversationPermissionService仅由business提供，再验收远程调用。混部残留恒allow实例不能视为授权修复已上线。
- 存量伪视图和缓存不必先清空：每次读取实时过滤；矛盾type/target按canonical ID还原到返回副本，不修改旧持久化数据。当前群规则是当前成员访问，不新增入群历史窗口产品语义。
- R03的Primary consumer在all-in-one中选择统一引用；真实injvm调用仍须联调，不以consumer-only context替代。
- R04保留默认60秒租约和主动kickoff；R19本地fencing不能回滚已经完成的全局lease替换决策。
- R06保留topic/key/wire及唯一seq allocator。新消息/同版本重放已验收；升级前已错误归属的history、fanout job及旧assignedSeq须另行审计，不可盲目改会话后复用旧seq。这类数据修复/迁移属于R07/R11环境验收。

## 6. 新增文件登记

本次新增源码/测试文件（由docs/INDEX入口登记本账本统一追踪）：

- `server/api-server/src/main/java/com/cheeseocean/im/apiserver/config/ApiServerConsumerConfiguration.java`
- `server/api-server/src/test/java/com/cheeseocean/im/apiserver/config/ApiServerConsumerContextTest.java`
- `server/postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/CommandSessionValidationTest.java`
- `server/business/src/main/java/com/cheeseocean/im/business/service/permission/ConversationPermissionServiceImpl.java`（替换并移除postbox默认放行provider）
- `server/business/src/test/java/com/cheeseocean/im/business/service/permission/ConversationPermissionServiceImplTest.java`
- `server/business/src/test/java/com/cheeseocean/im/business/service/conversation/ConversationAuthorizationTest.java`
- `server/postbox/src/test/java/com/cheeseocean/im/postbox/service/HistoryQueryAuthorizationTest.java`
- `server/postmaster/src/test/java/com/cheeseocean/im/postmaster/listener/IngressCanonicalBatchTest.java`

## 7. 提交前远端整合记录

push前发现远端新增 `78555eb`、`644549b`，保留并合并，未覆盖远端提交。
R07/R08/R12/R14/R15/R25已有部分实现合入；表中的待处理代表尚需按完整标准复核/迁移，而非没有实现。

- 保留sender维度mapping ID、真实收件数unreadDelta、原子版本cursor/保留窗口检查、同设备全部session扫描、Mongo block窗口下推、有界连接清理池与出站writable守卫。
- 配置写的两份授权逻辑合并为统一领域规则及写前全量预检，保留仅更新已有视图与稳定403/1201；保留sync独立读前校验，并对齐规范s:与g:/ng:。
- R06回归适配int unreadDelta；双消息部分发布/重放仍传接收者2、发送者0。新增sync的g:/ng:当前成员与退出成员回归。
- KafkaTemplate保留非事务兼容开关；QueueAdapter仍显式事务并强制read_committed。
- 未读read ACK/冷恢复仍以seq差重算；mapping存量迁移、稀疏分页continuation、版本晚提交、设备并发撤销及清理池过载仍按原任务标准继续验收。

整合后已通过Java全模块编译与以下模块测试：business、postbox、api-server、postoffice、postmaster、authcenter、infra-queue、infra-state、storage-business、storage-history。新增sync兼容回归另跑business测试。
