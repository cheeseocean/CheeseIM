package com.cheeseocean.im.business.service.permission;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionResult;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionService;
import com.cheeseocean.im.common.api.user.UserInfoService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 消息发送权限聚合服务实现。
 *
 * <p>把发送热路径原本分散在 postbox 的三次远程调用收敛到 business 本地服务调用，
 * 保留各领域服务已有缓存语义，同时让 postbox 只承担一次 Dubbo RTT。
 */
@Service
@DubboService
public class MessageSendPermissionServiceImpl implements MessageSendPermissionService {

    private final FriendRelationService friendRelationService;
    private final UserInfoService userInfoService;
    private final ConversationService conversationService;

    public MessageSendPermissionServiceImpl(FriendRelationService friendRelationService,
                                            UserInfoService userInfoService,
                                            ConversationService conversationService) {
        this.friendRelationService = friendRelationService;
        this.userInfoService = userInfoService;
        this.conversationService = conversationService;
    }

    @Override
    public MessageSendPermissionResult check(MessageSendPermissionRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getSenderId())
                || !StringUtils.hasText(request.getReceiverId())
                || !StringUtils.hasText(request.getConversationId())) {
            return MessageSendPermissionResult.blocked(
                    ReceiveOption.BLOCK.getCode(),
                    ReceiveOption.BLOCK.getCode());
        }
        int globalReceiveOption = userInfoService.getReceiveOptions(request.getReceiverId());
        int conversationReceiveOption = conversationService.getReceiveOption(
                request.getReceiverId(),
                request.getConversationId());
        if (friendRelationService.isBlocked(request.getSenderId(), request.getReceiverId())) {
            return MessageSendPermissionResult.blocked(globalReceiveOption, conversationReceiveOption);
        }
        return MessageSendPermissionResult.allow(globalReceiveOption, conversationReceiveOption);
    }
}
