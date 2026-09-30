package com.cheeseocean.im.common.api.dto.conversation;


import lombok.Data;

/**
 * 会话配置更新请求。
 * 用于 {@link com.cheeseocean.im.common.api.conversation.ConversationService#setConversations}。
 *
 * <p>仅更新已有且当前有权访问的会话；本请求不能创建会话。
 * conversationType（非零）/targetId（非空）仅作身份一致性校验，可选配置为 null 表示不修改。
 */
@Data
public class SetConversationRequest {

    /**
     * 会话 ID，必填
     */
    private String conversationId;

    /**
     * 会话类型：1=单聊，2=群聊，3=通知
     */
    private int conversationType;

    /**
     * 对端 ID：单聊为对方 userId，群聊为 groupId
     */
    private String targetId;

    /**
     * 会话级消息接收选项 code，null 表示不修改。
     * 取值见 {@link com.cheeseocean.im.common.api.enums.ReceiveOption}。
     */
    private Integer recvMsgOpt;

    /**
     * 是否置顶，null 表示不修改
     */
    private Boolean pinned;

    /**
     * 草稿文本，null 表示不修改
     */
    private String draftText;

    /**
     * 附加信息，null 表示不修改
     */
    private String attachedInfo;
}
