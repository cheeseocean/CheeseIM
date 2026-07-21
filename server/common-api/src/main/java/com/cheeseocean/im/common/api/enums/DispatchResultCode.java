package com.cheeseocean.im.common.api.enums;

/**
 * 单连接在线投递结果码。
 *
 * <p>code 会跨 Dubbo/节点队列边界传递，必须保持稳定。</p>
 */
public enum DispatchResultCode {

    OK("OK", "写入并提交成功"),
    DUPLICATE("DUPLICATE", "已完成的重复投递"),
    CONNECTION_NOT_FOUND("CONNECTION_NOT_FOUND", "连接不存在或已迁移"),
    DELIVERY_IN_PROGRESS("DELIVERY_IN_PROGRESS", "同一投递正在处理"),
    DEDUP_UNAVAILABLE("DEDUP_UNAVAILABLE", "去重存储不可用"),
    DEDUP_COMMIT_FAILED("DEDUP_COMMIT_FAILED", "写入成功但去重提交失败"),
    SEND_FAILED("SEND_FAILED", "连接写入失败"),
    WRITE_PENDING("WRITE_PENDING", "连接写入未在响应期限内完成");

    private final String code;
    private final String desc;

    DispatchResultCode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DispatchResultCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DispatchResultCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
