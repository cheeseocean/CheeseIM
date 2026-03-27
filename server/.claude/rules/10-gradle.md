# Gradle 多模块规范

## settings.gradle 要求

```groovy
rootProject.name = 'CheeseIMServer'

include 'bootstrap-all'
include 'common-api'
include 'common-core'
include 'postoffice'
include 'postmaster'
include 'postbox'
include 'postman'
```

## 根 build.gradle 要求
- 统一插件版本
- 统一依赖版本
- 统一 Java 版本为 17
- 统一仓库配置
- 统一测试配置

## 依赖关系约束
- `bootstrap-all -> postoffice, postmaster, postman, postbox, common-api, common-core`
- `postoffice -> common-api, common-core, postbox`
- `postmaster -> common-api, common-core, social`
- `postman -> common-api, common-core`
- `postbox -> common-api, common-core`

## 禁止事项
- `common` 依赖业务模块
