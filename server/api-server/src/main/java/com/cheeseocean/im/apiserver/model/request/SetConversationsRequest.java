package com.cheeseocean.im.apiserver.model.request;

import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import lombok.Data;

@Data
public class SetConversationsRequest {
    private SetConversationRequest payload;
}
