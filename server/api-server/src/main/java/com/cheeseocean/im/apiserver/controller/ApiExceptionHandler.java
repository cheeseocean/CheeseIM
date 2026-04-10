package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * API Server 统一异常封装。
 *
 * <p>当前仍需保持不同 HTTP 接口原有的错误码语义，因此这里按请求路径回填
 * 既有的 status/code 组合，先完成 controller 层去重，再视后续情况继续收敛异常模型。
 */
@RestControllerAdvice(basePackages = "com.cheeseocean.im.apiserver.controller")
public class ApiExceptionHandler {

    private static final Logger log = CommonLoggers.POSTBOX;

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

    /**
     * 处理请求体校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e,
                                                                         HttpServletRequest request) {
        log.warn("参数验证失败: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "VALIDATION_ERROR");
        error.put("errorMessage", "参数验证失败");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        error.put("fieldErrors", fieldErrors(e.getBindingResult().getFieldErrors()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 处理 query/form 参数绑定异常。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("参数绑定失败: {}", e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "BIND_ERROR");
        error.put("errorMessage", "参数绑定失败");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        error.put("fieldErrors", fieldErrors(e.getBindingResult().getFieldErrors()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 处理参数类型不匹配异常。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException e,
                                                                           HttpServletRequest request) {
        String requiredType = e.getRequiredType() == null ? "unknown" : e.getRequiredType().getSimpleName();
        log.warn("参数类型不匹配: parameter={}, value={}, requiredType={}", e.getName(), e.getValue(), requiredType);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "TYPE_MISMATCH");
        error.put("errorMessage", String.format("参数 '%s' 类型不匹配，期望类型: %s", e.getName(), requiredType));
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 处理空指针异常。
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException e,
                                                                          HttpServletRequest request) {
        log.error("空指针异常: {}", request.getRequestURI(), e);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "NULL_POINTER");
        error.put("errorMessage", "系统内部错误");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * 处理运行时异常。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e,
                                                                      HttpServletRequest request) {
        log.error("运行时异常: {}", request.getRequestURI(), e);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "RUNTIME_ERROR");
        error.put("errorMessage", "系统运行时错误: " + e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * 处理兜底异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常: {}", request.getRequestURI(), e);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "UNKNOWN_ERROR");
        error.put("errorMessage", "系统未知错误");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private Map<String, String> fieldErrors(Iterable<FieldError> fieldErrors) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : fieldErrors) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return errors;
    }
}
