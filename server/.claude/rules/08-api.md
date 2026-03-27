# API 层规范

## API 层允许内容
- Dubbo 对外服务接口
- DTO
- Request
- Response

## API 层禁止内容
- 具体业务实现
- 数据库访问代码
- Mongo Repository
- Document 对象

## 接口规范
- 接口命名语义清晰
- 方法名使用动词开头
- 公共方法必须有中文注释
- DTO 不暴露数据库实现细节
