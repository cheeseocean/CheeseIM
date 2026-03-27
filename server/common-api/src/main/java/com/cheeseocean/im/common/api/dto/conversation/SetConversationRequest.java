package com.cheeseocean.im.common.api.dto.conversation;

/**
 * 会话配置更新请求。
 * 用于 {@link com.cheeseocean.im.common.api.conversation.ConversationWriteService#setConversations}。
 *
 * <p>创建会话时 conversationId / conversationType / targetId 必填；
 * 更新配置时仅需填写要变更的可选字段，null 表示不修改。
 */
public class SetConversationRequest {

    /** 会话 ID，必填 */
    private String conversationId;

    /** 会话类型：1=单聊，2=群聊，3=通知 */
    private int conversationType;

    /** 对端 ID：单聊为对方 userId，群聊为 groupId */
    private String targetId;

    /**
     * 会话级消息接收选项 code，null 表示不修改。
     * 取值见 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt}。
     */
    private Integer recvMsgOpt;

    /** 是否置顶，null 表示不修改 */
    private Boolean pinned;

    /** 草稿文本，null 表示不修改 */
    private String draftText;

    /** 附加信息，null 表示不修改 */
    private String attachedInfo;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public int getConversationType() { return conversationType; }
    public void setConversationType(int conversationType) { this.conversationType = conversationType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public Integer getRecvMsgOpt() { return recvMsgOpt; }
    public void setRecvMsgOpt(Integer recvMsgOpt) { this.recvMsgOpt = recvMsgOpt; }

    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }

    public String getDraftText() { return draftText; }
    public void setDraftText(String draftText) { this.draftText = draftText; }

    public String getAttachedInfo() { return attachedInfo; }
    public void setAttachedInfo(String attachedInfo) { this.attachedInfo = attachedInfo; }
}
