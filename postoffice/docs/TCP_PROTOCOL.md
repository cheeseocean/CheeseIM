# CheeseIM TCP 自定义协议文档

## 概述

CheeseIM Postoffice 网关现在支持双协议模式：
- **WebSocket 协议**：用于 Web 端（端口 8080）
- **TCP 自定义协议**：用于移动端和桌面端（端口 8081）

## 协议格式

### TCP 消息格式

```
+--------+--------+--------+--------+--------+--------+--------+--------+
| Magic  | Version| MsgType| Length |    OperationID (16 bytes)        |
+--------+--------+--------+--------+--------+--------+--------+--------+
|                    Timestamp (8 bytes)                               |
+--------+--------+--------+--------+--------+--------+--------+--------+
|                    Data (Length bytes)                               |
+--------+--------+--------+--------+--------+--------+--------+--------+
```

### 字段说明

| 字段 | 长度 | 类型 | 说明 |
|------|------|------|------|
| Magic | 2 bytes | short | 协议标识：0xCEEE |
| Version | 1 byte | byte | 协议版本：0x01 |
| MsgType | 1 byte | byte | 消息类型 |
| Length | 4 bytes | int | 数据长度 |
| OperationID | 16 bytes | string | 操作ID，用于请求追踪 |
| Timestamp | 8 bytes | long | 时间戳 |
| Data | Length bytes | string | 消息数据（JSON格式） |

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

### 错误相关
- `TCP_ERROR_RESP (90)`: 通用错误响应
- `TCP_PARAM_ERROR (91)`: 参数错误
- `TCP_PERMISSION_ERROR (92)`: 权限错误
- `TCP_INTERNAL_ERROR (93)`: 服务器内部错误

## 连接流程

### 1. 建立连接
```
Client -> Server: TCP_CONNECT_REQ
Server -> Client: TCP_CONNECT_SUCCESS
```

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
```

### 3. 心跳保活
```
Client -> Server: TCP_HEARTBEAT_REQ ("ping")
Server -> Client: TCP_HEARTBEAT_RESP ("pong")
```

### 4. 发送消息
```
Client -> Server: TCP_SEND_MSG_REQ
{
  "content": "Hello World!",
  "contentType": 101,
  "recvID": "receiver123"
}

Server -> Client: TCP_SEND_MSG_RESP
{
  "serverMsgID": "msg-456",
  "clientMsgID": "client-123",
  "sendTime": 1640995200000
}
```

## 配置说明

### application.yml 配置

```yaml
postoffice:
  # WebSocket服务器配置（Web端）
  websocket:
    enabled: true
    port: 8080
    path: "/ws"
    ssl:
      enabled: false
      cert-path: ""
      key-path: ""
  
  # TCP服务器配置（移动端和桌面端）
  tcp:
    enabled: true
    port: 8081
    ssl:
      enabled: false
      cert-path: ""
      key-path: ""
```

## 客户端实现指南

### Java 客户端示例

```java
// 连接到TCP服务器
Socket socket = new Socket("localhost", 8081);
OutputStream out = socket.getOutputStream();
InputStream in = socket.getInputStream();

// 发送连接请求
TcpMessage connectReq = new TcpMessage(TcpMessageType.TCP_CONNECT_REQ, 
                                      UUID.randomUUID().toString(), 
                                      "{}");
out.write(connectReq.encode());

// 接收响应
byte[] responseBytes = new byte[1024];
int bytesRead = in.read(responseBytes);
TcpMessage response = TcpMessage.decode(Arrays.copyOf(responseBytes, bytesRead));
```

### Dart 客户端示例

```dart
import 'dart:io';
import 'dart:typed_data';

class TcpClient {
  Socket? _socket;
  
  Future<void> connect(String host, int port) async {
    _socket = await Socket.connect(host, port);
    
    // 发送连接请求
    final connectReq = TcpMessage(
      msgType: 1, // TCP_CONNECT_REQ
      operationID: generateUUID(),
      data: '{}',
    );
    
    _socket!.add(connectReq.encode());
    
    // 监听响应
    _socket!.listen((data) {
      final message = TcpMessage.decode(data);
      handleMessage(message);
    });
  }
  
  void handleMessage(TcpMessage message) {
    switch (message.msgType) {
      case 2: // TCP_CONNECT_SUCCESS
        print('Connected successfully');
        break;
      case 21: // TCP_HEARTBEAT_RESP
        print('Heartbeat response received');
        break;
      // ... 其他消息类型处理
    }
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
    MsgType     byte
    OperationID string
    Timestamp   int64
    Data        string
}

func (m *TcpMessage) Encode() []byte {
    // 实现编码逻辑
    // ...
}

func main() {
    conn, err := net.Dial("tcp", "localhost:8081")
    if err != nil {
        panic(err)
    }
    defer conn.Close()
    
    // 发送连接请求
    connectReq := &TcpMessage{
        MsgType:     1, // TCP_CONNECT_REQ
        OperationID: generateUUID(),
        Data:        "{}",
    }
    
    conn.Write(connectReq.Encode())
    
    // 读取响应
    buffer := make([]byte, 1024)
    n, err := conn.Read(buffer)
    if err != nil {
        panic(err)
    }
    
    response := DecodeTcpMessage(buffer[:n])
    fmt.Printf("Received: %+v\n", response)
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
        
        // 发送连接请求
        val connectReq = TcpMessage(
            msgType = 1, // TCP_CONNECT_REQ
            operationID = UUID.randomUUID().toString(),
            data = "{}"
        )
        
        out.write(connectReq.encode())
        
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
            2 -> println("Connected successfully") // TCP_CONNECT_SUCCESS
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

### API 接口

- `GET /api/v1/postoffice/status/tcp` - 获取TCP服务器状态
- `GET /api/v1/postoffice/connections/stats` - 获取连接统计
- `GET /api/v1/postoffice/users/{userID}/online` - 检查用户在线状态

### 日志配置

```yaml
logging:
  level:
    com.cheeseocean.im.postoffice.server.CheeseServer: DEBUG
    com.cheeseocean.im.postoffice.codec: DEBUG
```

## 安全考虑

1. **SSL/TLS 支持**：生产环境建议启用SSL
2. **Token 验证**：使用JWT进行用户认证
3. **连接限制**：限制每个用户的最大连接数
4. **消息验证**：验证消息格式和权限

## 部署建议

1. **端口配置**：确保8081端口对客户端开放
2. **防火墙设置**：配置防火墙规则
3. **负载均衡**：使用TCP负载均衡器
4. **监控告警**：监控连接数和消息处理性能
