# bootstrap-all/ARCH.md — all-in-one 单 JVM 联调入口

> 开发与本地联调推荐入口。单 JVM 装配所有模块，Dubbo 走 injvm，队走 Chronicle。
> 详细端口/配置矩阵见 `server/AGENTS.md` §9。

## 1. 装配事实

- Spring Boot 应用，启动类在同包
- 装配：当前 server 为 16 个子模块，bootstrap-all 通过构建依赖装配业务服务及基础设施；ops-cli 是独立运维入口
- Dubbo：injvm 协议，`register:false` / `subscribe:false`，不连 Nacos
- 队列：`cheeseim.queue.type=chronicle`，本地 `data/queue`
- 缓存：`cache.data-dir=data/cache`
- 状态：`state.data-dir=data/state`
- seq 段大小 100
- profile：`all-in-one`

## 2. 暴露端口

| 服务 | 端口 |
| --- | --- |
| HTTP API | 18079 |
| WebSocket | 5147，path `/ws` |
| TCP | 5148 |

## 3. 边界

- **all-in-one 只用于本地联调**，不可生产部署。生产用分模块 profile（见 `server/AGENTS.md` §9）。
- Chronicle 队列是单机文件，不可跨 JVM 共享；多节点必须切 `cheeseim.queue.type=kafka`。
- injvm Dubbo 不能模拟跨节点；任何"跨节点路由 / 踢下线"相关 bug 在 all-in-one 下**复现不了**，需要分模块启动或集群 chaos 测试。

## 4. 启动

```bash
cd server
./gradlew :bootstrap-all:bootRun
```

前置中间件：MongoDB / Redis；在线路由和节点投递依赖 Redis，JWT 密钥必须由
`CHEESEIM_AUTH_JWT_SECRET` 提供。会话服务仍有直接 `@Transactional` 路径，
自定义事务执行器的关闭开关不能覆盖这些方法；standalone Mongo 兼容性尚待修复和启动验证。

## 5. 改动评估 checklist

- [ ] 加新模块装配需同步本文件 + `server/AGENTS.md` §1.2 依赖矩阵
- [ ] 改启动端口需同步 `application-all.yml` + 根 `README.md` 端口表
- [ ] all-in-one 一次只选择一种 `QueueAdapter`；Chronicle 用于单机默认路径，Kafka 需同时启用对应配置与 topic 校验
