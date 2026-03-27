# MongoDB 持久层规范

## 基本要求
- MongoDB 持久化实现位于 `infra` 模块
- `Document` 与 `Domain` 必须分离
- 必须通过 `Converter` 进行转换
- 不允许将 Document 直接暴露给上层

## Document 规范
- 持久化对象以 `Document` 结尾
- 使用 `@Document` 标注集合
- 枚举在 Mongo 中优先保存 `code`

## Repository 实现规范
- 通过 Spring Data MongoDB 定义 `MongoRepository`
- 通过 `RepositoryImpl` 实现 `domain` 中的仓储抽象
- 所有领域对象和文档对象转换必须集中处理

## Converter 示例说明
- `toDomain`：将文档对象转换为领域对象
- `toDocument`：将领域对象转换为文档对象
