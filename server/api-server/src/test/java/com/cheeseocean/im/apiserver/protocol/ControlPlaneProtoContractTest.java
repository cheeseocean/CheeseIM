package com.cheeseocean.im.apiserver.protocol;

import com.cheeseocean.im.apiserver.controller.GroupController;
import com.cheeseocean.im.apiserver.model.response.ConversationIncrementalSyncResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationResponse;
import com.cheeseocean.im.apiserver.model.response.FriendRequestResponse;
import com.cheeseocean.im.apiserver.model.response.FriendshipResponse;
import com.cheeseocean.im.apiserver.model.request.HandleFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.request.SendFriendRequestRequest;
import com.cheeseocean.im.common.api.protocol.proto.ProtoConversation;
import com.cheeseocean.im.common.api.protocol.proto.ProtoConversationSyncResult;
import com.cheeseocean.im.common.api.protocol.proto.ProtoFriend;
import com.cheeseocean.im.common.api.protocol.proto.ProtoFriendRequest;
import com.cheeseocean.im.common.api.protocol.proto.ProtoGroupSummary;
import com.cheeseocean.im.common.api.protocol.proto.ProtoHandleFriendRequestCommand;
import com.cheeseocean.im.common.api.protocol.proto.ProtoSendFriendRequestCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 防止 HTTP JSON 字段悄悄偏离跨语言 Protobuf 控制面契约。
 *
 * @author wxc
 */
class ControlPlaneProtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void responseFieldsMatchProtoJsonNames() {
        assertContract(FriendshipResponse.class, ProtoFriend.getDescriptor());
        assertContract(FriendRequestResponse.class, ProtoFriendRequest.getDescriptor());
        assertContract(GroupController.GroupSummaryResponse.class, ProtoGroupSummary.getDescriptor());
        assertContract(ConversationResponse.class, ProtoConversation.getDescriptor());
        assertContract(ConversationIncrementalSyncResponse.class, ProtoConversationSyncResult.getDescriptor());
        assertContract(SendFriendRequestRequest.class, ProtoSendFriendRequestCommand.getDescriptor());
        assertContract(HandleFriendRequestRequest.class, ProtoHandleFriendRequestCommand.getDescriptor());
    }

    private void assertContract(Class<?> responseType, Descriptors.Descriptor descriptor) {
        Set<String> responseFields = objectMapper.getSerializationConfig().introspect(
                        objectMapper.constructType(responseType)).findProperties().stream()
                .map(property -> property.getName())
                .collect(Collectors.toSet());
        Set<String> protoFields = descriptor.getFields().stream()
                .map(Descriptors.FieldDescriptor::getJsonName)
                .collect(Collectors.toSet());
        assertEquals(protoFields, responseFields, responseType.getSimpleName());
    }
}
