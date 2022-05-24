package com.cheeseocean.cheeseim.base.entity;

import java.util.List;
import java.util.Map;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class MsgData {
    private String sendID;
    private String recvID;
    private String groupID;
    private String clientMsgID;
    private String serverMsgID;
    private int senderPlatformID;
    private String senderNickname;
    private String senderAvatarURL;
    private String sessionType;
    private String msgFrom;
    private String contentType;
    private String content;
    private int seq;
    private long sendTime;
    private long createTime;
    private int status;
    private Map<String, Boolean> options;
    private OfflinePushInfo offlinePushInfo;
    private List<String> atUserIDList;

    public String getSendID() {
        return sendID;
    }

    public void setSendID(String sendID) {
        this.sendID = sendID;
    }

    public String getRecvID() {
        return recvID;
    }

    public void setRecvID(String recvID) {
        this.recvID = recvID;
    }

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public String getClientMsgID() {
        return clientMsgID;
    }

    public void setClientMsgID(String clientMsgID) {
        this.clientMsgID = clientMsgID;
    }

    public String getServerMsgID() {
        return serverMsgID;
    }

    public void setServerMsgID(String serverMsgID) {
        this.serverMsgID = serverMsgID;
    }

    public int getSenderPlatformID() {
        return senderPlatformID;
    }

    public void setSenderPlatformID(int senderPlatformID) {
        this.senderPlatformID = senderPlatformID;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public void setSenderNickname(String senderNickname) {
        this.senderNickname = senderNickname;
    }

    public String getSenderAvatarURL() {
        return senderAvatarURL;
    }

    public void setSenderAvatarURL(String senderAvatarURL) {
        this.senderAvatarURL = senderAvatarURL;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getMsgFrom() {
        return msgFrom;
    }

    public void setMsgFrom(String msgFrom) {
        this.msgFrom = msgFrom;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Map<String, Boolean> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Boolean> options) {
        this.options = options;
    }

    public OfflinePushInfo getOfflinePushInfo() {
        return offlinePushInfo;
    }

    public void setOfflinePushInfo(OfflinePushInfo offlinePushInfo) {
        this.offlinePushInfo = offlinePushInfo;
    }

    public List<String> getAtUserIDList() {
        return atUserIDList;
    }

    public void setAtUserIDList(List<String> atUserIDList) {
        this.atUserIDList = atUserIDList;
    }
}
