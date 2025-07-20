package com.cheeseocean.im.postoffice.protocol;

/**
 * WebSocket消息类型常量
 * 参照OpenIM Server的消息类型定义
 * 
 * @author CheeseIM
 */
public class WSMessageType {
    
    // ============ 连接相关 (1001-1004) ============
    /**
     * 连接请求
     */
    public static final int WS_CONNECT_REQ = 1001;
    
    /**
     * 连接成功响应
     */
    public static final int WS_CONNECT_SUCCESS = 1002;
    
    /**
     * 连接失败响应
     */
    public static final int WS_CONNECT_FAILED = 1003;
    
    /**
     * 连接断开通知
     */
    public static final int WS_DISCONNECT = 1004;
    
    // ============ 认证相关 (1101-1103) ============
    /**
     * 用户认证请求
     */
    public static final int WS_AUTH_REQ = 1101;
    
    /**
     * 认证成功响应
     */
    public static final int WS_AUTH_SUCCESS = 1102;
    
    /**
     * 认证失败响应
     */
    public static final int WS_AUTH_FAILED = 1103;
    
    // ============ 心跳相关 (1201-1202) ============
    /**
     * 心跳请求
     */
    public static final int WS_HEARTBEAT_REQ = 1201;
    
    /**
     * 心跳响应
     */
    public static final int WS_HEARTBEAT_RESP = 1202;
    
    // ============ 消息相关 (2001-2005) ============
    /**
     * 发送消息请求
     */
    public static final int WS_SEND_MSG_REQ = 2001;
    
    /**
     * 发送消息响应
     */
    public static final int WS_SEND_MSG_RESP = 2002;
    
    /**
     * 接收消息通知
     */
    public static final int WS_RECV_MSG_NOTIFY = 2003;
    
    /**
     * 消息已读通知
     */
    public static final int WS_MSG_READ_NOTIFY = 2004;
    
    /**
     * 消息撤回通知
     */
    public static final int WS_MSG_REVOKE_NOTIFY = 2005;
    
    // ============ 用户状态相关 (3001-3003) ============
    /**
     * 用户上线通知
     */
    public static final int WS_USER_ONLINE_NOTIFY = 3001;
    
    /**
     * 用户下线通知
     */
    public static final int WS_USER_OFFLINE_NOTIFY = 3002;
    
    /**
     * 用户状态变更通知
     */
    public static final int WS_USER_STATUS_CHANGE_NOTIFY = 3003;
    
    // ============ 会话相关 (4001-4002) ============
    /**
     * 会话变更通知
     */
    public static final int WS_CONVERSATION_CHANGE_NOTIFY = 4001;
    
    /**
     * 正在输入通知
     */
    public static final int WS_TYPING_NOTIFY = 4002;
    
    // ============ 群组相关 (5001-5005) ============
    /**
     * 群组创建通知
     */
    public static final int WS_GROUP_CREATE_NOTIFY = 5001;
    
    /**
     * 群组解散通知
     */
    public static final int WS_GROUP_DISMISS_NOTIFY = 5002;
    
    /**
     * 群组成员变更通知
     */
    public static final int WS_GROUP_MEMBER_CHANGE_NOTIFY = 5003;
    
    /**
     * 群组信息变更通知
     */
    public static final int WS_GROUP_INFO_CHANGE_NOTIFY = 5004;
    
    /**
     * 群组邀请通知
     */
    public static final int WS_GROUP_INVITE_NOTIFY = 5005;
    
    // ============ 好友相关 (6001-6004) ============
    /**
     * 好友申请通知
     */
    public static final int WS_FRIEND_REQUEST_NOTIFY = 6001;
    
    /**
     * 好友申请处理通知
     */
    public static final int WS_FRIEND_REQUEST_HANDLE_NOTIFY = 6002;
    
    /**
     * 好友添加通知
     */
    public static final int WS_FRIEND_ADD_NOTIFY = 6003;
    
    /**
     * 好友删除通知
     */
    public static final int WS_FRIEND_DELETE_NOTIFY = 6004;
    
    // ============ 系统通知 (7001-7002) ============
    /**
     * 系统通知
     */
    public static final int WS_SYSTEM_NOTIFY = 7001;
    
    /**
     * 强制下线通知
     */
    public static final int WS_FORCE_LOGOUT_NOTIFY = 7002;
    
    // ============ 错误响应 (9001-9999) ============
    /**
     * 通用错误响应
     */
    public static final int WS_ERROR_RESP = 9001;
    
    /**
     * 参数错误
     */
    public static final int WS_PARAM_ERROR = 9002;
    
    /**
     * 权限错误
     */
    public static final int WS_PERMISSION_ERROR = 9003;
    
    /**
     * 服务器内部错误
     */
    public static final int WS_INTERNAL_ERROR = 9004;
    
    private WSMessageType() {
        // 私有构造函数，防止实例化
    }
    
    /**
     * 判断是否为请求类型消息
     */
    public static boolean isRequestMessage(int msgType) {
        return msgType == WS_CONNECT_REQ || 
               msgType == WS_AUTH_REQ || 
               msgType == WS_HEARTBEAT_REQ || 
               msgType == WS_SEND_MSG_REQ;
    }
    
    /**
     * 判断是否为响应类型消息
     */
    public static boolean isResponseMessage(int msgType) {
        return msgType == WS_CONNECT_SUCCESS || 
               msgType == WS_CONNECT_FAILED || 
               msgType == WS_AUTH_SUCCESS || 
               msgType == WS_AUTH_FAILED || 
               msgType == WS_HEARTBEAT_RESP || 
               msgType == WS_SEND_MSG_RESP;
    }
    
    /**
     * 判断是否为通知类型消息
     */
    public static boolean isNotifyMessage(int msgType) {
        return msgType >= 2003 && msgType <= 7002;
    }
    
    /**
     * 获取消息类型描述
     */
    public static String getMessageTypeDesc(int msgType) {
        switch (msgType) {
            case WS_CONNECT_REQ: return "连接请求";
            case WS_CONNECT_SUCCESS: return "连接成功";
            case WS_CONNECT_FAILED: return "连接失败";
            case WS_DISCONNECT: return "连接断开";
            case WS_AUTH_REQ: return "认证请求";
            case WS_AUTH_SUCCESS: return "认证成功";
            case WS_AUTH_FAILED: return "认证失败";
            case WS_HEARTBEAT_REQ: return "心跳请求";
            case WS_HEARTBEAT_RESP: return "心跳响应";
            case WS_SEND_MSG_REQ: return "发送消息";
            case WS_SEND_MSG_RESP: return "发送消息响应";
            case WS_RECV_MSG_NOTIFY: return "接收消息通知";
            case WS_MSG_READ_NOTIFY: return "消息已读通知";
            case WS_MSG_REVOKE_NOTIFY: return "消息撤回通知";
            case WS_USER_ONLINE_NOTIFY: return "用户上线通知";
            case WS_USER_OFFLINE_NOTIFY: return "用户下线通知";
            case WS_USER_STATUS_CHANGE_NOTIFY: return "用户状态变更通知";
            case WS_CONVERSATION_CHANGE_NOTIFY: return "会话变更通知";
            case WS_TYPING_NOTIFY: return "正在输入通知";
            case WS_GROUP_CREATE_NOTIFY: return "群组创建通知";
            case WS_GROUP_DISMISS_NOTIFY: return "群组解散通知";
            case WS_GROUP_MEMBER_CHANGE_NOTIFY: return "群组成员变更通知";
            case WS_GROUP_INFO_CHANGE_NOTIFY: return "群组信息变更通知";
            case WS_GROUP_INVITE_NOTIFY: return "群组邀请通知";
            case WS_FRIEND_REQUEST_NOTIFY: return "好友申请通知";
            case WS_FRIEND_REQUEST_HANDLE_NOTIFY: return "好友申请处理通知";
            case WS_FRIEND_ADD_NOTIFY: return "好友添加通知";
            case WS_FRIEND_DELETE_NOTIFY: return "好友删除通知";
            case WS_SYSTEM_NOTIFY: return "系统通知";
            case WS_FORCE_LOGOUT_NOTIFY: return "强制下线通知";
            case WS_ERROR_RESP: return "错误响应";
            case WS_PARAM_ERROR: return "参数错误";
            case WS_PERMISSION_ERROR: return "权限错误";
            case WS_INTERNAL_ERROR: return "服务器内部错误";
            default: return "未知消息类型";
        }
    }
}
