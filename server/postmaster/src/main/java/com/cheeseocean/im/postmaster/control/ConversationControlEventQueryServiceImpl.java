package com.cheeseocean.im.postmaster.control;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.conversation.ConversationControlEventQueryService;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 会话控制事件的 postmaster 查询适配器。 */
@Service
@DubboService
public class ConversationControlEventQueryServiceImpl implements ConversationControlEventQueryService {

    private final ConversationControlEventRepository controlEventRepository;

    public ConversationControlEventQueryServiceImpl(ConversationControlEventRepository controlEventRepository) {
        this.controlEventRepository = controlEventRepository;
    }

    @Override
    public List<ConversationControlEvent> findAfter(String userId, long cursor, int limit) {
        return controlEventRepository.findAfter(userId, cursor, limit);
    }
}
