package com.cheeseocean.im.postoffice.auth;

/**
 * 认证服务接口
 * 
 * @author CheeseIM
 */
public interface AuthService {
    
    /**
     * 验证JWT Token
     * 
     * @param token JWT Token
     * @return 认证结果
     */
    AuthResult validateToken(String token);
    
    /**
     * 生成JWT Token
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @return JWT Token
     */
    String generateToken(String userID, Integer platformID);
    
    /**
     * 刷新Token
     * 
     * @param token 旧Token
     * @return 新Token
     */
    String refreshToken(String token);
    
    /**
     * 认证结果类
     */
    class AuthResult {
        private boolean success;
        private String userID;
        private Integer platformID;
        private String errorMessage;
        private long expireTime;
        
        public AuthResult() {}
        
        public AuthResult(boolean success, String userID, Integer platformID) {
            this.success = success;
            this.userID = userID;
            this.platformID = platformID;
        }
        
        public static AuthResult success(String userID, Integer platformID, long expireTime) {
            AuthResult result = new AuthResult(true, userID, platformID);
            result.setExpireTime(expireTime);
            return result;
        }
        
        public static AuthResult failure(String errorMessage) {
            AuthResult result = new AuthResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }
        
        // Getter and Setter
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getUserID() {
            return userID;
        }
        
        public void setUserID(String userID) {
            this.userID = userID;
        }
        
        public Integer getPlatformID() {
            return platformID;
        }
        
        public void setPlatformID(Integer platformID) {
            this.platformID = platformID;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
        
        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }
        
        @Override
        public String toString() {
            return "AuthResult{" +
                    "success=" + success +
                    ", userID='" + userID + '\'' +
                    ", platformID=" + platformID +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", expireTime=" + expireTime +
                    '}';
        }
    }
}
