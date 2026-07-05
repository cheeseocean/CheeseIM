# apps/CheeseBox/AGENTS.md — CheeseBox TUI 约束

> 基于 `sdks/go` 的 TUI 聊天应用，端到端联调主客户端。
> 改动前先读根 `AGENTS.md` + `sdks/go/AGENTS.md` + 本文件 + `apps/CheeseBox/arch.md`。

## 1. 目录

| 路径 | 职责 |
| --- | --- |
| `cmd/cheesebox` | 入口 main |
| `internal/` | TUI 组件、ViewModel、状态机 |
| `arch.md` | 架构说明（权威） |

## 2. 与 SDK 的边界

- CheeseBox 是 **TUI 应用**，不重复实现 IM 能力。所有 HTTP / TCP / 同步逻辑必须通过 `sdks/go` 调用。
- 改 CheeseBox 不要往 SDK 里塞 UI 特化逻辑；改 SDK 不要往 CheeseBox 里塞通用能力。

## 3. 已知能力缺口（README 已承认）

- 好友请求处理入口
- 会话删除入口
- 富媒体消息（图片/文件/语音）

补这些功能时，**先看 SDK 是否已有对应 API**；没有就在 SDK 加 API，CheeseBox 只做 UI 集成。

## 4. 命名与风格

- Go 风格沿用 `sdks/go/AGENTS.md`。
- TUI 组件 `PascalCase`，事件/channel 命名清晰表达流向。

## 5. 验证

```bash
cd apps/CheeseBox
go test ./...
go run ./cmd/cheesebox
```

端到端联调见根 `AGENTS.md` §9。

## 6. 改动评估 checklist

- [ ] 改协议侧字段后重新生成 `sdks/go/proto`，并更新 CheeseBox 调用
- [ ] 加新视图同步更新 `arch.md`
- [ ] 不引入 Java 依赖，不直接连 Mongo/Redis

## 7. 勘误记录

（暂无）