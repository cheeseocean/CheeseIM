package com.cheeseocean.im.postoffice.protocol;

import com.cheeseocean.im.common.api.dto.message.ChatSendRequest;
import com.cheeseocean.im.common.api.dto.message.ReadReceiptPayload;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ReceiptType;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket gateway message envelope.
 */
public class WSMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createDefaultMapper();
    
    /**
     * 消息类型
     */
    @JsonProperty("msgType")
    private Integer msgType;
    
    /**
     * 操作ID，用于请求追踪和链路监控
     */
    @JsonProperty("operationID")
    private String operationID;
    
    /**
     * 消息数据，具体内容根据msgType而定
     */
    @JsonProperty("data")
    private Object data;
    
    /**
     * 发送者ID
     */
    @JsonProperty("sendID")
    private String sendID;
    
    /**
     * 接收者ID
     */
    @JsonProperty("recvID")
    private String recvID;
    
    /**
     * 发送时间戳
     */
    @JsonProperty("sendTime")
    private Long sendTime;
    
    /**
     * 消息ID（可选）
     */
    @JsonProperty("msgID")
    private String msgID;
    
    /**
     * 扩展字段
     */
    @JsonProperty("ex")
    private Map<String, Object> ex;
    
    public WSMessage() {
        this.sendTime = System.currentTimeMillis();
    }
    
    public WSMessage(Integer msgType, String operationID, Object data) {
        this();
        this.msgType = msgType;
        this.operationID = operationID;
        this.data = data;
    }
    
    public WSMessage(Integer msgType, String operationID, Object data, String sendID, String recvID) {
        this(msgType, operationID, data);
        this.sendID = sendID;
        this.recvID = recvID;
    }
    
    // ============ 静态工厂方法 ============
    
    /**
     * 创建连接成功响应
     */
    public static WSMessage connectSuccess(String operationID) {
        return new WSMessage(WSMessageType.WS_CONNECT_SUCCESS, operationID, "连接成功");
    }
    
    /**
     * 创建连接失败响应
     */
    public static WSMessage connectFailed(String operationID, String reason) {
        return new WSMessage(WSMessageType.WS_CONNECT_FAILED, operationID, reason);
    }
    
    /**
     * 创建认证成功响应
     */
    public static WSMessage authSuccess(String operationID, String userID) {
        return new WSMessage(WSMessageType.WS_AUTH_SUCCESS, operationID, 
                           Map.of("userID", userID, "message", "认证成功"));
    }
    
    /**
     * 创建认证失败响应
     */
    public static WSMessage authFailed(String operationID, String reason) {
        return new WSMessage(WSMessageType.WS_AUTH_FAILED, operationID, reason);
    }
    
    /**
     * 创建心跳响应
     */
    public static WSMessage heartbeatResp(String operationID) {
        return new WSMessage(WSMessageType.WS_HEARTBEAT_RESP, operationID, "pong");
    }
    
    /**
     * 创建发送消息响应
     */
    public static WSMessage sendMsgResp(String operationID, String serverMsgID, String clientMsgID, Long sendTime) {
        return sendMsgResp(operationID, serverMsgID, clientMsgID, sendTime, null);
    }

    public static WSMessage sendMsgResp(String operationID,
                                        String serverMsgID,
                                        String clientMsgID,
                                        Long sendTime,
                                        Long conversationSeq) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("serverMsgID", serverMsgID);
        payload.put("clientMsgID", clientMsgID);
        payload.put("sendTime", sendTime);
        if (conversationSeq != null) {
            payload.put("conversationSeq", conversationSeq);
        }
        return new WSMessage(WSMessageType.WS_SEND_MSG_RESP, operationID, payload);
    }
    
    /**
     * 创建接收消息通知
     */
    public static WSMessage recvMsgNotify(String operationID, Object msgData) {
        return new WSMessage(WSMessageType.WS_RECV_MSG_NOTIFY, operationID, msgData);
    }
    
    /**
     * 创建用户上线通知
     */
    public static WSMessage userOnlineNotify(String operationID, String userID, Integer platformID) {
        return new WSMessage(WSMessageType.WS_USER_ONLINE_NOTIFY, operationID, 
                           Map.of("userID", userID, "platformID", platformID, "status", "online"));
    }
    
    /**
     * 创建用户下线通知
     */
    public static WSMessage userOfflineNotify(String operationID, String userID, Integer platformID) {
        return new WSMessage(WSMessageType.WS_USER_OFFLINE_NOTIFY, operationID, 
                           Map.of("userID", userID, "platformID", platformID, "status", "offline"));
    }
    
    /**
     * 创建强制下线通知
     */
    public static WSMessage forceLogoutNotify(String operationID, String reason) {
        return new WSMessage(WSMessageType.WS_FORCE_LOGOUT_NOTIFY, operationID, 
                           Map.of("reason", reason, "message", "您的账号在其他设备登录，已被强制下线"));
    }
    
    /**
     * 创建错误响应
     */
    public static WSMessage errorResp(String operationID, int errorCode, String errorMsg) {
        return new WSMessage(WSMessageType.WS_ERROR_RESP, operationID, 
                           Map.of("errCode", errorCode, "errMsg", errorMsg));
    }

    public ClientEnvelope toClientEnvelope() {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(resolveCommandType());
        envelope.setRequestId(operationID);
        envelope.setBody(resolveClientBody());
        return envelope;
    }

    private CommandType resolveCommandType() {
        if (msgType == null) {
            return null;
        }
        switch (msgType) {
            case WSMessageType.WS_SEND_MSG_REQ:
                return CommandType.CHAT_SEND;
            case WSMessageType.WS_MSG_READ_NOTIFY:
                return CommandType.READ_RECEIPT;
            case WSMessageType.WS_MSG_REVOKE_NOTIFY:
                return CommandType.CHAT_REVOKE;
            case WSMessageType.WS_AUTH_REQ:
                return CommandType.AUTH;
            case WSMessageType.WS_HEARTBEAT_REQ:
                return CommandType.HEARTBEAT;
            case WSMessageType.WS_CONNECT_REQ:
                return CommandType.CONNECT;
            default:
                return null;
        }
    }

    private Object resolveClientBody() {
        if (data == null) {
            return null;
        }

        if (msgType == null) {
            return data;
        }

        switch (msgType) {
            case WSMessageType.WS_SEND_MSG_REQ:
                return readBody(ChatSendRequest.class);
            case WSMessageType.WS_MSG_READ_NOTIFY:
                ReadReceiptPayload payload = readBody(ReadReceiptPayload.class);
                if (payload != null && payload.getReceiptType() == null) {
                    payload.setReceiptType(ReceiptType.READ_CURSOR);
                }
                return payload;
            default:
                return data;
        }
    }

    private <T> T readBody(Class<T> bodyType) {
        try {
            if (data instanceof String) {
                return OBJECT_MAPPER.readValue((String) data, bodyType);
            }
            return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(data), bodyType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode WS body as " + bodyType.getSimpleName(), e);
        }
    }
    
    /**
     * 创建参数错误响应
     */
    public static WSMessage paramError(String operationID, String errorMsg) {
        return new WSMessage(WSMessageType.WS_PARAM_ERROR, operationID, errorMsg);
    }
    
    /**
     * 创建权限错误响应
     */
    public static WSMessage permissionError(String operationID, String errorMsg) {
        return new WSMessage(WSMessageType.WS_PERMISSION_ERROR, operationID, errorMsg);
    }
    
    /**
     * 创建服务器内部错误响应
     */
    public static WSMessage internalError(String operationID, String errorMsg) {
        return new WSMessage(WSMessageType.WS_INTERNAL_ERROR, operationID, errorMsg);
    }
    
    // ============ Getter and Setter ============
    
    public Integer getMsgType() {
        return msgType;
    }
    
    public void setMsgType(Integer msgType) {
        this.msgType = msgType;
    }
    
    public String getOperationID() {
        return operationID;
    }
    
    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
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
    
    public Long getSendTime() {
        return sendTime;
    }
    
    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }
    
    public String getMsgID() {
        return msgID;
    }
    
    public void setMsgID(String msgID) {
        this.msgID = msgID;
    }
    
    public Map<String, Object> getEx() {
        return ex;
    }
    
    public void setEx(Map<String, Object> ex) {
        this.ex = ex;
    }
    
    @Override
    public String toString() {
        return "WSMessage{" +
                "msgType=" + msgType +
                ", operationID='" + operationID + '\'' +
                ", data=" + data +
                ", sendID='" + sendID + '\'' +
                ", recvID='" + recvID + '\'' +
                ", sendTime=" + sendTime +
                ", msgID='" + msgID + '\'' +
                ", ex=" + ex +
                '}';
    }
}
