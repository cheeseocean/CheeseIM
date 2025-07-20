package com.cheeseocean.im.push.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 推送消息验证器
 * 验证推送消息的合法性和完整性
 * 
 * @author CheeseIM
 */
public class PushMessageValidator {
    
    /**
     * 设备Token格式验证正则表达式
     */
    private static final Pattern IOS_TOKEN_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Pattern ANDROID_TOKEN_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    
    /**
     * 内容长度限制
     */
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_SOUND_LENGTH = 50;
    private static final int MAX_CATEGORY_LENGTH = 50;
    
    /**
     * 验证推送消息
     * 
     * @param pushMessage 推送消息
     * @return 验证结果
     */
    public static ValidationResult validate(PushMessage pushMessage) {
        ValidationResult result = new ValidationResult();
        
        if (pushMessage == null) {
            result.addError("推送消息不能为空");
            return result;
        }
        
        // 验证必填字段
        validateRequiredFields(pushMessage, result);
        
        // 验证字段格式
        validateFieldFormats(pushMessage, result);
        
        // 验证字段长度
        validateFieldLengths(pushMessage, result);
        
        // 验证业务逻辑
        validateBusinessLogic(pushMessage, result);
        
        return result;
    }
    
    /**
     * 验证必填字段
     */
    private static void validateRequiredFields(PushMessage pushMessage, ValidationResult result) {
        if (pushMessage.getUserID() == null || pushMessage.getUserID().trim().isEmpty()) {
            result.addError("用户ID不能为空");
        }
        
        if (pushMessage.getPlatformID() == null || pushMessage.getPlatformID() <= 0) {
            result.addError("平台ID无效");
        }
        
        if (pushMessage.getDeviceToken() == null || pushMessage.getDeviceToken().trim().isEmpty()) {
            result.addError("设备Token不能为空");
        }
        
        if ((pushMessage.getTitle() == null || pushMessage.getTitle().trim().isEmpty()) &&
            (pushMessage.getContent() == null || pushMessage.getContent().trim().isEmpty())) {
            result.addError("推送标题和内容不能同时为空");
        }
    }
    
    /**
     * 验证字段格式
     */
    private static void validateFieldFormats(PushMessage pushMessage, ValidationResult result) {
        // 验证平台ID
        Integer platformID = pushMessage.getPlatformID();
        if (platformID != null && (platformID < 1 || platformID > 5)) {
            result.addError("平台ID必须在1-5之间");
        }
        
        // 验证设备Token格式
        String deviceToken = pushMessage.getDeviceToken();
        if (deviceToken != null && !deviceToken.trim().isEmpty()) {
            if (platformID != null) {
                switch (platformID) {
                    case 1: // iOS
                        if (!IOS_TOKEN_PATTERN.matcher(deviceToken).matches()) {
                            result.addError("iOS设备Token格式无效");
                        }
                        break;
                    case 2: // Android
                        if (!ANDROID_TOKEN_PATTERN.matcher(deviceToken).matches()) {
                            result.addError("Android设备Token格式无效");
                        }
                        break;
                    // 其他平台的Token格式验证可以根据需要添加
                }
            }
        }
        
        // 验证优先级
        Integer priority = pushMessage.getPriority();
        if (priority != null && (priority < 0 || priority > 2)) {
            result.addError("优先级必须在0-2之间");
        }
        
        // 验证推送类型
        Integer pushType = pushMessage.getPushType();
        if (pushType != null && pushType <= 0) {
            result.addError("推送类型必须大于0");
        }
        
        // 验证角标
        Integer badge = pushMessage.getBadge();
        if (badge != null && badge < 0) {
            result.addError("角标数量不能为负数");
        }
    }
    
    /**
     * 验证字段长度
     */
    private static void validateFieldLengths(PushMessage pushMessage, ValidationResult result) {
        if (pushMessage.getTitle() != null && pushMessage.getTitle().length() > MAX_TITLE_LENGTH) {
            result.addError("推送标题长度不能超过" + MAX_TITLE_LENGTH + "个字符");
        }
        
        if (pushMessage.getContent() != null && pushMessage.getContent().length() > MAX_CONTENT_LENGTH) {
            result.addError("推送内容长度不能超过" + MAX_CONTENT_LENGTH + "个字符");
        }
        
        if (pushMessage.getSound() != null && pushMessage.getSound().length() > MAX_SOUND_LENGTH) {
            result.addError("声音文件名长度不能超过" + MAX_SOUND_LENGTH + "个字符");
        }
        
        if (pushMessage.getCategory() != null && pushMessage.getCategory().length() > MAX_CATEGORY_LENGTH) {
            result.addError("分类名称长度不能超过" + MAX_CATEGORY_LENGTH + "个字符");
        }
    }
    
    /**
     * 验证业务逻辑
     */
    private static void validateBusinessLogic(PushMessage pushMessage, ValidationResult result) {
        // 验证过期时间
        Long expireTime = pushMessage.getExpireTime();
        if (expireTime != null && expireTime <= System.currentTimeMillis()) {
            result.addError("推送消息已过期");
        }
        
        // iOS特定验证
        if (pushMessage.getPlatformID() != null && pushMessage.getPlatformID() == 1) {
            // iOS角标验证
            Integer badge = pushMessage.getBadge();
            if (badge != null && badge > 99999) {
                result.addWarning("iOS角标数量过大，可能显示异常");
            }
            
            // iOS分类验证
            String category = pushMessage.getCategory();
            if (category != null && !category.matches("^[a-zA-Z0-9_.-]+$")) {
                result.addError("iOS分类名称只能包含字母、数字、下划线、点和连字符");
            }
        }
        
        // Android特定验证
        if (pushMessage.getPlatformID() != null && pushMessage.getPlatformID() == 2) {
            // Android不支持角标
            if (pushMessage.getBadge() != null && pushMessage.getBadge() > 0) {
                result.addWarning("Android平台不支持角标功能");
            }
        }
        
        // 验证扩展数据
        if (pushMessage.getExtras() != null && pushMessage.getExtras().size() > 20) {
            result.addWarning("扩展数据项过多，可能影响推送性能");
        }
    }
    
    /**
     * 快速验证（只验证必填字段）
     * 
     * @param pushMessage 推送消息
     * @return 是否通过验证
     */
    public static boolean quickValidate(PushMessage pushMessage) {
        if (pushMessage == null) {
            return false;
        }
        
        // 检查必填字段
        if (pushMessage.getUserID() == null || pushMessage.getUserID().trim().isEmpty()) {
            return false;
        }
        
        if (pushMessage.getPlatformID() == null || pushMessage.getPlatformID() <= 0) {
            return false;
        }
        
        if (pushMessage.getDeviceToken() == null || pushMessage.getDeviceToken().trim().isEmpty()) {
            return false;
        }
        
        if ((pushMessage.getTitle() == null || pushMessage.getTitle().trim().isEmpty()) &&
            (pushMessage.getContent() == null || pushMessage.getContent().trim().isEmpty())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return null;
            }
            return String.join("; ", errors);
        }
        
        public String getWarningMessage() {
            if (warnings.isEmpty()) {
                return null;
            }
            return String.join("; ", warnings);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{");
            sb.append("valid=").append(isValid());
            
            if (!errors.isEmpty()) {
                sb.append(", errors=").append(errors);
            }
            
            if (!warnings.isEmpty()) {
                sb.append(", warnings=").append(warnings);
            }
            
            sb.append("}");
            return sb.toString();
        }
    }
}
