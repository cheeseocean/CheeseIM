# 代码生成输出要求

当生成代码时，必须输出完整且可用的项目骨架，至少包括：

1. `settings.gradle`
2. 根 `build.gradle`
3. 各子模块 `build.gradle`
4. 启动类
5. `application.yml`
6. 一套完整 `User` 示例链路

## User 示例必须覆盖
- `UserService`
- `UserServiceImpl`
- `UserDTO`
- `CreateUserRequest`
- `User`
- `UserStatusEnum`
- `UserRepository`
- `UserDocument`
- `UserMongoRepository`
- `UserRepositoryImpl`
- `UserConverter`
- `BusinessException`

## 输出风格要求
- 保持企业级代码风格
- 优先可读性和可维护性
- 不省略关键文件
- 代码可直接作为脚手架扩展
