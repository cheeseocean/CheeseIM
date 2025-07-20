# CheeseIM Push Service

推送服务模块，参照OpenIM Server的push模块实现，负责在线推送和离线推送功能。

## 🚀 功能特性

### 核心功能
- **在线推送** - 通过Dubbo RPC调用postoffice进行实时推送
- **离线推送** - 通过第三方推送服务（APNs、FCM、极光推送等）进行离线推送
- **推送路由** - 智能推送路由，先在线推送，失败后转离线推送
- **设备管理** - 设备Token管理和多平台支持
- **推送模板** - 推送内容模板管理和个性化推送
- **推送统计** - 完整的推送统计和实时监控

### 技术特性
- **Kafka集成** - 监听推送消息队列，支持消息重试
- **Dubbo RPC** - 调用postoffice进行在线推送
- **Redis缓存** - 设备Token缓存，推送配置缓存
- **多推送提供商** - 支持APNs、FCM、极光推送、华为推送、小米推送
- **异步处理** - 异步推送处理，提升系统吞吐量
- **监控统计** - 实时统计和监控，支持性能分析

## 🏗️ 架构设计

### 参照OpenIM Server架构

```
push 模块架构：
├── PushMessageListener      - 监听toPushTopic，进行推送路由
├── OfflinePushListener      - 监听offlinePushTopic，进行离线推送
├── OnlinePushService        - 在线推送服务（通过Dubbo调用postoffice）
├── OfflinePushService       - 离线推送服务（第三方推送）
├── PushRoutingService       - 推送路由服务
├── PushProviderService      - 推送提供商服务（APNs、FCM等）
├── DeviceTokenService       - 设备Token管理服务
├── PushTemplateService      - 推送模板服务
├── PushStatisticsService    - 推送统计服务
└── PushController           - REST API控制器
```

### 推送流程

```
1. 接收推送消息 (from postman via Kafka)
   ↓
2. 推送路由 (PushRoutingService)
   ├── 在线推送 → 通过Dubbo调用postoffice
   │   ├── 成功 → 推送完成
   │   └── 失败/离线 → 转离线推送
   ↓
3. 离线推送 (OfflinePushService)
   ├── 发送到offlinePushTopic
   ├── 监听offlinePushTopic
   ├── 第三方推送服务推送
   └── 支持重试机制
   ↓
4. 推送统计 (PushStatisticsService)
```

## 🛠️ 工具类支持

### PushMessageBuilder - 推送消息构建器
```java
// 创建文本推送消息
PushMessage textPush = PushMessageBuilder.create()
    .userID("user123")
    .foriOS()
    .title("新消息")
    .content("Hello World")
    .asTextMessage()
    .build();

// 创建系统通知
PushMessage systemPush = PushMessageBuilder.create()
    .userID("user123")
    .forAndroid()
    .title("系统通知")
    .content("您有新的好友申请")
    .asSystemNotification()
    .expireAfter(3600) // 1小时后过期
    .build();
```

### PushContentGenerator - 推送内容生成器
```java
// 根据消息自动生成推送内容
String title = PushContentGenerator.generateTitle(message, groupName);
String content = PushContentGenerator.generateContent(message);
String summary = PushContentGenerator.generateSummary(message, groupName);

// 检查消息是否需要推送
boolean shouldPush = PushContentGenerator.shouldPush(message);
int priority = PushContentGenerator.getPushPriority(message);
```

### PushMessageValidator - 推送消息验证器
```java
// 验证推送消息
PushMessageValidator.ValidationResult result = PushMessageValidator.validate(pushMessage);
if (!result.isValid()) {
    System.out.println("验证失败: " + result.getErrorMessage());
}

// 快速验证
boolean isValid = PushMessageValidator.quickValidate(pushMessage);
```

## 📋 API接口

### REST API

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/v1/push/health` | GET | 健康检查 |
| `/api/v1/push/status` | GET | 服务状态 |
| `/api/v1/push/stats/push` | GET | 推送统计 |
| `/api/v1/push/stats/realtime` | GET | 实时统计 |
| `/api/v1/push/stats/tokens` | GET | 设备Token统计 |
| `/api/v1/push/users/{userID}/online` | GET | 检查用户在线状态 |
| `/api/v1/push/users/{userID}/tokens` | GET | 获取用户设备Token |
| `/api/v1/push/users/{userID}/tokens` | POST | 保存用户设备Token |
| `/api/v1/push/users/{userID}/tokens` | DELETE | 删除用户设备Token |
| `/api/v1/push/users/{userID}/offline-push` | GET | 获取用户离线推送配置 |
| `/api/v1/push/stats/reset` | POST | 重置统计 |
| `/api/v1/push/config` | GET | 服务配置 |

### Kafka Topics

| Topic | 类型 | 描述 |
|-------|------|------|
| `cheese_im_to_push` | 消费 | 接收推送消息请求 |
| `cheese_im_offline_push` | 消费/生产 | 离线推送消息队列 |

### Dubbo Services

| Service | 类型 | 描述 |
|---------|------|------|
| `PostofficeOnlinePushService` | 消费 | 调用postoffice进行在线推送 |

## ⚙️ 配置说明

### application.yml
```yaml
server:
  port: 8083

spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: push-group
    producer:
      acks: all
      retries: 3

dubbo:
  application:
    name: cheese-im-push
  registry:
    address: nacos://localhost:8848
  consumer:
    timeout: 5000
    retries: 2
    check: false

cheese:
  im:
    push:
      apns:
        enabled: false
        key-path: /path/to/apns.p8
        key-id: YOUR_KEY_ID
        team-id: YOUR_TEAM_ID
        bundle-id: com.example.app
        production: true
      jpush:
        enabled: false
        app-key: YOUR_APP_KEY
        master-secret: YOUR_MASTER_SECRET
        production: true
```

## 🚀 启动方式

### 本地开发
```bash
# 启动依赖服务
docker run -d --name redis -p 6379:6379 redis:latest
docker run -d --name kafka -p 9092:9092 confluentinc/cp-kafka:latest
docker run -d --name nacos -p 8848:8848 nacos/nacos-server:latest

# 启动推送服务
./start.sh
# 或者
./gradlew :push:bootRun
```

### Docker部署
```bash
# 构建镜像
docker build -t cheese-im-push:latest .

# 启动容器
docker run -d --name cheese-im-push \
  -p 8083:8083 \
  -e redis.host=redis \
  -e kafka.bootstrap-servers=kafka:9092 \
  -e nacos.server-addr=nacos:8848 \
  cheese-im-push:latest
```

## 🧪 测试指南

### 1. 健康检查
```bash
curl http://localhost:8083/api/v1/push/health
```

### 2. 查看服务状态
```bash
curl http://localhost:8083/api/v1/push/status
```

### 3. 查看推送统计
```bash
curl http://localhost:8083/api/v1/push/stats/push
```

### 4. 保存设备Token
```bash
curl -X POST "http://localhost:8083/api/v1/push/users/test001/tokens?platformID=1&deviceToken=test_token_123"
```

### 5. 检查用户在线状态
```bash
curl http://localhost:8083/api/v1/push/users/test001/online
```

### 6. 获取用户设备Token
```bash
curl http://localhost:8083/api/v1/push/users/test001/tokens
```

## 📊 监控指标

### 推送统计
- **总推送数** - 处理的推送总数
- **成功推送数** - 成功推送的数量
- **失败推送数** - 推送失败的数量
- **成功率** - 推送成功率
- **提供商分布** - 各推送提供商的使用统计
- **平台分布** - 各平台的推送分布

### 实时统计
- **每秒推送数** - 实时推送处理速率
- **每分钟推送数** - 分钟级推送处理量
- **每小时推送数** - 小时级推送处理量
- **平均响应时间** - 推送处理平均耗时
- **当前成功率** - 实时推送成功率

### 设备Token统计
- **总Token数** - 当前设备Token总数
- **活跃Token数** - 活跃的设备Token数量
- **过期Token数** - 过期的设备Token数量
- **平台分布** - 各平台的Token分布

## 🔧 推送提供商配置

### APNs (Apple Push Notification)
```yaml
cheese:
  im:
    push:
      apns:
        enabled: true
        key-path: /path/to/AuthKey_XXXXXXXXXX.p8
        key-id: XXXXXXXXXX
        team-id: XXXXXXXXXX
        bundle-id: com.example.app
        production: true
```

### 极光推送 (JPush)
```yaml
cheese:
  im:
    push:
      jpush:
        enabled: true
        app-key: your-app-key
        master-secret: your-master-secret
        production: true
        time-to-live: 86400
```

### FCM (Firebase Cloud Messaging)
```yaml
cheese:
  im:
    push:
      fcm:
        enabled: true
        service-account-key: /path/to/service-account-key.json
        project-id: your-project-id
```

## 🔗 相关模块

- **postbox** - 消息服务，提供RPC接口和消息存储
- **postoffice** - 网关服务，提供WebSocket连接和在线推送
- **postman** - 消息传输服务，负责消息路由和分发
- **common** - 公共模块，提供实体类和常量定义

## 🧪 运行测试

### 单元测试
```bash
# 运行所有测试
./gradlew :push:test

# 运行特定测试类
./gradlew :push:test --tests PushServiceTest

# 运行测试并生成报告
./gradlew :push:test jacocoTestReport
```

### 集成测试
```bash
# 启动测试环境
docker-compose -f docker-compose.test.yml up -d

# 运行集成测试
./gradlew :push:integrationTest

# 清理测试环境
docker-compose -f docker-compose.test.yml down
```

## 🔧 故障排查

### 常见问题

#### 1. 推送服务无法启动
```bash
# 检查依赖服务
curl http://localhost:6379  # Redis
curl http://localhost:9092  # Kafka
curl http://localhost:8848  # Nacos

# 查看日志
tail -f logs/push.log
```

#### 2. APNs推送失败
- 检查证书文件路径和权限
- 验证Bundle ID和Team ID
- 确认生产/开发环境配置

#### 3. FCM推送失败
- 检查服务账号密钥文件
- 验证项目ID配置
- 确认设备Token有效性

#### 4. 极光推送失败
- 检查App Key和Master Secret
- 验证推送证书配置
- 确认推送环境设置

### 性能优化

#### 1. 推送吞吐量优化
```yaml
cheese:
  im:
    push:
      routing:
        batch-size: 500  # 增加批处理大小
        online-timeout: 3000  # 调整超时时间
```

#### 2. Redis连接优化
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 10
```

#### 3. Kafka消费优化
```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500
      fetch-min-size: 1024
    listener:
      concurrency: 5
```

## 📝 开发指南

### 添加新的推送提供商

1. **实现PushProviderService接口**
```java
@Service
public class CustomPushProviderService implements PushProviderService {
    @Override
    public PushService.PushResult sendPush(PushMessage pushMessage) {
        // 实现推送逻辑
    }

    @Override
    public String getProviderName() {
        return "CustomPush";
    }

    @Override
    public List<Integer> getSupportedPlatforms() {
        return Arrays.asList(1, 2); // 支持的平台
    }

    // 其他必需方法...
}
```

2. **添加配置**
```yaml
cheese:
  im:
    push:
      custom:
        enabled: true
        api-key: your-api-key
        api-secret: your-api-secret
```

3. **注册到Spring容器**
```java
@Configuration
public class PushProviderConfig {
    @Bean
    @ConditionalOnProperty(name = "cheese.im.push.custom.enabled", havingValue = "true")
    public CustomPushProviderService customPushProviderService() {
        return new CustomPushProviderService();
    }
}
```

### 扩展推送模板

1. **创建自定义模板**
```java
public class CustomPushTemplate extends PushTemplate {
    public CustomPushTemplate() {
        setTemplateID("custom_template");
        setTemplateName("自定义模板");
        setTitleTemplate("{customTitle}");
        setContentTemplate("{customContent}");
    }
}
```

2. **实现模板渲染器**
```java
@Component
public class CustomTemplateRenderer {
    public String render(String template, Map<String, Object> variables) {
        // 实现自定义模板渲染逻辑
        return template;
    }
}
```

### 自定义推送策略

1. **扩展路由策略**
```java
@Service
public class CustomPushRoutingService extends PushRoutingServiceImpl {
    @Override
    public PushRoutingResult routePushMessage(Message message, List<String> targetUsers) {
        // 实现自定义路由逻辑
        if (isVipMessage(message)) {
            return routeVipMessage(message, targetUsers);
        }
        return super.routePushMessage(message, targetUsers);
    }
}
```

2. **配置策略参数**
```yaml
cheese:
  im:
    push:
      routing:
        custom-strategy: vip-first
        vip-timeout: 1000
        normal-timeout: 5000
```

### 监控和告警

1. **自定义指标**
```java
@Component
public class PushMetrics {
    private final MeterRegistry meterRegistry;
    private final Counter pushCounter;

    public PushMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.pushCounter = Counter.builder("push.total")
                .description("Total push count")
                .register(meterRegistry);
    }

    public void incrementPushCount(String provider, String platform, boolean success) {
        pushCounter.increment(
            Tags.of(
                "provider", provider,
                "platform", platform,
                "success", String.valueOf(success)
            )
        );
    }
}
```

2. **健康检查**
```java
@Component
public class PushHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 检查推送服务健康状态
        if (isPushServiceHealthy()) {
            return Health.up()
                    .withDetail("providers", getAvailableProviders())
                    .build();
        } else {
            return Health.down()
                    .withDetail("error", "Push service unavailable")
                    .build();
        }
    }
}
