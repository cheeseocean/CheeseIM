package com.cheeseocean.im.bootstrap.all;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postman.exception.PushException;
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
 * 全局异常处理器
 * 统一处理推送服务中的异常
 * 
 * @author xxxcrel
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    /**
     * 处理推送异常
     */
    @ExceptionHandler(PushException.class)
    public ResponseEntity<Map<String, Object>> handlePushException(PushException e, HttpServletRequest request) {
        logger.error("推送异常: {}", e.toString(), e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", e.getErrorCode() != null ? e.getErrorCode() : "PUSH_ERROR");
        error.put("errorMessage", e.getMessage());
        error.put("provider", e.getProvider());
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        logger.warn("参数验证失败: {}", e.getMessage());
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "VALIDATION_ERROR");
        error.put("errorMessage", "参数验证失败");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        // 收集验证错误详情
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        error.put("fieldErrors", fieldErrors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException e, HttpServletRequest request) {
        logger.warn("参数绑定失败: {}", e.getMessage());
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "BIND_ERROR");
        error.put("errorMessage", "参数绑定失败");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        // 收集绑定错误详情
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        error.put("fieldErrors", fieldErrors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logger.warn("参数类型不匹配: parameter={}, value={}, requiredType={}", 
                   e.getName(), e.getValue(), e.getRequiredType().getSimpleName());
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "TYPE_MISMATCH");
        error.put("errorMessage", String.format("参数 '%s' 类型不匹配，期望类型: %s", 
                  e.getName(), e.getRequiredType().getSimpleName()));
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("非法参数异常: {}", e.getMessage());
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "ILLEGAL_ARGUMENT");
        error.put("errorMessage", e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        logger.error("空指针异常: {}", request.getRequestURI(), e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "NULL_POINTER");
        error.put("errorMessage", "系统内部错误");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logger.error("运行时异常: {}", request.getRequestURI(), e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "RUNTIME_ERROR");
        error.put("errorMessage", "系统运行时错误: " + e.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
        logger.error("未知异常: {}", request.getRequestURI(), e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("errorCode", "UNKNOWN_ERROR");
        error.put("errorMessage", "系统未知错误");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
