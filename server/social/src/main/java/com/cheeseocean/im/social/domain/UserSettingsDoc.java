package com.cheeseocean.im.social.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-user settings stored in MongoDB.
 * _id = userId for O(1) lookup.
 */
@Document("user_settings")
public class UserSettingsDoc {

    @Id
    private String userId;

    /** 取值见 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt}。 */
    private int globalRecvMsgOpt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getGlobalRecvMsgOpt() { return globalRecvMsgOpt; }
    public void setGlobalRecvMsgOpt(int globalRecvMsgOpt) { this.globalRecvMsgOpt = globalRecvMsgOpt; }
}
