package com.cheeseocean.im.common.api.dto.user;

/**
 * 用户注册请求。
 * 由管理员接口或服务内部批量调用。
 */
public class RegisterUserRequest {

    /** 用户 ID，不能为空且不能包含冒号 */
    private String userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String faceUrl;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /**
     * 管理员级别，普通注册传 0。
     * 通知账号注册传对应级别 code。
     */
    private int appManagerLevel;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public int getAppManagerLevel() { return appManagerLevel; }
    public void setAppManagerLevel(int appManagerLevel) { this.appManagerLevel = appManagerLevel; }
}
