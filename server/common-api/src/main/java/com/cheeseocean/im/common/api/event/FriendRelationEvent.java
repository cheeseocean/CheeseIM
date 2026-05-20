package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.enums.ContentType;
import lombok.Data;

import java.io.Serializable;

@Data
public class FriendRelationEvent implements Serializable {

    private String      recipientUserId;
    private String      actorUserId;
    private String      peerUserId;
    private Long        occurredAt;
}
