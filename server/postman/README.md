# Postman

`postman` 是 CheeseIM 的投递与离线推送模块。它消费 `postmaster` 产生的投递事件，优先尝试在线投递；无法在线送达或需要系统通知时，按配置生成离线推送。

## 职责

- 消费 delivery event 与 offline push event。
- 调用 `postoffice` 在线投递消息。
- 根据用户、会话和消息选项判断是否需要离线推送。
- 封装厂商推送 provider，统一离线推送调用。
- 暴露 `MessagePushService` 供其他模块触发推送能力。

## 非职责

- 不负责 TCP/WS 连接管理。
- 不分配消息 seq。
- 不写历史消息。
- 不维护好友、群组、会话等业务数据。

## 关键类

| 类 | 说明 |
| --- | --- |
| `Postman` | 独立模块启动入口。 |
| `DeliveryEventListener` | 在线投递事件消费入口。 |
| `OfflinePushEventListener` | 离线推送事件消费入口。 |
| `MessagePushServiceImpl` | 推送服务实现。 |
| `PushDecisionService` | 离线推送决策。 |
| `OfflinePushService` | 离线推送执行编排。 |
| `VendorPushProvider` | 厂商推送 provider 接口。 |

## 与其他模块的关系

- 上游是 `postmaster` 产生的 delivery/offline push event。
- 在线投递依赖 `postoffice`。
- 推送内容来自消息体、离线推送配置和业务通知规则。
