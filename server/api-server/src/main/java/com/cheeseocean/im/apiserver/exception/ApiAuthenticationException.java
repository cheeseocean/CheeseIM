package com.cheeseocean.im.apiserver.exception;

/**
 * HTTP access token 缺失、无效或对应 session 已失效。
 */
public class ApiAuthenticationException extends RuntimeException {

    public ApiAuthenticationException(String message) {
        super(message);
    }
}
