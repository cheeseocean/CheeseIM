# 领域层规范

## 领域层包含内容
- 领域实体
- 值对象
- 领域服务
- 枚举
- Repository 抽象接口

## 强制要求
1. `domain` 层不得依赖 Spring Data MongoDB
2. `domain` 层不得依赖 Document
3. 领域对象要表达业务语义，而非数据库结构
4. 领域层优先使用枚举表达状态

## Repository 抽象示例

```java
/**
 * 用户仓储接口
 * 定义用户领域对象的持久化抽象
 */
public interface UserRepository {

    /**
     * 根据用户ID查询用户
     */
    Optional<User> findById(Long userId);

    /**
     * 保存用户
     */
    User save(User user);
}
```
