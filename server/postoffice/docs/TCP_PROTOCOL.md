# CheeseIM TCP 自定义协议文档

## 概述

CheeseIM Postoffice 网关现在支持双协议模式：
- **WebSocket 协议**：用于 Web 端（默认端口 5147，路径 `/ws`）
- **TCP 自定义协议**：用于移动端和桌面端（默认端口 5148）

## 协议格式

### TCP 消息格式

```
+--------+--------+--------+--------+--------+--------+--------+--------+
| Magic  | Version| CommandType| Length |    OperationID (16 bytes)        |
+--------+--------+--------+--------+--------+--------+--------+--------+
|                    Timestamp (8 bytes)                               |
+--------+--------+--------+--------+--------+--------+--------+--------+
|                    Data (Length bytes)                               |
+--------+--------+--------+--------+--------+--------+--------+--------+
```

### 字段说明

| 字段 | 长度 | 类型 | 说明 |
|------|------|------|------|
| Magic | 2 bytes | short | 协议标识：0xCEEE，按大端序写入 |
| Version | 1 byte | byte | 协议版本：0x01 |
| CommnadType | 1 byte | byte | 消息类型 |
| Length | 4 bytes | int | 数据长度，按大端序写入 |
| OperationID | 16 bytes | string | UTF-8 编码后固定 16 字节；超长截断，不足补零，解码后 trim |
| Timestamp | 8 bytes | long | 毫秒时间戳，按大端序写入 |
| Data | Length bytes | string | UTF-8 文本载荷；请求通常是 JSON，对 `CONNECT_SUCCESS`、`AUTH_FAILED`、`HEARTBEAT_RESP` 等响应也可能是普通字符串 |

### 编码约定

- `CheeseMessage` 使用 Java `ByteBuffer` 默认的大端序（big-endian）
- `OperationID` 在 TCP 线上总是固定 16 字节，不是变长字符串
- `Data` 必须按 `Length` 读取完整后再决定是否解析为 JSON；不要假设所有消息都携带 JSON

## 消息类型

### 连接相关
- `TCP_CONNECT_REQ (1)`: 连接请求
- `TCP_CONNECT_SUCCESS (2)`: 连接成功响应
- `TCP_CONNECT_FAILED (3)`: 连接失败响应

### 认证相关
- `TCP_AUTH_REQ (10)`: 认证请求
- `TCP_AUTH_SUCCESS (11)`: 认证成功响应
- `TCP_AUTH_FAILED (12)`: 认证失败响应

### 心跳相关
- `TCP_HEARTBEAT_REQ (20)`: 心跳请求
- `TCP_HEARTBEAT_RESP (21)`: 心跳响应

### 消息相关
- `TCP_SEND_MSG_REQ (30)`: 发送消息请求
- `TCP_SEND_MSG_RESP (31)`: 发送消息响应
- `TCP_RECV_MSG_NOTIFY (32)`: 接收消息通知
- `TCP_MSG_READ_RECEIPT (33)`: 历史已读回执请求，现已废弃，不再作为服务端主链路入口

### 错误相关
- `TCP_ERROR_RESP (90)`: 通用错误响应
- `TCP_PARAM_ERROR (91)`: 参数错误
- `TCP_PERMISSION_ERROR (92)`: 权限错误
- `TCP_INTERNAL_ERROR (93)`: 服务器内部错误

## 连接流程

### 1. 建立底层连接
```
TCP Socket / WebSocket 握手建立
Server -> Client: CONNECT_SUCCESS
"连接成功"
```

#### 载荷示例（连接）
- **TCP 连接建立成功推送**：`msgType=2, operationID="system", data="连接成功"`
- **WebSocket 连接建立成功推送**：`{"msgType":1002,"operationID":"system","data":"连接成功"}`

### 2. 用户认证
```
Client -> Server: TCP_AUTH_REQ
{
  "token": "jwt-token",
  "userID": "user123",
  "platformID": 2
}

Server -> Client: TCP_AUTH_SUCCESS
{
  "userID": "user123",
  "message": "认证成功"
}

Server -> Client: TCP_AUTH_FAILED
"token invalid"
```

#### 载荷示例（认证）
- **TCP 认证请求**：`{"token":"jwt-token","userID":"user123","platformID":2}`
- **TCP 认证成功响应**：`{"userID":"user123","message":"认证成功"}`
- **TCP 认证失败响应**（示例）：`"token invalid"`
- **WebSocket 认证请求**：`{"token":"jwt-token","userID":"user123","platformID":2}`
- **WebSocket 认证成功响应**：`{"userID":"user123","message":"认证成功"}`
- **WebSocket 认证失败响应**（示例）：`"token invalid"`

### 3. 心跳保活
```
Client -> Server: TCP_HEARTBEAT_REQ ("ping")
Server -> Client: TCP_HEARTBEAT_RESP ("pong")
```

### 4. 发送消息
```
Client -> Server: TCP_SEND_MSG_REQ
{
  "clientMsgID": "client-123",
  "content": "Hello World!",
  "contentType": 101,
  "recvID": "receiver123",
  "chatType": 1
}

Server -> Client: TCP_SEND_MSG_RESP
{
  "serverMsgID": "msg-456",
  "clientMsgID": "client-123",
  "sendTime": 1710000000000
}
```

#### 载荷示例（发送消息）
- **TCP 发送消息请求**：`{"clientMsgID":"client-123","recvID":"receiver123","content":"Hello World!","contentType":101,"chatType":1}`
- **TCP 发送消息响应**：`{"serverMsgID":"msg-456","clientMsgID":"client-123","sendTime":1710000000000}`
- **WebSocket 发送消息请求**：`{"clientMsgID":"client-123","recvID":"receiver123","content":"Hello World!","contentType":101,"chatType":1}`
- **WebSocket 发送消息响应**：`{"serverMsgID":"msg-456","clientMsgID":"client-123","sendTime":1710000000000}`

### 4.1 已读回执

现行已读回执统一走发送消息入口：

- WebSocket: `WS_SEND_MSG_REQ (2001)`
- TCP: `TCP_SEND_MSG_REQ (30)`
- `contentType = 2004`，即 `READ_RECEIPT`
- `content` 为 JSON 字符串，表达 `READ_CURSOR`

请求载荷示例：

```json
{
  "clientMsgID": "read-123",
  "recvID": "receiver123",
  "contentType": 2004,
  "chatType": 1,
  "content": "{\"receiptType\":\"READ_CURSOR\",\"conversationId\":\"c1:receiver123:user123\",\"seq\":19}"
}
```

处理语义：

- 服务端直接更新 `userReadSeq`
- 不分配新的消息 `seq`
- 不写历史
- 不更新会话 lastMessage
- 不走普通消息投递链

兼容性说明：

- 历史的 `WS_MSG_READ_NOTIFY (2004)` / `TCP_MSG_READ_RECEIPT (33)` 已不再作为业务入口
- 客户端如果继续发送旧入口，请视为协议不兼容并尽快切换到 `SEND_MSG_REQ + contentType=2004`

### 5. 接收消息通知
```
Server -> Client: TCP_RECV_MSG_NOTIFY
{
  "serverMsgID": "msg-456",
  "clientMsgID": "client-123",
  "sendID": "receiver123",
  "recvID": "user123",
  "content": "Hello World!",
  "contentType": 101,
  "chatType": 1,
  "sendTime": 1710000000000
}

Server -> Client: WS_RECV_MSG_NOTIFY
{
  "serverMsgID": "msg-456",
  "clientMsgID": "client-123",
  "sendID": "receiver123",
  "recvID": "user123",
  "content": "Hello World!",
  "contentType": 101,
  "chatType": 1,
  "sendTime": 1710000000000
}
```

#### 载荷示例（接收通知）
- **TCP 接收通知**：`{"serverMsgID":"msg-456","clientMsgID":"client-123","sendID":"receiver123","recvID":"user123","content":"Hello World!","contentType":101,"chatType":1,"sendTime":1710000000000}`
- **WebSocket 接收通知**：`{"serverMsgID":"msg-456","clientMsgID":"client-123","sendID":"receiver123","recvID":"user123","content":"Hello World!","contentType":101,"chatType":1,"sendTime":1710000000000}`


## 配置说明

### application.yml 配置

```yaml
postoffice:
  # WebSocket服务器配置（Web端）
  websocket:
    enabled: true
    port: 5147
    path: "/ws"
    ssl:
      enabled: false
      cert-path: ""
      key-path: ""
  
  # TCP服务器配置（移动端和桌面端）
  tcp:
    enabled: true
    port: 5148
    ssl:
      enabled: false
      cert-path: ""
      key-path: ""
```

## 客户端实现指南

### Java 客户端示例

```java
// 连接到TCP服务器
Socket socket = new Socket("localhost", 5148);
OutputStream out = socket.getOutputStream();
InputStream in = socket.getInputStream();

// 服务端会在连接建立后主动推送 CONNECT_SUCCESS(system)
// 接收响应时必须先读满固定头，再按 Length 继续读完整 payload
// 不能假设一次 in.read(...) 就得到完整消息
TcpMessage response = receiveMessage(in);

// 收到 CONNECT_SUCCESS 后，再发送认证请求
TcpMessage authReq = new TcpMessage(TcpMessageType.TCP_AUTH_REQ,
                                   UUID.randomUUID().toString(),
                                   "{\"token\":\"jwt-token\",\"userID\":\"user123\",\"platformID\":2}");
out.write(authReq.encode());
```

### Dart 客户端示例

```dart
import 'dart:io';
import 'dart:typed_data';

class TcpClient {
  Socket? _socket;
  
  Future<void> connect(String host, int port) async {
    _socket = await Socket.connect(host, port);
    
    // 监听响应
    _socket!.listen((data) {
      // 生产环境必须做 Length 拆包/组包，不能直接按单个 socket chunk 解码
      frameDecoder.addChunk(data);
      for (final message in frameDecoder.drainFrames()) {
        handleMessage(message);
      }
    });
  }
  
  void handleMessage(TcpMessage message) {
    switch (message.msgType) {
      case 2: // TCP_CONNECT_SUCCESS
        print('Connected successfully');
        sendAuth();
        break;
      case 21: // TCP_HEARTBEAT_RESP
        print('Heartbeat response received');
        break;
      // ... 其他消息类型处理
    }
  }

  void sendAuth() {
    final authReq = TcpMessage(
      msgType: 10, // TCP_AUTH_REQ
      operationID: generateUUID(),
      data: '{"token":"jwt-token","userID":"user123","platformID":2}',
    );
    _socket!.add(authReq.encode());
  }
}
```

### Go 客户端示例

```go
package main

import (
    "net"
    "encoding/binary"
    "fmt"
)

type TcpMessage struct {
    CommnadType     byte
    OperationID string
    Timestamp   int64
    Data        string
}

func (m *TcpMessage) Encode() []byte {
    // 实现编码逻辑
    // ...
}

func main() {
    conn, err := net.Dial("tcp", "localhost:5148")
    if err != nil {
        panic(err)
    }
    defer conn.Close()
    
    // 先读取服务端主动推送的 CONNECT_SUCCESS(system)
    response, err := receiveMessage(conn)
    if err != nil {
        panic(err)
    }
    fmt.Printf("Received connect push: %+v\n", response)

    // 收到 CONNECT_SUCCESS 后再发送认证请求
    authReq := &TcpMessage{
        CommnadType:     10, // TCP_AUTH_REQ
        OperationID: generateUUID(),
        Data:        "{\"token\":\"jwt-token\",\"userID\":\"user123\",\"platformID\":2}",
    }
    conn.Write(authReq.Encode())
}
```

### Kotlin 客户端示例

```kotlin
import java.net.Socket
import java.io.OutputStream
import java.io.InputStream

class TcpClient {
    private var socket: Socket? = null
    
    fun connect(host: String, port: Int) {
        socket = Socket(host, port)
        
        val out = socket!!.getOutputStream()
        val input = socket!!.getInputStream()
        
        // 启动消息接收线程
        Thread {
            while (socket?.isConnected == true) {
                val message = receiveMessage(input)
                handleMessage(message)
            }
        }.start()
    }
    
    private fun handleMessage(message: TcpMessage) {
        when (message.msgType.toInt()) {
            2 -> {
                println("Connected successfully") // TCP_CONNECT_SUCCESS
                val authReq = TcpMessage(
                    msgType = 10, // TCP_AUTH_REQ
                    operationID = UUID.randomUUID().toString(),
                    data = "{\"token\":\"jwt-token\",\"userID\":\"user123\",\"platformID\":2}"
                )
                socket?.getOutputStream()?.write(authReq.encode())
            }
            21 -> println("Heartbeat response") // TCP_HEARTBEAT_RESP
            // ... 其他消息类型处理
        }
    }
}
```

## 性能优势

### TCP vs WebSocket

| 特性 | TCP 自定义协议 | WebSocket |
|------|---------------|-----------|
| 协议开销 | 32 bytes 固定头部 | HTTP + WebSocket 头部 |
| 数据格式 | 二进制 | 文本/二进制 |
| 解析性能 | 高（直接二进制解析） | 中等（JSON解析） |
| 网络传输 | 高效 | 中等 |
| 适用场景 | 移动端、桌面端 | Web端 |

## 错误处理

### 常见错误码

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 400 | 参数错误 | 检查请求参数格式 |
| 401 | 认证失败 | 重新获取有效token |
| 403 | 权限不足 | 检查用户权限 |
| 500 | 服务器内部错误 | 重试或联系管理员 |

### 连接异常处理

1. **连接超时**：检查网络连接，重试连接
2. **认证失败**：刷新token，重新认证
3. **心跳超时**：检查网络状态，重新连接
4. **消息发送失败**：检查连接状态，重试发送

## 监控和调试

### 调试方式

- 查看 `postoffice` 应用日志确认 TCP 服务状态
- 运行 `TcpClientTest` 和 `MessageSendReqMapperTest` 验证 TCP 协议编解码与发送请求映射链路

### 日志配置

```yaml
logging:
  level:
    com.cheeseocean.im.postoffice.server.TcpServer: DEBUG
    com.cheeseocean.im.postoffice.codec: DEBUG
```

## 安全考虑

1. **SSL/TLS 支持**：生产环境建议启用SSL
2. **Token 验证**：使用JWT进行用户认证
3. **连接限制**：限制每个用户的最大连接数
4. **消息验证**：验证消息格式和权限

## 部署建议

1. **端口配置**：确保5148端口对客户端开放
2. **防火墙设置**：配置防火墙规则
3. **负载均衡**：使用TCP负载均衡器
4. **监控告警**：监控连接数和消息处理性能
