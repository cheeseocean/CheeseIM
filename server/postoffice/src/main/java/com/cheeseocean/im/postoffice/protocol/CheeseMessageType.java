package com.cheeseocean.im.postoffice.protocol;

/**
 * TCP协议消息类型定义
 * 与WebSocket消息类型保持一致，但使用byte类型以节省空间
 * 
 * @author CheeseIM
 */
public class CheeseMessageType {
    
    // ============ 连接相关 ============
    
    /**
     * 连接请求
     */
    public static final byte TCP_CONNECT_REQ = 1;
    
    /**
     * 连接成功响应
     */
    public static final byte TCP_CONNECT_SUCCESS = 2;
    
    /**
     * 连接失败响应
     */
    public static final byte TCP_CONNECT_FAILED = 3;
    
    // ============ 认证相关 ============
    
    /**
     * 认证请求
     */
    public static final byte TCP_AUTH_REQ = 10;
    
    /**
     * 认证成功响应
     */
    public static final byte TCP_AUTH_SUCCESS = 11;
    
    /**
     * 认证失败响应
     */
    public static final byte TCP_AUTH_FAILED = 12;
    
    // ============ 心跳相关 ============
    
    /**
     * 心跳请求
     */
    public static final byte TCP_HEARTBEAT_REQ = 20;
    
    /**
     * 心跳响应
     */
    public static final byte TCP_HEARTBEAT_RESP = 21;
    
    // ============ 消息相关 ============
    
    /**
     * 发送消息请求
     */
    public static final byte TCP_SEND_MSG_REQ = 30;
    
    /**
     * 发送消息响应
     */
    public static final byte TCP_SEND_MSG_RESP = 31;
    
    /**
     * 接收消息通知
     */
    public static final byte TCP_RECV_MSG_NOTIFY = 32;
    
    /**
     * 消息已读回执
     */
    public static final byte TCP_MSG_READ_RECEIPT = 33;
    
    /**
     * 撤回消息请求
     */
    public static final byte TCP_REVOKE_MSG_REQ = 34;
    
    /**
     * 撤回消息通知
     */
    public static final byte TCP_REVOKE_MSG_NOTIFY = 35;
    
    // ============ 用户状态相关 ============
    
    /**
     * 用户上线通知
     */
    public static final byte TCP_USER_ONLINE_NOTIFY = 40;
    
    /**
     * 用户下线通知
     */
    public static final byte TCP_USER_OFFLINE_NOTIFY = 41;
    
    /**
     * 强制下线通知
     */
    public static final byte TCP_FORCE_LOGOUT_NOTIFY = 42;
    
    /**
     * 多端登录通知
     */
    public static final byte TCP_MULTI_LOGIN_NOTIFY = 43;
    
    // ============ 会话相关 ============
    
    /**
     * 会话变更通知
     */
    public static final byte TCP_CONVERSATION_CHANGE_NOTIFY = 50;
    
    /**
     * 新会话通知
     */
    public static final byte TCP_NEW_CONVERSATION_NOTIFY = 51;
    
    // ============ 群组相关 ============
    
    /**
     * 群组信息变更通知
     */
    public static final byte TCP_GROUP_INFO_CHANGE_NOTIFY = 60;
    
    /**
     * 群成员变更通知
     */
    public static final byte TCP_GROUP_MEMBER_CHANGE_NOTIFY = 61;
    
    /**
     * 入群申请通知
     */
    public static final byte TCP_GROUP_APPLICATION_NOTIFY = 62;
    
    // ============ 好友相关 ============
    
    /**
     * 好友申请通知
     */
    public static final byte TCP_FRIEND_APPLICATION_NOTIFY = 70;
    
    /**
     * 好友申请处理通知
     */
    public static final byte TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY = 71;
    
    /**
     * 好友信息变更通知
     */
    public static final byte TCP_FRIEND_INFO_CHANGE_NOTIFY = 72;
    
    /**
     * 好友删除通知
     */
    public static final byte TCP_FRIEND_DELETE_NOTIFY = 73;
    
    /**
     * 黑名单变更通知
     */
    public static final byte TCP_BLACK_LIST_CHANGE_NOTIFY = 74;
    
    // ============ 错误相关 ============
    
    /**
     * 通用错误响应
     */
    public static final byte TCP_ERROR_RESP = 90;
    
    /**
     * 参数错误
     */
    public static final byte TCP_PARAM_ERROR = 91;
    
    /**
     * 权限错误
     */
    public static final byte TCP_PERMISSION_ERROR = 92;
    
    /**
     * 服务器内部错误
     */
    public static final byte TCP_INTERNAL_ERROR = 93;
    
    /**
     * 获取消息类型描述
     */
    public static String getMessageTypeDesc(byte msgType) {
        switch (msgType) {
            // 连接相关
            case TCP_CONNECT_REQ: return "TCP_CONNECT_REQ";
            case TCP_CONNECT_SUCCESS: return "TCP_CONNECT_SUCCESS";
            case TCP_CONNECT_FAILED: return "TCP_CONNECT_FAILED";
            
            // 认证相关
            case TCP_AUTH_REQ: return "TCP_AUTH_REQ";
            case TCP_AUTH_SUCCESS: return "TCP_AUTH_SUCCESS";
            case TCP_AUTH_FAILED: return "TCP_AUTH_FAILED";
            
            // 心跳相关
            case TCP_HEARTBEAT_REQ: return "TCP_HEARTBEAT_REQ";
            case TCP_HEARTBEAT_RESP: return "TCP_HEARTBEAT_RESP";
            
            // 消息相关
            case TCP_SEND_MSG_REQ: return "TCP_SEND_MSG_REQ";
            case TCP_SEND_MSG_RESP: return "TCP_SEND_MSG_RESP";
            case TCP_RECV_MSG_NOTIFY: return "TCP_RECV_MSG_NOTIFY";
            case TCP_MSG_READ_RECEIPT: return "TCP_MSG_READ_RECEIPT";
            case TCP_REVOKE_MSG_REQ: return "TCP_REVOKE_MSG_REQ";
            case TCP_REVOKE_MSG_NOTIFY: return "TCP_REVOKE_MSG_NOTIFY";
            
            // 用户状态相关
            case TCP_USER_ONLINE_NOTIFY: return "TCP_USER_ONLINE_NOTIFY";
            case TCP_USER_OFFLINE_NOTIFY: return "TCP_USER_OFFLINE_NOTIFY";
            case TCP_FORCE_LOGOUT_NOTIFY: return "TCP_FORCE_LOGOUT_NOTIFY";
            case TCP_MULTI_LOGIN_NOTIFY: return "TCP_MULTI_LOGIN_NOTIFY";
            
            // 会话相关
            case TCP_CONVERSATION_CHANGE_NOTIFY: return "TCP_CONVERSATION_CHANGE_NOTIFY";
            case TCP_NEW_CONVERSATION_NOTIFY: return "TCP_NEW_CONVERSATION_NOTIFY";
            
            // 群组相关
            case TCP_GROUP_INFO_CHANGE_NOTIFY: return "TCP_GROUP_INFO_CHANGE_NOTIFY";
            case TCP_GROUP_MEMBER_CHANGE_NOTIFY: return "TCP_GROUP_MEMBER_CHANGE_NOTIFY";
            case TCP_GROUP_APPLICATION_NOTIFY: return "TCP_GROUP_APPLICATION_NOTIFY";
            
            // 好友相关
            case TCP_FRIEND_APPLICATION_NOTIFY: return "TCP_FRIEND_APPLICATION_NOTIFY";
            case TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY: return "TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY";
            case TCP_FRIEND_INFO_CHANGE_NOTIFY: return "TCP_FRIEND_INFO_CHANGE_NOTIFY";
            case TCP_FRIEND_DELETE_NOTIFY: return "TCP_FRIEND_DELETE_NOTIFY";
            case TCP_BLACK_LIST_CHANGE_NOTIFY: return "TCP_BLACK_LIST_CHANGE_NOTIFY";
            
            // 错误相关
            case TCP_ERROR_RESP: return "TCP_ERROR_RESP";
            case TCP_PARAM_ERROR: return "TCP_PARAM_ERROR";
            case TCP_PERMISSION_ERROR: return "TCP_PERMISSION_ERROR";
            case TCP_INTERNAL_ERROR: return "TCP_INTERNAL_ERROR";
            
            default: return "UNKNOWN_TYPE_" + msgType;
        }
    }
    
    /**
     * 检查是否为请求类型消息
     */
    public static boolean isRequestType(byte msgType) {
        return msgType == TCP_CONNECT_REQ ||
               msgType == TCP_AUTH_REQ ||
               msgType == TCP_HEARTBEAT_REQ ||
               msgType == TCP_SEND_MSG_REQ ||
               msgType == TCP_REVOKE_MSG_REQ;
    }
    
    /**
     * 检查是否为响应类型消息
     */
    public static boolean isResponseType(byte msgType) {
        return msgType == TCP_CONNECT_SUCCESS ||
               msgType == TCP_CONNECT_FAILED ||
               msgType == TCP_AUTH_SUCCESS ||
               msgType == TCP_AUTH_FAILED ||
               msgType == TCP_HEARTBEAT_RESP ||
               msgType == TCP_SEND_MSG_RESP ||
               msgType == TCP_ERROR_RESP ||
               msgType == TCP_PARAM_ERROR ||
               msgType == TCP_PERMISSION_ERROR ||
               msgType == TCP_INTERNAL_ERROR;
    }
    
    /**
     * 检查是否为通知类型消息
     */
    public static boolean isNotifyType(byte msgType) {
        return msgType == TCP_RECV_MSG_NOTIFY ||
               msgType == TCP_MSG_READ_RECEIPT ||
               msgType == TCP_REVOKE_MSG_NOTIFY ||
               msgType == TCP_USER_ONLINE_NOTIFY ||
               msgType == TCP_USER_OFFLINE_NOTIFY ||
               msgType == TCP_FORCE_LOGOUT_NOTIFY ||
               msgType == TCP_MULTI_LOGIN_NOTIFY ||
               msgType == TCP_CONVERSATION_CHANGE_NOTIFY ||
               msgType == TCP_NEW_CONVERSATION_NOTIFY ||
               msgType == TCP_GROUP_INFO_CHANGE_NOTIFY ||
               msgType == TCP_GROUP_MEMBER_CHANGE_NOTIFY ||
               msgType == TCP_GROUP_APPLICATION_NOTIFY ||
               msgType == TCP_FRIEND_APPLICATION_NOTIFY ||
               msgType == TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY ||
               msgType == TCP_FRIEND_INFO_CHANGE_NOTIFY ||
               msgType == TCP_FRIEND_DELETE_NOTIFY ||
               msgType == TCP_BLACK_LIST_CHANGE_NOTIFY;
    }
    
    /**
     * TCP消息类型转换为WebSocket消息类型
     */
    public static int tcpToWsMessageType(byte tcpType) {
        switch (tcpType) {
            case TCP_CONNECT_SUCCESS: return WSMessageType.WS_CONNECT_SUCCESS;
            case TCP_CONNECT_FAILED: return WSMessageType.WS_CONNECT_FAILED;
            case TCP_AUTH_REQ: return WSMessageType.WS_AUTH_REQ;
            case TCP_AUTH_SUCCESS: return WSMessageType.WS_AUTH_SUCCESS;
            case TCP_AUTH_FAILED: return WSMessageType.WS_AUTH_FAILED;
            case TCP_HEARTBEAT_REQ: return WSMessageType.WS_HEARTBEAT_REQ;
            case TCP_HEARTBEAT_RESP: return WSMessageType.WS_HEARTBEAT_RESP;
            case TCP_SEND_MSG_REQ: return WSMessageType.WS_SEND_MSG_REQ;
            case TCP_SEND_MSG_RESP: return WSMessageType.WS_SEND_MSG_RESP;
            case TCP_RECV_MSG_NOTIFY: return WSMessageType.WS_RECV_MSG_NOTIFY;
            case TCP_MSG_READ_RECEIPT: return WSMessageType.WS_MSG_READ_NOTIFY;
            case TCP_REVOKE_MSG_NOTIFY: return WSMessageType.WS_MSG_REVOKE_NOTIFY;
            case TCP_USER_ONLINE_NOTIFY: return WSMessageType.WS_USER_ONLINE_NOTIFY;
            case TCP_USER_OFFLINE_NOTIFY: return WSMessageType.WS_USER_OFFLINE_NOTIFY;
            case TCP_FORCE_LOGOUT_NOTIFY: return WSMessageType.WS_FORCE_LOGOUT_NOTIFY;
            case TCP_ERROR_RESP: return WSMessageType.WS_ERROR_RESP;
            case TCP_PARAM_ERROR: return WSMessageType.WS_PARAM_ERROR;
            case TCP_PERMISSION_ERROR: return WSMessageType.WS_PERMISSION_ERROR;
            case TCP_INTERNAL_ERROR: return WSMessageType.WS_INTERNAL_ERROR;
            default: return tcpType; // 如果没有对应的WebSocket类型，直接返回TCP类型
        }
    }
    
    /**
     * WebSocket消息类型转换为TCP消息类型
     */
    public static byte wsToTcpMessageType(int wsType) {
        switch (wsType) {
            case WSMessageType.WS_CONNECT_SUCCESS: return TCP_CONNECT_SUCCESS;
            case WSMessageType.WS_CONNECT_FAILED: return TCP_CONNECT_FAILED;
            case WSMessageType.WS_AUTH_REQ: return TCP_AUTH_REQ;
            case WSMessageType.WS_AUTH_SUCCESS: return TCP_AUTH_SUCCESS;
            case WSMessageType.WS_AUTH_FAILED: return TCP_AUTH_FAILED;
            case WSMessageType.WS_HEARTBEAT_REQ: return TCP_HEARTBEAT_REQ;
            case WSMessageType.WS_HEARTBEAT_RESP: return TCP_HEARTBEAT_RESP;
            case WSMessageType.WS_SEND_MSG_REQ: return TCP_SEND_MSG_REQ;
            case WSMessageType.WS_SEND_MSG_RESP: return TCP_SEND_MSG_RESP;
            case WSMessageType.WS_RECV_MSG_NOTIFY: return TCP_RECV_MSG_NOTIFY;
            case WSMessageType.WS_MSG_READ_NOTIFY: return TCP_MSG_READ_RECEIPT;
            case WSMessageType.WS_MSG_REVOKE_NOTIFY: return TCP_REVOKE_MSG_NOTIFY;
            case WSMessageType.WS_USER_ONLINE_NOTIFY: return TCP_USER_ONLINE_NOTIFY;
            case WSMessageType.WS_USER_OFFLINE_NOTIFY: return TCP_USER_OFFLINE_NOTIFY;
            case WSMessageType.WS_FORCE_LOGOUT_NOTIFY: return TCP_FORCE_LOGOUT_NOTIFY;
            case WSMessageType.WS_ERROR_RESP: return TCP_ERROR_RESP;
            case WSMessageType.WS_PARAM_ERROR: return TCP_PARAM_ERROR;
            case WSMessageType.WS_PERMISSION_ERROR: return TCP_PERMISSION_ERROR;
            case WSMessageType.WS_INTERNAL_ERROR: return TCP_INTERNAL_ERROR;
            default: return (byte) wsType; // 如果没有对应的TCP类型，强制转换
        }
    }
}
