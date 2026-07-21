package com.cheeseocean.im.common.api.exception;

import com.cheeseocean.im.common.api.enums.ErrorCode;

/**
 * 可跨模块传递的业务异常。
 *
 * <p>异常只携带稳定错误码和对外描述，不向 HTTP/RPC 调用方泄露底层技术异常。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDesc());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
