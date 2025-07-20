package com.cheeseocean.im.business.conversation.api.param;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * create group chat request
 *
 * @author xxxcrel
 * @date 2025/7/19 20:18
 */
public class CreateGroupChatReq implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * group id
     */
    @JsonProperty("groupID")
    private String groupID;

    /**
     * user ids
     */
    @JsonProperty("userIDs")
    private List<String> userIDs;

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public List<String> getUserIDs() {
        return userIDs;
    }

    public void setUserIDs(List<String> userIDs) {
        this.userIDs = userIDs;
    }
}
