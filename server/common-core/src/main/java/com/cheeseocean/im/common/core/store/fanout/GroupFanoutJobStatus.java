package com.cheeseocean.im.common.core.store.fanout;

import java.util.Arrays;

/**
 * 群扩散任务持久化状态。
 */
public enum GroupFanoutJobStatus {

    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成");

    private final int code;
    private final String desc;

    GroupFanoutJobStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static GroupFanoutJobStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown group fanout job status code: " + code));
    }
}
