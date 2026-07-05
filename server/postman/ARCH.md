# postman/ARCH.md — 投递与离线推送事实快照

> 在线投递执行 + 离线厂商推送（APNs/FCM/Huawei/Xiaomi/JPush）。
> 跨节点在线投递当前不可靠，见 `server/docs/architecture/ASSESSMENT.md` P0-1。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `DeliveryEventListener` | `listener/DeliveryEventListener.java:40` | 消费 `TopicNames.DELIVERY`，做在线投递 + 触发离线推送 |
| `OfflinePushEventListener` | `listener/OfflinePushEventListener.java:24` | 消费 `TopicNames.OFFLINE_PUSH`，调用厂商推送 |
| `OnlineDispatcherImpl` | **在 postoffice**（`postoffice/api/OnlineDispatcherImpl.java:67`） | Dubbo 投递执行 |
| `OfflinePushServiceImpl` | `service/impl/OfflinePushServiceImpl.java:58` | 多厂商推送 fan-out 编排 |
| `MessagePushServiceImpl` | `service/impl/MessagePushServiceImpl.java:31` | 推送尝试记录 + 投递状态记录 |
| `PushDecisionService` | `service/PushDecisionService.java:13` | 推送决策（依赖 `DeliveryState` 与 attempt 记录） |
| `provider/*` | `provider/` | 5 个厂商真实实现 |

## 2. 在线投递链路（**当前不可靠**）

`DeliveryEventListener.deliverToUser`（line 69）：
1. `onlineRouteQueryService.findByUser(userId)` 查 Redis 路由
2. `onlineDispatcher.dispatchMessage(req)` 通过 Dubbo 调用
3. `OnlineDispatcherImpl.dispatchMessage` 仅取本地 `connectionManager.getUserConnections`（`OnlineDispatcherImpl.java:67`）

❌ Dubbo 默认随机 LB，命中非持有连接的节点会得到空列表 → `hasSuccessfulDispatch=false` → 误判离线走 push。

**修复见 ASSESSMENT P0-1**：`RouteSnapshot.gatewayNode` 写真实节点 id + postman 按 node 选 Dubbo 服务组或 per-node topic 直投。

## 3. 群投递（**硬跳过**）

```java
// DeliveryEventListener.java:59-61
if (message.getChatType() == ChatType.GROUP) {
    log.warn("Skipping group delivery because target fanout data is not attached...");
    return List.of();
}
```

群消息在 postmaster 持久化但**永不投递**。`GroupFanoutPlanner` 在 postmaster 已实现但无人调用。修复见 ASSESSMENT P0-2。

## 4. 投递去重

`ConnectionManager.markDeliveryIfAbsent`（postoffice 模块，`ConnectionManager.java:322`）用 `deliveredMessageKeys = ConcurrentHashMap.newKeySet()`，**无界本地**。

`MessagePushServiceImpl.attempts` / `deliveryStates`（line 31-32）也是 `ConcurrentHashMap` 本地存储，**多副本会重复推送**。ASSESSMENT P4-24 修复项 → 迁 Redis。

## 5. 离线推送 5 厂商

每个 provider 真实 SDK 调用，`enabled:false` 默认，`@ConditionalOnProperty` 装配：

| Provider | 文件 | SDK |
| --- | --- | --- |
| APNs | `provider/APNsPushProvider.java:36` | pushy，同步 `sendNotification().get()` |
| FCM | `provider/FCMPushProvider.java` | Firebase Admin |
| Huawei | `provider/HuaweiPushProvider.java` | HuaweiPush SDK |
| Xiaomi | `provider/XiaomiPushProvider.java` | MiPush SDK |
| JPush | `provider/JPushPushProvider.java:104` | `jpushClient.sendPush(payload)` |

`OfflinePushServiceImpl.selectPushProvider`（line 284）按优先级 O(n) 选择可用 provider。

## 6. 离线推送 fan-out

- `OfflinePushServiceImpl.java:58` 用 `CompletableFuture.runAsync` 并发 fan-out
- ⚠️ 使用 **common ForkJoinPool**（无显式 executor），1M 级 fan-out 会饿死其它 CompletableFuture 用户
- 日推送计数（line 162）是**非原子 read-modify-write**，多副本会出错

## 7. Kafka 序列化绕过 QueueAdapter

`DeliveryEventListener.emitOfflinePushIfNeeded`（line 102）直接 `kafkaTemplate.send` 发 OfflinePushEvent，**绕过 `QueueAdapter` 抽象**。如果切回 Chronicle 队列模式，这条路径会失效。ASSESSMENT P0-6 修复项。

## 8. 配置

`module-postman.yml`：
- 5 厂商开关全部 `enabled:false`
- `max-retry=3`、`max-daily-push-count=100`
- 定时任务清理 `scheduled-tasks interval=6h`
- Kafka `push-group`
- actuator + Prometheus

## 9. 改动评估 checklist

- [ ] 改在线 dispatch 必须同步 postoffice `OnlineDispatcherImpl`
- [ ] 加新推送厂商继承 `PushProvider` 接口，加 `enabled` 开关
- [ ] 改 `OfflinePushEvent` 结构需同步 `IngressEventListener` 发布侧
- [ ] 加跨节点去重 key 必须上 Redis，禁止本地 Set/Map