package com.cheeseocean.im.common.api.dto.user;

/**
 * 用户信息更新请求。
 * 所有字段均为可选，null 表示不修改该字段（可选字段更新语义）。
 */
public class UpdateUserInfoRequest {

    /** 新昵称，null 表示不修改 */
    private String nickname;

    /** 新头像 URL，null 表示不修改 */
    private String faceUrl;

    /** 扩展字段，null 表示不修改 */
    private String ex;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }
}
