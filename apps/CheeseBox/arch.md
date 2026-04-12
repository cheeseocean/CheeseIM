# CheeseIM TUI 客户端设计文档

## 1. 文档目标

本文档用于指导 **CheeseIM 终端 UI 客户端（TUI）** 的设计与实现。

当前阶段聚焦以下最小可用需求：

- 单聊
- 群聊
- 加好友
- 基于真实 `CheeseIM server` 接口联调

同时，设计中需为后续功能预留扩展位，包括但不限于：

- 会话搜索
- 历史消息分页
- 消息状态（发送中 / 送达 / 已读 / 失败）
- 文件消息
- 群成员管理
- 用户资料页
- 通知中心
- 多端同步
- 断线重连与补同步

本文档默认客户端技术栈为：

- Go
- Bubble Tea
- Bubbles
- Lip Gloss
- `sdks/go` 通用 IM client SDK
- TCP / HTTP API（以 CheeseIM server 实际接口为准）

当前实现边界已经调整为：

- `sdks/go`
  - 通用 IM client 能力
  - 鉴权、TCP、会话同步、好友/群组/会话查询
- `apps/CheeseBox`
  - TUI 页面
  - 本地 UI store
  - 交互和显示逻辑

---

## 2. 范围定义

### 2.1 当前实现范围

本阶段只实现以下功能闭环：

#### 用户侧
- 登录后进入主界面
- 查看好友列表
- 发起单聊
- 查看群列表
- 进入群聊
- 发送文本消息
- 接收实时消息
- 添加好友

#### UI 侧
- 左侧导航栏
- 中间会话/联系人/群组列表
- 右侧聊天窗口
- 底部输入框
- 顶部连接状态栏
- 基础错误提示

#### 联调侧
- 基于真实 CheeseIM server 的 HTTP / WS 接口调试
- 不用 mock 替代真实消息链路
- 允许通过适配层兼容 CheeseIM 当前协议

---

### 2.2 暂不实现

以下能力暂不作为第一阶段交付内容，但设计中需预留：

- 已读回执
- 撤回消息
- 文件传输
- 图片消息
- 会话置顶 / 免打扰
- 搜索消息内容
- 本地数据库缓存
- 离线消息补拉
- 多端同时在线状态同步
- 群公告 / 群管理
- 消息编辑

---

## 3. 产品目标

### 3.1 核心目标

做一个可实际接入 CheeseIM server 的终端 IM 客户端，用于：

- 日常单聊与群聊
- 基础社交关系操作（加好友）
- server 接口联调与问题定位
- 为后续完整 IM 终端客户端打基础

### 3.2 非目标

当前版本不是为了追求完整的桌面 IM 体验，而是：

- 优先保证消息收发链路正确
- 优先保证 Bubble Tea 状态结构清晰
- 优先保证和 CheeseIM server 的协议适配稳定

---

## 4. 用户故事

### 4.1 单聊
- 作为用户，我可以从好友列表中选择一个好友开始聊天。
- 作为用户，我可以在单聊窗口发送文本消息。
- 作为用户，我可以在当前会话中实时收到对方消息。

### 4.2 群聊
- 作为用户，我可以查看自己所在的群组。
- 作为用户，我可以进入群聊查看消息。
- 作为用户，我可以在群里发送文本消息。
- 作为用户，我可以看到群消息中的发送者昵称。

### 4.3 加好友
- 作为用户，我可以输入用户 ID / 昵称 / 账号来添加好友。
- 作为用户，我可以看到加好友成功或失败提示。
- 作为用户，我添加成功后能在好友列表中看到对方。

### 4.4 联调
- 作为开发者，我可以连接真实 CheeseIM server。
- 作为开发者，我可以看到请求失败、连接失败、鉴权失败等错误信息。
- 作为开发者，我可以快速切换会话、重发消息、观察协议数据是否正确。

---

## 5. 界面设计

## 5.1 主界面布局

推荐采用三栏布局：

```text
┌──────────┬────────────────────────┬────────────────────────────────────────────┐
│ 导航     │ 列表区                 │ 聊天区                                     │
│──────────│────────────────────────│────────────────────────────────────────────│
│ [聊天]   │ 搜索/标题               │ ChatHeader                                 │
│ [好友]   │ 会话列表/好友列表/群列表 │────────────────────────────────────────────│
│ [群组]   │                        │ 消息区                                     │
│ [设置]   │                        │                                            │
│──────────│────────────────────────│────────────────────────────────────────────│
│ 状态信息 │ 操作提示               │ 输入框                                     │
└──────────┴────────────────────────┴────────────────────────────────────────────┘
```

### 5.2 导航栏

一级导航建议：

- 聊天
- 好友
- 群组
- 设置

后续可扩展：

- 通知
- 文件
- 搜索

### 5.3 列表区行为

根据导航切换展示内容：

#### 聊天
- 最近会话列表
- 单聊与群聊混排
- 显示最近消息摘要
- 显示未读数（后续）

#### 好友
- 好友列表
- 支持选中后打开单聊
- 支持快捷键打开“添加好友”弹窗

#### 群组
- 群组列表
- 选中后进入群聊

### 5.4 聊天区结构

聊天区分为三块：

1. `ChatHeader`
2. `MessageViewport`
3. `MessageInput`

#### ChatHeader
显示：
- 当前会话名称
- 会话类型（单聊 / 群聊）
- 在线状态（若 server 支持）
- 群成员数量（若 server 支持）

#### MessageViewport
当前先使用日志流样式：

```text
[09:41] Alice: 早上那个问题我看过了
[09:42] 我: 好，晚点我整理一下
[09:50] Bob: 我先修一下测试环境
```

群聊中必须展示发送者昵称。

#### MessageInput
- Enter 发送
- Ctrl+N 换行（可选）
- Esc 返回焦点切换

---

## 6. 功能设计

## 6.1 单聊

### 输入
- 从好友列表中选中好友
- 或从会话列表中选中单聊会话

### 输出
- 拉取当前会话最近消息
- 展示消息列表
- 允许发送文本消息
- 接收 server 推送的新消息

### 最小闭环
1. 选择好友
2. 打开单聊
3. 拉取消息历史（若接口已提供）
4. 输入消息并发送
5. 收到 ACK 或 server 消息推送
6. 刷新界面

---

## 6.2 群聊

### 输入
- 从群组列表中选择群
- 或从会话列表中选择群会话

### 输出
- 展示群历史消息
- 支持发送文本消息
- 接收群实时消息
- 渲染消息发送者昵称

### 群聊特殊点
- 群消息必须带 `sender_id / sender_name`
- 群名称与会话 ID 分开存储
- 后续预留群成员列表入口

---

## 6.3 加好友

### 最小需求
- 通过用户标识添加好友
- 操作入口在“好友”页
- 添加成功后刷新好友列表

### 交互方案
使用 modal：

```text
┌──────────────────────────────┐
│ 添加好友                      │
│──────────────────────────────│
│ 用户ID/账号: [            ]   │
│                              │
│ [确认]         [取消]         │
└──────────────────────────────┘
```

### 返回处理
- 成功：toast / status bar 显示成功
- 失败：显示错误原因
- 若 server 仅支持申请而非立即添加，也保留文案扩展位

---

## 7. 预留设计

当前虽然不实现，但数据结构和 UI 结构需预留以下扩展点。

### 7.1 消息状态
为每条本地消息预留状态字段：

- sending
- sent
- delivered
- read
- failed

### 7.2 消息类型
当前仅支持 `text`，但枚举需支持：

- text
- image
- file
- system
- recall

### 7.3 会话类型
需支持：

- single
- group
- system（后续）

### 7.4 同步能力
后续需可扩展为：

- 历史消息分页
- 重连后补拉消息
- 未读数同步
- 草稿恢复

### 7.5 资料与管理面板
后续可为右侧详情栏预留：

- 用户资料
- 群资料
- 群成员
- 群公告

---

## 8. 技术架构

## 8.1 分层建议

```text
TUI Layer
  ├─ AppModel
  ├─ NavModel
  ├─ ListModel
  ├─ ChatModel
  └─ ModalModel

Application Layer
  ├─ ConversationService
  ├─ FriendService
  ├─ GroupService
  └─ MessageService

Transport Layer
  ├─ HTTP Client
  ├─ WebSocket Client
  └─ CheeseIM Adapter

Domain Layer
  ├─ User
  ├─ Friend
  ├─ Group
  ├─ Conversation
  └─ Message
```

---

## 8.2 CheeseIM 接口适配原则

由于当前要基于 **真实 CheeseIM server** 联调，客户端不能假设协议固定为标准 IM 协议，因此需要单独做一层适配。

### 适配层职责
- 屏蔽 CheeseIM server 当前具体字段命名差异
- 屏蔽群聊 / 单聊接口差异
- 统一给 UI 输出标准领域模型
- 在联调阶段便于打印原始包与转换结果

### 适配方式
建议定义统一接口：

```go
type IMAdapter interface {
    Login(ctx context.Context, req LoginReq) (LoginResp, error)
    GetFriends(ctx context.Context) ([]Friend, error)
    AddFriend(ctx context.Context, keyword string) error
    GetGroups(ctx context.Context) ([]Group, error)
    GetConversations(ctx context.Context) ([]Conversation, error)
    GetMessages(ctx context.Context, convID string, limit int, before string) ([]Message, error)
    SendPrivateMessage(ctx context.Context, toUserID string, content string) (Message, error)
    SendGroupMessage(ctx context.Context, groupID string, content string) (Message, error)
    ConnectGateway(ctx context.Context) (<-chan ServerEvent, error)
}
```

> 实际方法名和参数以 CheeseIM server 已有接口为准。

---

## 9. 推荐数据模型

## 9.1 User / Friend

```go
type User struct {
    ID        string
    Username  string
    Nickname  string
    Avatar    string
    Online    bool
}

type Friend struct {
    User
    Remark string
}
```

## 9.2 Group

```go
type Group struct {
    ID          string
    Name        string
    MemberCount int
}
```

## 9.3 Conversation

```go
type ConversationType string

const (
    ConversationSingle ConversationType = "single"
    ConversationGroup  ConversationType = "group"
)

type Conversation struct {
    ID             string
    Type           ConversationType
    Title          string
    TargetID       string
    LastMessage    string
    LastMessageAt  time.Time
    UnreadCount    int
}
```

## 9.4 Message

```go
type MessageType string

type MessageStatus string

const (
    MessageTypeText MessageType = "text"
)

const (
    MessageStatusSending MessageStatus = "sending"
    MessageStatusSent    MessageStatus = "sent"
    MessageStatusFailed  MessageStatus = "failed"
)

type Message struct {
    ID             string
    ClientID       string
    ConversationID string
    ConversationType ConversationType
    SenderID       string
    SenderName     string
    TargetID       string
    Content        string
    Type           MessageType
    Status         MessageStatus
    SentAt         time.Time
    IsMine         bool
}
```

---

## 10. Bubble Tea 组件拆分

## 10.1 Root

`AppModel` 负责：
- 全局布局
- 焦点切换
- 路由
- modal 管理
- server 事件分发

## 10.2 子组件

### NavModel
负责：
- 一级导航切换

### ListModel
可复用于：
- 会话列表
- 好友列表
- 群组列表

### ChatModel
负责：
- 当前会话头部
- 消息视图
- 输入框

### ModalModel
当前先支持：
- AddFriendModal
- ConfirmModal（后续）

### StatusBarModel
显示：
- 连接状态
- 当前用户
- 错误提示
- 快捷键提示

---

## 11. 状态管理建议

## 11.1 全局状态

```go
type AppState struct {
    CurrentUser          User
    CurrentTab           string
    CurrentConversation  *Conversation
    Conversations        []Conversation
    Friends              []Friend
    Groups               []Group
    Messages             map[string][]Message
    Connected            bool
    LastError            string
}
```

## 11.2 焦点枚举

```go
type FocusArea int

const (
    FocusNav FocusArea = iota
    FocusList
    FocusChat
    FocusInput
    FocusModal
)
```

---

## 12. 联调设计

## 12.1 调试原则

本项目明确要求：

- 不做纯 mock 演示
- 必须接 CheeseIM server 真接口验证
- UI 要能观察真实消息收发链路
- 出错时要尽可能显示接口和事件层面的错误

## 12.2 联调阶段建议输出

建议增加 debug 面板或日志能力：

- HTTP 请求 URL / method
- 请求参数
- 响应状态码
- 响应 body 摘要
- WebSocket 连接状态
- 收到的原始 server event
- 适配转换后的领域对象

可通过以下方式实现：

- 标准日志输出到文件
- `--debug` 模式
- 独立 debug panel（后续）

## 12.3 真实 server 适配注意点

需要优先明确以下内容：

1. 登录方式
    - token 登录？
    - 用户名密码登录？
    - 登录后 token 是否需注入 ws 连接？

2. 会话模型
    - server 是否显式提供 conversation 列表？
    - 如果没有，是否要从消息关系推导？

3. 历史消息拉取
    - 是否有分页？
    - 是否区分单聊和群聊接口？

4. 实时推送协议
    - 是否统一消息事件结构？
    - 是否有 heartbeat / ping-pong？

5. 加好友流程
    - 直接建立好友关系？
    - 还是发申请后等待同意？

这些信息将决定适配层设计。

---

## 13. 推荐目录结构

```text
cheeseim-tui/
├── cmd/
│   └── cheeseim-tui/
│       └── main.go
├── internal/
│   ├── app/
│   │   ├── model.go
│   │   ├── update.go
│   │   ├── view.go
│   │   └── keys.go
│   ├── ui/
│   │   ├── nav/
│   │   ├── list/
│   │   ├── chat/
│   │   ├── modal/
│   │   └── statusbar/
│   ├── domain/
│   │   ├── user.go
│   │   ├── friend.go
│   │   ├── group.go
│   │   ├── conversation.go
│   │   └── message.go
│   ├── adapter/
│   │   ├── cheeseim/
│   │   │   ├── client.go
│   │   │   ├── http.go
│   │   │   ├── ws.go
│   │   │   ├── mapper.go
│   │   │   └── protocol.go
│   ├── service/
│   │   ├── friend_service.go
│   │   ├── group_service.go
│   │   ├── conversation_service.go
│   │   └── message_service.go
│   └── debug/
│       └── logger.go
├── docs/
│   └── design.md
└── README.md
```

---

## 14. 里程碑建议

## M1：静态骨架
- 完成三栏布局
- 完成导航和列表切换
- 假数据渲染聊天界面

## M2：好友 / 群组 / 单聊接入真实接口
- 接入登录
- 拉好友列表
- 拉群组列表
- 打开单聊 / 群聊

## M3：消息联调
- 接入发送消息接口
- 接入实时推送
- 当前会话刷新
- 非当前会话摘要更新

## M4：加好友联调
- 添加好友 modal
- 接入 add friend 接口
- 刷新好友列表

## M5：稳定性补强
- 错误展示
- 发送失败处理
- 重连机制预留
- 历史消息加载预留

---

## 15. 验收标准

当前阶段完成后，至少满足：

1. 能连接真实 CheeseIM server
2. 能显示好友列表
3. 能显示群组列表
4. 能发起单聊
5. 能进入群聊
6. 能发送文本消息
7. 能接收实时消息
8. 能添加好友
9. UI 状态清晰，不因网络事件导致崩溃
10. 代码结构支持后续功能扩展

---

## 16. 风险与建议

### 风险
- CheeseIM server 接口字段与假定模型不一致
- server 没有统一 conversation 模型
- 单聊 / 群聊走不同消息链路
- WebSocket 推送协议不稳定或缺少文档
- 加好友不是即时成功型接口

### 建议
- 先做 adapter，再做 UI 绑定
- 不要在 Bubble Tea 组件里直接写协议转换逻辑
- 所有 server 原始响应都保留 debug 日志
- 先做“能联调”的版本，再做“更好看”的版本

---

# 附录 A：给代码生成/联调助手的 Prompt

下面这份 Prompt 可用于让另一个模型或编码助手基于你现有的 CheeseIM server 来完成 UI 端开发和联调。

```text
你是一个资深 Go 工程师和 TUI 架构师。请基于我的已有 IM 服务端 CheeseIM，帮我实现一个 Bubble Tea 终端客户端。

目标：
1. 使用 Go + Bubble Tea + Bubbles + Lip Gloss 实现一个真实可运行的 IM TUI 客户端。
2. 当前只实现以下需求：
   - 单聊
   - 群聊
   - 加好友
3. 必须基于真实 CheeseIM server 接口联调，不要只做 mock UI。
4. 代码结构要为后续能力预留扩展，包括：历史消息分页、消息状态、断线重连、文件消息、会话搜索、通知中心、群成员管理。

实现要求：
1. 先定义清晰的目录结构，至少包含：
   - app
   - ui
   - domain
   - adapter/cheeseim
   - service
   - debug
2. 必须增加 CheeseIM adapter 层，不能让 Bubble Tea UI 直接依赖原始 HTTP/WS 协议字段。
3. 统一抽象领域模型：User / Friend / Group / Conversation / Message。
4. UI 采用三栏布局：
   - 左侧导航：聊天 / 好友 / 群组 / 设置
   - 中间列表：根据当前 tab 展示会话、好友或群组
   - 右侧聊天区：Header + 消息列表 + 输入框
5. 消息展示先用日志流样式，不做复杂气泡。
6. 单聊和群聊都支持文本消息发送。
7. 群聊消息必须显示发送者昵称。
8. 好友页支持弹出 AddFriend modal，输入用户ID/账号后调用 server 接口。
9. 对所有真实请求和推送增加 debug 日志，便于排查 server 联调问题。
10. 遇到 CheeseIM server 文档不完整时，不要停下来问泛泛问题；先通过适配层预留接口，并明确标出 TODO 和假设。

联调要求：
1. 优先梳理我已有 CheeseIM server 的接口：
   - 登录接口
   - 获取好友列表
   - 添加好友
   - 获取群列表
   - 获取会话列表（如果有）
   - 获取历史消息
   - 发送单聊消息
   - 发送群聊消息
   - WebSocket / Gateway 实时推送
2. 如果 server 没有 conversation 列表接口，请从好友、群组和最近消息关系推导 conversation 列表。
3. 如果单聊和群聊协议不同，请在 adapter 层内部屏蔽，不要污染 UI 层。
4. 输出时优先给出：
   - 项目目录结构
   - 核心 domain model
   - adapter 接口设计
   - Bubble Tea root model 设计
   - 关键 Update / View 代码骨架
   - 与 CheeseIM server 对接的代码骨架
5. 所有代码都尽量给出可直接放入项目的版本，不要只给概念说明。

编码风格要求：
- Go 代码清晰、模块化、可维护
- 避免把所有逻辑塞进一个巨大的 Update() 函数
- UI 状态和网络协议转换分层清晰
- 对可能失败的接口调用增加错误处理和日志
- 对未来扩展位写清楚注释

如果我继续提供 CheeseIM server 的接口文档、接口代码、路由定义、protobuf/JSON 结构，请继续在现有架构基础上完善 adapter 和 UI，不要推翻重来。
```

---

# 附录 B：更适合“联调模式”的 Prompt

如果你是拿这份 prompt 去让模型帮你“边看 CheeseIM server 边写 UI”，建议用下面这版更直接：

```text
基于我现有的 CheeseIM server，帮我完成一个 Go + Bubble Tea 的 IM TUI 客户端。

当前只做：
- 单聊
- 群聊
- 加好友

要求：
- 必须基于真实 server 接口联调
- 不要只做 mock
- 用 adapter 层兼容 CheeseIM 现有协议
- UI 做成三栏：导航 / 列表 / 聊天区
- 支持好友列表、群组列表、消息发送、消息接收、加好友弹窗
- 代码结构要为后续文件消息、消息状态、历史消息分页、断线重连预留扩展

请按以下顺序输出：
1. 先分析我给你的 CheeseIM server 接口/代码
2. 抽象统一 domain model
3. 设计 adapter 层
4. 设计 Bubble Tea root model 和子组件
5. 输出完整项目骨架代码
6. 输出关键联调代码（HTTP + WebSocket）
7. 标出所有和 CheeseIM server 强绑定的地方
8. 对文档不明确的地方写 TODO，不要跳过整体实现

注意：
- 群聊消息必须显示发送者昵称
- 单聊和群聊要统一映射成 Conversation + Message
- 加好友先按最简单流程处理，但保留申请制扩展位
- 所有真实请求都要带 debug 日志
- 不要把协议字段转换逻辑写在 Bubble Tea UI 组件里
```
