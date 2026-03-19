package com.cheeseocean.im.push.exception;

/**
 * 推送异常基类
 * 
 * @author CheeseIM
 */
public class PushException extends RuntimeException {
    
    private String errorCode;
    private String provider;
    
    public PushException(String message) {
        super(message);
    }
    
    public PushException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PushException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public PushException(String errorCode, String message, String provider) {
        super(message);
        this.errorCode = errorCode;
        this.provider = provider;
    }
    
    public PushException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public PushException(String errorCode, String message, String provider, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.provider = provider;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        
        if (errorCode != null) {
            sb.append("[").append(errorCode).append("]");
        }
        
        if (provider != null) {
            sb.append("[").append(provider).append("]");
        }
        
        sb.append(": ").append(getMessage());
        
        return sb.toString();
    }
}

/**
 * 推送配置异常
 */
class PushConfigException extends PushException {
    
    public PushConfigException(String message) {
        super("CONFIG_ERROR", message);
    }
    
    public PushConfigException(String message, String provider) {
        super("CONFIG_ERROR", message, provider);
    }
    
    public PushConfigException(String message, Throwable cause) {
        super("CONFIG_ERROR", message, cause);
    }
}

/**
 * 推送连接异常
 */
class PushConnectionException extends PushException {
    
    public PushConnectionException(String message) {
        super("CONNECTION_ERROR", message);
    }
    
    public PushConnectionException(String message, String provider) {
        super("CONNECTION_ERROR", message, provider);
    }
    
    public PushConnectionException(String message, Throwable cause) {
        super("CONNECTION_ERROR", message, cause);
    }
    
    public PushConnectionException(String message, String provider, Throwable cause) {
        super("CONNECTION_ERROR", message, provider, cause);
    }
}

/**
 * 推送认证异常
 */
class PushAuthException extends PushException {
    
    public PushAuthException(String message) {
        super("AUTH_ERROR", message);
    }
    
    public PushAuthException(String message, String provider) {
        super("AUTH_ERROR", message, provider);
    }
    
    public PushAuthException(String message, Throwable cause) {
        super("AUTH_ERROR", message, cause);
    }
}

/**
 * 推送限流异常
 */
class PushRateLimitException extends PushException {
    
    public PushRateLimitException(String message) {
        super("RATE_LIMIT", message);
    }
    
    public PushRateLimitException(String message, String provider) {
        super("RATE_LIMIT", message, provider);
    }
}

/**
 * 设备Token无效异常
 */
class InvalidDeviceTokenException extends PushException {
    
    public InvalidDeviceTokenException(String message) {
        super("INVALID_TOKEN", message);
    }
    
    public InvalidDeviceTokenException(String message, String provider) {
        super("INVALID_TOKEN", message, provider);
    }
}
