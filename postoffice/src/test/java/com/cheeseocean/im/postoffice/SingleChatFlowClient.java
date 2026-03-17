package com.cheeseocean.im.postoffice;

import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryCommand;

final class SingleChatFlowClient {

    private SingleChatFlowClient() {
    }

    static DeliveryCommand command(String clientMsgId, String content) {
        return DeliveryCommand.builder()
                .clientMsgId(clientMsgId)
                .conversationId("single:userA:userB")
                .senderId("userA")
                .receiverId("userB")
                .deviceId("ios-a")
                .content(content)
                .contentType(101)
                .sessionType(1)
                .build();
    }

    static DeliveryAck read(String serverMsgId) {
        return DeliveryAck.read(serverMsgId, "single:userA:userB", "userB", "ios-b", System.currentTimeMillis());
    }
}
