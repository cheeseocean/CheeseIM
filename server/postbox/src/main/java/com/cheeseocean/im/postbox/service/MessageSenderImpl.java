package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.core.util.IdGenerator;
import com.cheeseocean.im.postbox.facade.ConversationServiceFacade;
import com.cheeseocean.im.postbox.facade.UserServiceFacade;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class MessageSenderImpl implements MessageSender {

    private final IngressMessagePublisher ingressMessagePublisher;

    @DubboReference
    private FriendRelationService friendRelationService;

    @Autowired
    private ConversationServiceFacade conversationServiceFacade;

    @Autowired
    private UserServiceFacade userServiceFacade;

    public MessageSenderImpl(IngressMessagePublisher ingressMessagePublisher) {
        this.ingressMessagePublisher = ingressMessagePublisher;
    }

    private static SendMessageResp rejectedResp(Message msg) {
        return buildResp(msg, false);
    }

    /**
     * 统一构造发送响应，避免 accepted/基础字段在不同分支重复拼装。
     */
    private static SendMessageResp buildResp(Message msg,
                                             boolean accepted) {
        SendMessageResp resp = new SendMessageResp();
        resp.setAccepted(accepted);
        resp.setClientMsgId(msg.getClientMsgId());
        resp.setServerMsgId(msg.getServerMsgId());
        return resp;
    }


    @Override
    public SendMessageResp sendMessage(SendMessageReq req) {
        Message msg = req.getMsg();
        String         conversationId = ConversationIdUtil.buildConversationId(msg.getSessionType(), msg.getSenderId(), msg.getReceiverId(), msg.getGroupId());
        MessageOptions options        = MessageOptionPolicy.fillDefaultOptions(msg);

        // 单聊需要检查接收方选项
        if (requiresPermissionCheck(msg, options)) {
            SendMessageResp blocked = checkSingleChatPermission(msg, conversationId, options);
            if (blocked != null) {
                return blocked;
            }
        }

        // 生成服务端消息 ID，并将请求转换为统一入口事件投递到后续链路。
        msg.setServerMsgId(IdGenerator.generateMsgId());
        ingressMessagePublisher.publish(msg);

        // 消息进入入口队列即视为 accepted，响应仅回传基础确认信息。
        return buildResp(msg, true);
    }

    /**
     * 权限检查仅对单聊非通知消息生效，通知消息和群消息直接跳过。
     */
    private boolean requiresPermissionCheck(Message msg, MessageOptions options) {
        if (options.getNotification() != null && options.getNotification()) {
            return false;
        }
        return msg.getSessionType() == SessionType.SINGLE;
    }

    /**
     * 单聊消息发送权限校验：
     * <p>
     * 1. 黑名单检查  — recvId 将 senderId 加入黑名单则直接拒绝。
     * 2. 用户接收配置
     * BLOCK (1)        → 直接拒绝
     * DO_NOT_DISTURB (2) → 关闭离线推送，继续投递
     * 3. 会话级接收配置（全局通过后才检查）：
     * BLOCK (1)        → 直接拒绝（已读回执除外）
     * DO_NOT_DISTURB (2) → 关闭离线推送，继续投递
     * <p>
     * 返回 null 表示消息可正常投递（options 可能已被修改）；
     * 返回非 null 表示消息需被丢弃。
     */
    private SendMessageResp checkSingleChatPermission(Message req,
                                                      String conversationId,
                                                      MessageOptions options) {
        String senderId = req.getSenderId();
        String receiverId   = req.getReceiverId();

        // 1. 黑名单
        if (friendRelationService.isBlocked(senderId, receiverId)) {
            return rejectedResp(req);
        }

        // 2. 用户级别接收配置
        ReceiveOption globalOpt = ReceiveOption.fromCode(userServiceFacade.getReceiveOptions(receiverId));
        if (globalOpt == ReceiveOption.BLOCK) {
            return rejectedResp(req);
        }
        if (globalOpt == ReceiveOption.DO_NOT_DISTURB) {
            options.setNeedOfflinePush(false);
        }

        // 3. 会话级接收配置
        ReceiveOption convOpt = ReceiveOption.fromCode(conversationServiceFacade.getReceiveOption(receiverId, conversationId));
        if (convOpt == ReceiveOption.BLOCK) {
            // 已读回执绕过会话级屏蔽
            if (!MessageOptionPolicy.isReadReceipt(req.getContentType())) {
                return rejectedResp(req);
            }
        }
        if (convOpt == ReceiveOption.DO_NOT_DISTURB) {
            options.setNeedOfflinePush(false);
        }

        return null;
    }
}
