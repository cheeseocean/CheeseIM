package com.cheeseocean.im.business.service.permission;

import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话访问权限的唯一领域实现。
 *
 * <p>会话视图是用户设置，不是授权凭证。单聊和个人通知按规范 ID 判定归属，群及群通知读取当前成员事实；
 * 不缓存正授权，保证退出群及仓储故障不会复用旧放行结果。当前契约只判定访问关系，不定义入群历史窗口。</p>
 */
@Slf4j
@Service
@DubboService
public class ConversationPermissionServiceImpl implements ConversationPermissionService {

    private final GroupMemberRepository groupMemberRepository;

    public ConversationPermissionServiceImpl(GroupMemberRepository groupMemberRepository) {
        this.groupMemberRepository = groupMemberRepository;
    }

    @Override
    public PermissionCheckResult check(ConversationPermissionRequest request) {
        if (request == null || blank(request.getUserId()) || blank(request.getConversationId())) {
            return deny(ErrorCode.INVALID_PARAM);
        }
        String userId = request.getUserId();
        String conversationId = request.getConversationId();
        if (conversationId.startsWith("s:")) {
            String peer = ConversationIdUtil.peerUser(conversationId, userId);
            return peer != null && !blank(peer) && conversationId.equals(ConversationIdUtil.single(userId, peer))
                    ? PermissionCheckResult.allow() : deny(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }
        if (conversationId.startsWith("n:")) {
            return conversationId.equals(ConversationIdUtil.notification(userId))
                    ? PermissionCheckResult.allow() : deny(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }
        String groupId = conversationId.startsWith("g:") ? conversationId.substring(2)
                : conversationId.startsWith("ng:") ? conversationId.substring(3) : null;
        if (blank(groupId)) {
            return deny(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }
        try {
            return groupMemberRepository.existsByGroupAndUser(groupId, userId)
                    ? PermissionCheckResult.allow() : deny(ErrorCode.CONVERSATION_ACCESS_DENIED);
        } catch (RuntimeException exception) {
            log.warn("会话成员事实查询失败，拒绝访问: conversationId={}", conversationId, exception);
            return deny(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 本节点业务读取与 RPC 复用同一权限规则，不能把可写会话视图当作成员关系。 */
    public boolean canAccess(String userId, String conversationId) {
        ConversationPermissionRequest request = new ConversationPermissionRequest();
        request.setUserId(userId);
        request.setConversationId(conversationId);
        return check(request).isAllowed();
    }

    private PermissionCheckResult deny(ErrorCode errorCode) {
        return PermissionCheckResult.deny(Integer.toString(errorCode.getCode()), errorCode.getDesc());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
