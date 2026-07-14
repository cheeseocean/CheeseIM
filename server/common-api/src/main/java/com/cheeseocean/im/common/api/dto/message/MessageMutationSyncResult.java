package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 撤回 mutation 的会话增量同步结果。
 */
@Data
public class MessageMutationSyncResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String errorCode;
    private String errorMessage;
    private List<MessageMutationResult> mutations = new ArrayList<>();
    private long nextCreatedAt;
    private String nextMutationId;
    private boolean hasMore;

    /** 创建失败结果，调用端可按业务错误处理。 */
    public static MessageMutationSyncResult rejected(String code, String message) {
        MessageMutationSyncResult result = new MessageMutationSyncResult();
        result.setSuccess(false);
        result.setErrorCode(code);
        result.setErrorMessage(message);
        return result;
    }
}
