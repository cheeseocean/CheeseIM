package com.cheeseocean.im.apiserver.exception;

/**
 * 当前主体已认证，但无权操作请求中指定的资源。
 */
public class ApiAuthorizationException extends RuntimeException {

    public ApiAuthorizationException(String message) {
        super(message);
    }
}
