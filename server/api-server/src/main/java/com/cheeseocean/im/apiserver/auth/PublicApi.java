package com.cheeseocean.im.apiserver.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明无需 access token 的 HTTP API。
 *
 * <p>未标注的 `/api/**` Controller 方法默认必须通过统一会话鉴权。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
