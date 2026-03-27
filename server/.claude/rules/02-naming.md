# 命名规范

## 通用命名
- DTO 以 `DTO` 结尾
- 请求对象以 `Request` 结尾
- 响应对象以 `Response` 结尾
- 枚举以 `Enum` 结尾
- 仓储接口以 `Repository` 结尾
- 转换器以 `Converter` 结尾
- Mongo 持久化对象以 `Document` 结尾
- 异常类以 `Exception` 结尾

## 禁止命名
- `UserDubboService`
- `UserRpcService`
- `doIt`
- `process`
- `handle`
- `test`

## 推荐命名
- `UserService`
- `UserServiceImpl`
- `UserRepository`
- `UserDocument`
- `UserConverter`
- `CreateUserRequest`
- `UserDTO`
