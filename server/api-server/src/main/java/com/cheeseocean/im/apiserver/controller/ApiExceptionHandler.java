package com.cheeseocean.im.apiserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * API Server 统一异常封装。
 *
 * <p>当前仍需保持不同 HTTP 接口原有的错误码语义，因此这里按请求路径回填
 * 既有的 status/code 组合，先完成 controller 层去重，再视后续情况继续收敛异常模型。
 */
@RestControllerAdvice(assignableTypes = {
        AuthController.class,
        WsTicketController.class,
        FriendController.class,
        BlacklistController.class,
        UserSettingsController.class
})
public class ApiExceptionHandler {

    /**
     * 处理需要保留既有业务错误码的非法状态异常。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e,
                                                                  HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", 40001, "message", e.getMessage()));
        }
        if (path.startsWith("/api/im/ws-ticket")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 40101, "message", e.getMessage()));
        }
        if (path.startsWith("/api/im/user/settings")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 40100, "message", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", 40002, "message", e.getMessage()));
    }

    /**
     * 处理黑名单接口的参数非法错误。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", 40001, "message", e.getMessage()));
    }
}
