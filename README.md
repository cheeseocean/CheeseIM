# CheeseIM

中文/[英文](./README.en.md)

CheeseIM 是一个即时通讯系统仓库，当前以 Java 17 的多模块后端为核心，同时包含客户端 Demo、Web/Flutter 客户端和 TCP SDK。

当前仓库重点在于一条经过重构的 IM 消息链路，主要服务边界如下：

- `postoffice`：接入层、长连接管理、在线路由
- `postman`：消息投递编排、幂等、补偿、状态收敛
- `postbox`：消息持久化、历史查询、会话视图
- `push`：在线投递执行、离线推送决策与适配
- `authcenter`：轻量登录与认证引导

如果你是第一次进入仓库，建议先把它理解成一个 monorepo：

- 根目录用于聚合服务端、客户端、SDK 与文档
- 后端主要从 `server/` 目录构建和运行
- 客户端和 SDK 在各自子目录独立开发

## 仓库结构

```text
CheeseIM/
├── server/                  # Java 17 Gradle 多模块后端
│   ├── authcenter/          # 认证服务
│   ├── bootstrap-all/       # all-in-one 启动聚合模块
│   ├── common-api/          # 跨模块 API/契约
│   ├── common-core/         # 共享基础设施与通用能力
│   ├── config/              # 多服务配置文件
│   ├── postoffice/          # 网关 / TCP / WebSocket 接入
│   ├── postman/             # 投递编排与状态机
│   ├── postbox/             # 消息存储与查询
│   └── push/                # 在线投递执行与离线推送
├── apps/
│   ├── im_flutter_client/   # Flutter 客户端
│   ├── im_java_client_demo/ # Java TCP 客户端 Demo
│   └── im_web_client/       # React + Vite Web 客户端
├── sdks/
│   └── im_tcp_sdk/          # Dart TCP SDK
├── distro/docker/           # 本地中间件编排
└── docs/                    # 运行手册、设计文档、计划文档
```

## 核心服务职责

### `postoffice`

`postoffice` 是接入层与在线路由层，负责：

- 处理 TCP / WebSocket 长连接
- 认证、心跳、断连与会话绑定
- 维护用户-设备-网关节点的在线路由
- 规范化客户端消息与回执后转发给后续服务

它不负责最终消息真相、离线存储或完整投递编排。

### `postman`

`postman` 是消息链路中的编排核心，负责：

- 消息幂等校验
- 会话序列分配
- 投递状态推进
- 回执、已读、撤回等状态收敛
- 补偿任务与死信处理

它不直接承担查询侧存储模型，也不负责网关接入。

### `postbox`

`postbox` 是存储边界，负责：

- 持久化消息历史
- 维护消息块和消息 ID 映射
- 提供历史拉取、会话视图与部分查询能力
- 维护与会话状态相关的 Redis 热数据

### `push`

`push` 是投递执行与离线推送边界，负责：

- 消费投递事件并执行在线投递
- 判断是否需要厂商离线推送
- 对推送尝试去重
- 在回执收敛后取消过期推送
- 对接 APNs / FCM / 极光等推送适配器

### `authcenter`

`authcenter` 提供轻量认证入口，当前主要用于本地联调和 Demo 登录流程。

## 技术栈

### 后端

- Java 17
- Gradle 多模块构建
- Spring Boot 3.2.x
- Apache Dubbo 3.x
- Netty
- Kafka
- Redis
- MongoDB

### 客户端与 SDK

- React + Vite Web 客户端
- Flutter 客户端
- Dart TCP SDK
- Java TCP CLI Demo

## 基础依赖

按当前配置，后端默认依赖以下中间件：

- Nacos：服务注册与配置中心
- Kafka：异步事件流与投递链路
- Redis：在线路由、热状态、幂等/缓存数据
- MongoDB：消息历史与持久化查询数据

仓库内已提供本地中间件编排文件：

```bash
docker-compose -f distro/docker/docker-compose.middleware.yml up -d
```

该编排当前包含：

- `nacos`
- `kafka`
- `zookeeper`
- `kafka-console`

说明：

- Redis 和 MongoDB 需要你自行准备，默认地址分别是 `localhost:6379` 与 `localhost:27017`
- 后端配置文件位于 `server/config/src/main/resources/`

## 运行方式

### 1. 启动中间件

在仓库根目录执行：

```bash
docker-compose -f distro/docker/docker-compose.middleware.yml up -d
```

### 2. 进入服务端目录

后端 Gradle Wrapper 位于 `server/`：

```bash
cd server
```

### 3. 构建服务端

```bash
./gradlew build
```

### 4. 启动核心服务

本地联调通常需要分别启动以下模块：

```bash
./gradlew :authcenter:bootRun
./gradlew :postbox:bootRun
./gradlew :postman:bootRun
./gradlew :push:bootRun
./gradlew :postoffice:bootRun
```

### 5. 启动 all-in-one 模式

如果你希望以单进程方式本地运行全部后端模块，可以使用 `bootstrap-all`：

```bash
./gradlew :bootstrap-all:bootRun
```

当前 `application-all.yml` 显示：

- all-in-one HTTP 端口为 `18079`
- Dubbo 使用 `injvm`，不注册到外部注册中心

## 常用端口

按当前配置文件，默认端口如下：

- `postoffice` HTTP：`18080`
- `postoffice` WebSocket：`5147`
- `postoffice` TCP：`5148`
- `postman` HTTP：`18081`
- `postbox` HTTP：`18082`
- `push` HTTP：`18083`
- `authcenter` HTTP：`18084`
- `bootstrap-all` HTTP：`18079`
- Nacos：`8848`
- Kafka：`9092`

## 客户端与 Demo

### Java TCP Client Demo

`apps/im_java_client_demo` 是当前后端联调最直接的客户端 Demo，用来验证：

- 登录
- TCP 连接与认证
- 单聊文本消息发送
- 入站消息接收

运行方式：

```bash
cd server
./gradlew :apps:im_java_client_demo:cli-demo:run --args="--host 127.0.0.1 --tcp-port 5148 --base-url http://127.0.0.1:18084"
```

说明：

- 上面的命令使用当前仓库 `authcenter` 的默认 HTTP 端口 `18084`
- `apps/im_java_client_demo/README.md` 中仍保留了旧示例 `8080`
- 使用前请根据你的本地启动方式确认 `base-url`

### Web / Flutter / Dart SDK

仓库中还包含：

- `apps/im_web_client`
- `apps/im_flutter_client`
- `sdks/im_tcp_sdk`

它们的开发与测试入口可参考 [docs/client-runbook.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/client-runbook.md)。

## 测试与验证

### 后端单模块测试

在 `server/` 目录执行：

```bash
./gradlew :postoffice:test
./gradlew :postman:test
./gradlew :postbox:test
./gradlew :push:test
```

### 本地冒烟验证

项目内已有本地 IM 冒烟手册：

[docs/superpowers/runbooks/im-local-smoke-test.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/superpowers/runbooks/im-local-smoke-test.md)

其中包含：

- 在线投递
- 离线回退
- 重连拉取
- 已读后取消推送

最小回归命令示例：

```bash
cd server
./gradlew :postbox:test :postman:test :push:test :postoffice:test
```

## 配置入口

当前主要配置文件位于：

- `server/config/src/main/resources/common.yml`
- `server/config/src/main/resources/application-postoffice.yml`
- `server/config/src/main/resources/application-postman.yml`
- `server/config/src/main/resources/application-postbox.yml`
- `server/config/src/main/resources/application-push.yml`
- `server/config/src/main/resources/application-authcenter.yml`
- `server/config/src/main/resources/application-all.yml`

模块级默认行为包括：

- `postoffice` 默认开启 WebSocket 和 TCP 接入
- `postman` 默认开启补偿监听
- `push` 默认开启定时任务
- `postbox` 默认启用 MongoDB、Redis、Kafka 相关配置

## 设计文档

如果你想先理解当前重构后的架构，而不是直接读代码，建议先看：

- [docs/superpowers/specs/2026-03-17-im-architecture-design.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/superpowers/specs/2026-03-17-im-architecture-design.md)
- [docs/superpowers/specs/2026-03-17-im-message-pipeline-invariants.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/superpowers/specs/2026-03-17-im-message-pipeline-invariants.md)
- [docs/client-runbook.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/client-runbook.md)

`server/` 目录下也保留了较多历史设计资料与重构文档，可作为深入阅读入口。

## 当前状态与注意事项

这个仓库当前更适合被理解为“正在收敛中的 IM 系统实现”，而不是一个已经把所有面向终端产品能力都打磨完成的成品。

阅读和使用时建议注意：

- 根目录是 monorepo，不要直接假设后端命令都在根目录执行
- 后端实际构建和启动入口在 `server/`
- 本文档只描述当前代码与配置能支撑的事实
- 某些旧 Demo 或设计文档中的端口示例可能和当前默认配置不同，运行前请以 `server/config` 下配置为准
