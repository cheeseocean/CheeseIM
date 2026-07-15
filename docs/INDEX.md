# CheeseIM 文档地图

> 全仓文档单点入口。每个文档标注**状态**与**维护人须知**。
> 与代码冲突时以代码为准；本文每季度回归一次。
> Agent 开发者：进入仓库先读本文 + `AGENTS.md`，再按需跳转。

## 状态标记定义

| 标记 | 含义 |
| --- | --- |
| **权威** | 当前事实，与代码同步，可直接引用 |
| **次级** | 补充说明，遇到冲突以"权威"为准 |
| **草案** | 设想阶段，未在代码中实现 |
| **过程** | 历史决策背景，仅作追溯，不可作为当前事实依据 |
| **废弃** | 已与代码脱节，建议删除候选 |

---

## 一、入口文档（必读）

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `README.md` | 权威 | 项目入口、架构、模块边界、启动与测试 |
| `README.en.md` | 次级 | 英文版，与 `README.md` 同步维护 |
| `AGENTS.md` | 权威 | **Agent 跨端开发约束**，所有 AI 代理（Claude/Codex/Cursor/Cline）入门必读 |
| `docs/INDEX.md` | 权威 | 本文件，全仓文档地图 |
| `server/docs/architecture/ASSESSMENT.md` | 权威 | 服务端架构评估、百万级演进路线、阻断性问题清单 |

## 二、服务端文档（`server/`）

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `server/AGENTS.md` | 权威 | Java 服务端 Agent 约束（合并自 `server/.claude/rules/`） |
| `server/README`（模块级） | 次级 | 各模块职责，见下表 |
| `server/postoffice/docs/TCP_PROTOCOL.md` | 权威 | TCP/WS Protobuf 长连接协议说明 |
| `server/postmaster/docs/ConversationArch.md` | 权威 | 会话模型设计背景 |
| `server/postmaster/docs/SeqArch.md` | 权威 | 会话 seq 分配设计背景 |
| `server/docs/architecture/read-revoke-design.md` | 次级 | 已读/撤回/输入中控制事件的第一阶段实现与第二阶段边界；实现事实以 `ASSESSMENT.md` 和代码为准 |
| `server/postoffice/README.md` | 权威 | 网关模块职责 |
| `server/postbox/README.md` | 权威 | 消息接入/历史查询模块职责 |
| `server/postmaster/README.md` | 权威 | 消息编排核心模块职责 |
| `server/postman/README.md` | 权威 | 投递与离线推送模块职责 |
| `server/docs/architecture/im_design_final.md` | **草案** | 早期架构设计概念稿（options 驱动流转、方案 B 投递），部分已落地、部分已偏离；阅读时需对照 `ASSESSMENT.md` |
| `server/docs/architecture/im_detail_design.md` | **草案** | 早期详细设计稿 |
| `server/docs/architecture/im_mongo_design.md` | **草案** | 早期 Mongo 模型设计，部分已与当前文档脱节 |
| `server/docs/architecture/im_skeleton.md` | **废弃** | 早期骨架草案，保留仅为追溯体量 |
| `server/docs/architecture/im_task_design.md` | **过程** | 早期任务化设计稿 |
| `server/docs/architecture/im-auth-design.md` | **过程** | authcenter 早期设计，已与 `authcenter/ARCH.md` 偏离 |
| `server/docs/architecture/im-auth-session-upgrade.md` | **过程** | session 升级过程记录 |

## 三、客户端与 SDK 文档

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `sdks/go/AGENTS.md` | 权威 | Go SDK Agent 约束 |
| `apps/CheeseBox/AGENTS.md` | 权威 | CheeseBox TUI Agent 约束 |
| `apps/CheeseBox/README.md` | 权威 | TUI 客户端说明 |
| `apps/CheeseBox/arch.md` | 权威 | TUI 客户端架构 |
| `docs/client-runbook.md` | 权威 | Go SDK/CheeseBox 与服务端联调入口 |

## 四、协议与控制层

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `server/common-api/src/main/proto/message_protocol.proto` | 权威 | TCP/WS Protobuf 协议源 |
| `docs/CheeseIM-数据同步设计文档.md` | 权威 | 会话/消息同步设计 |

## 五、部署与运维

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `distro/docker/docker-compose.middleware.yml` | 权威 | 中间件本地启动 |
| `distro/mongo/enable-im-sharding.js` | 权威 | 已就绪 Mongo 分片集群的 CheeseIM 集合分片与索引初始化脚本 |
| `server/config/src/main/resources/application-*.yml` | 权威 | 模块配置（详见 `server/AGENTS.md`） |

## 六、缺失文档（建议补）

- `docs/DEPLOYMENT.md` — 从单机到集群的部署矩阵（端口/中间件/profile 矩阵）。当前散落在 README，集中后便于自动化部署。
- `docs/PROTOCOL.md` — TCP/WS + HTTP 控制面一页纸总览。
- `docs/ROADMAP.md` — 演进路线独立成文（当前在 `ASSESSMENT.md` 第五节）。

---

## 七、维护人须知

1. **状态漂移治理**：每季度审查本文档，"过程"和"草案"若彻底失去追溯价值则降级为"废弃"。
2. **新文档落档**：新增 `.md` 必须在本文"对应章节"登记，未登记视为无主文档。
3. **冲突仲裁**：与代码冲突以代码为准，并在文档底部追加"勘误记录"。
4. **同步规则**：`README.md` 与 `README.en.md` 同步维护；`ASSESSMENT.md` 与重大架构改动同步更新。

## 八、勘误记录

（暂无）
