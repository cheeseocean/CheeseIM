package com.cheeseocean.im.common.api;

import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.entity.InboxMessage;
import com.cheeseocean.im.common.entity.StoredMessage;

import java.util.List;

public interface MessageStoreService {

    StoredMessage saveMessage(StoredMessage message);

    long saveOfflineMessage(MessageProto message);

    List<Long> saveOfflineMessages(MessageProto message, List<String> receiverIds);

    List<InboxMessage> getOfflineMessages(String userId, int limit);

    void markDelivered(String userId, String serverMsgId);

    DeliveryResult applyAck(DeliveryAck ack);
}
