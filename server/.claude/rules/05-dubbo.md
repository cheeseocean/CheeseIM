# Dubbo 开发规范

## 命名规范
- Dubbo 服务接口命名禁止带 `Dubbo`
- Dubbo 服务实现类命名禁止带 `Dubbo`
- Dubbo 消费方引用对象命名禁止带 `Dubbo`

## 正确示例

```java
public interface UserService {
}
```

```java
@DubboService
public class UserServiceImpl implements UserService {
}
```

## 错误示例

```java
public interface UserDubboService {
}
```

```java
public class UserDubboServiceImpl {
}
```

## 实现要求
- Dubbo provider 实现位于 `service` 模块
- 接口定义位于 `api` 模块
- 使用 `@DubboService` 暴露服务
- 服务实现负责参数校验与流程编排
