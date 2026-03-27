# 枚举与常量规范

## 总体要求
- 所有业务状态、类型、来源、开关优先使用枚举
- 禁止散落 `public static final String`
- 禁止散落 `public static final Integer`
- 禁止直接使用魔法值

## 枚举定义要求
每个枚举必须包含：
1. `code`
2. `desc`
3. `fromCode`

## 示例

```java
/**
 * 用户状态枚举
 */
public enum UserStatusEnum {

    INIT("INIT", "初始化"),
    ENABLED("ENABLED", "已启用"),
    DISABLED("DISABLED", "已禁用");

    private final String code;
    private final String desc;

    UserStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static UserStatusEnum fromCode(String code) {
        for (UserStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法用户状态编码");
    }
}
```
