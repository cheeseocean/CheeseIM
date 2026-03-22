我建议把这部分单独抽成一个公共工程，比如：
	•	im-common-api
	•	或 im-contracts

不要把 DTO 散落在 postoffice/postbox/postman/push 里，不然后面改字段会非常痛苦。

⸻

1. 推荐工程结构

建议最少拆成这几个 Gradle 模块：

im-parent
├── im-common-core
├── im-common-api
├── im-postoffice
├── im-postbox
├── im-postman
└── im-push


⸻

2. 各公共模块职责

2.1 im-common-core

放最底层、纯工具和常量：
	•	常量定义
	•	枚举
	•	topic 名称
	•	redis key builder
	•	conversationId 生成器
	•	block 工具类
	•	通用异常码
	•	通用分页对象

⸻

2.2 im-common-api

放跨服务共享协议：
	•	Dubbo RPC 接口
	•	request / response DTO
	•	Kafka event DTO
	•	Message / Conversation DTO
	•	dispatch DTO
	•	query DTO

⸻

3. package 结构建议

3.1 im-common-core

com.xxx.im.common.core
├── constant
│   ├── TopicNames.java
│   ├── RedisKeys.java
│   ├── SessionType.java
│   ├── ContentType.java
│   ├── MessageStatus.java
│   └── ErrorCodes.java
├── util
│   ├── ConversationIdUtil.java
│   ├── MessageBlockUtil.java
│   ├── JsonUtil.java
│   └── TimeUtil.java
├── model
│   ├── PageQuery.java
│   ├── PageResult.java
│   └── BaseResponse.java
└── exception
    └── BizException.java


⸻

3.2 im-common-api

com.xxx.im.common.api
├── rpc
│   ├── MessageSendRpc.java
│   ├── MessageQueryRpc.java
│   └── OnlineDispatchRpc.java
├── dto
│   ├── message
│   ├── conversation
│   ├── dispatch
│   ├── query
│   └── common
└── event
    ├── IngressEvent.java
    ├── HistoryEvent.java
    ├── DeliveryEvent.java
    └── OfflinePushEvent.java


⸻

4. 核心常量代码骨架

4.1 TopicNames

package com.xxx.im.common.core.constant;

public final class TopicNames {
    private TopicNames() {
    }

    public static final String INGRESS = "ingress";
    public static final String HISTORY = "history";
    public static final String DELIVERY = "delivery";
    public static final String OFFLINE_PUSH = "offlinepush";
}


⸻

4.2 SessionType

package com.xxx.im.common.core.constant;

public final class SessionType {
    private SessionType() {
    }

    public static final int SINGLE = 1;
    public static final int GROUP = 2;
    public static final int NOTIFICATION = 3;
}


⸻

4.3 MessageStatus

package com.xxx.im.common.core.constant;

public final class MessageStatus {
    private MessageStatus() {
    }

    public static final int SENDING = 0;
    public static final int SEND_SUCCESS = 1;
    public static final int REVOKED = 2;
    public static final int DELETED = 3;
}


⸻

4.4 RedisKeys

package com.xxx.im.common.core.constant;

public final class RedisKeys {
    private RedisKeys() {
    }

    public static String onlineUser(String userId) {
        return "online:user:" + userId;
    }

    public static String onlineConn(String connectionId) {
        return "online:conn:" + connectionId;
    }

    public static String convMaxSeq(String conversationId) {
        return "conv:maxSeq:" + conversationId;
    }

    public static String convMinSeq(String conversationId) {
        return "conv:minSeq:" + conversationId;
    }

    public static String convLastMsg(String conversationId) {
        return "conv:lastMsg:" + conversationId;
    }

    public static String userReadSeq(String userId, String conversationId) {
        return "uc:read:" + userId + ":" + conversationId;
    }

    public static String userMinSeq(String userId, String conversationId) {
        return "uc:min:" + userId + ":" + conversationId;
    }

    public static String userMaxSeq(String userId, String conversationId) {
        return "uc:max:" + userId + ":" + conversationId;
    }

    public static String userUnread(String userId, String conversationId) {
        return "uc:unread:" + userId + ":" + conversationId;
    }

    public static String msgCache(String conversationId, long seq) {
        return "msg:" + conversationId + ":" + seq;
    }

    public static String ingressIdem(String conversationId, String clientMsgId) {
        return "idem:ingress:" + conversationId + ":" + clientMsgId;
    }

    public static String postmanIdem(String conversationId, String clientMsgId) {
        return "idem:postman:" + conversationId + ":" + clientMsgId;
    }

    public static String deliveryIdem(String serverMsgId, String userId, String connectionId) {
        return "idem:delivery:" + serverMsgId + ":" + userId + ":" + connectionId;
    }
}


⸻

5. 工具类代码骨架

5.1 ConversationIdUtil

package com.xxx.im.common.core.util;

import com.xxx.im.common.core.constant.SessionType;

public final class ConversationIdUtil {
    private ConversationIdUtil() {
    }

    public static String buildConversationId(int sessionType, String senderId, String recvId, String groupId) {
        if (sessionType == SessionType.SINGLE) {
            return single(senderId, recvId);
        }
        if (sessionType == SessionType.GROUP) {
            return group(groupId);
        }
        if (sessionType == SessionType.NOTIFICATION) {
            return notification(recvId);
        }
        throw new IllegalArgumentException("unknown sessionType: " + sessionType);
    }

    public static String single(String userA, String userB) {
        return userA.compareTo(userB) < 0
            ? "c1:" + userA + ":" + userB
            : "c1:" + userB + ":" + userA;
    }

    public static String group(String groupId) {
        return "c2:" + groupId;
    }

    public static String notification(String userId) {
        return "c3:" + userId;
    }
}


⸻

5.2 MessageBlockUtil

package com.xxx.im.common.core.util;

public final class MessageBlockUtil {
    private MessageBlockUtil() {
    }

    public static final int BLOCK_SIZE = 100;

    public static long blockNo(long seq) {
        return (seq - 1) / BLOCK_SIZE;
    }

    public static int index(long seq) {
        return (int) ((seq - 1) % BLOCK_SIZE);
    }

    public static long startSeq(long blockNo) {
        return blockNo * BLOCK_SIZE + 1;
    }

    public static long endSeq(long blockNo) {
        return (blockNo + 1) * BLOCK_SIZE;
    }

    public static String docId(String conversationId, long seq) {
        return conversationId + ":" + blockNo(seq);
    }

    public static String docIdByBlockNo(String conversationId, long blockNo) {
        return conversationId + ":" + blockNo;
    }
}


⸻

6. 通用基础 DTO

6.1 BaseResponse

package com.xxx.im.common.core.model;

import java.io.Serializable;

public class BaseResponse<T> implements Serializable {
    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> BaseResponse<T> ok(T data) {
        BaseResponse<T> resp = new BaseResponse<>();
        resp.setSuccess(true);
        resp.setCode("0");
        resp.setMessage("OK");
        resp.setData(data);
        return resp;
    }

    public static <T> BaseResponse<T> fail(String code, String message) {
        BaseResponse<T> resp = new BaseResponse<>();
        resp.setSuccess(false);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}


⸻

7. Message DTO 清单

7.1 MessageOptions

package com.xxx.im.common.api.dto.message;

import java.io.Serializable;

public class MessageOptions implements Serializable {
    private boolean needHistory;
    private boolean needConversation;
    private boolean needUnreadCount;
    private boolean needOnlinePush;
    private boolean needOfflinePush;
    private boolean senderSync;
    private boolean notification;
    private boolean needLastMessage;

    public boolean isNeedHistory() { return needHistory; }
    public void setNeedHistory(boolean needHistory) { this.needHistory = needHistory; }
    public boolean isNeedConversation() { return needConversation; }
    public void setNeedConversation(boolean needConversation) { this.needConversation = needConversation; }
    public boolean isNeedUnreadCount() { return needUnreadCount; }
    public void setNeedUnreadCount(boolean needUnreadCount) { this.needUnreadCount = needUnreadCount; }
    public boolean isNeedOnlinePush() { return needOnlinePush; }
    public void setNeedOnlinePush(boolean needOnlinePush) { this.needOnlinePush = needOnlinePush; }
    public boolean isNeedOfflinePush() { return needOfflinePush; }
    public void setNeedOfflinePush(boolean needOfflinePush) { this.needOfflinePush = needOfflinePush; }
    public boolean isSenderSync() { return senderSync; }
    public void setSenderSync(boolean senderSync) { this.senderSync = senderSync; }
    public boolean isNotification() { return notification; }
    public void setNotification(boolean notification) { this.notification = notification; }
    public boolean isNeedLastMessage() { return needLastMessage; }
    public void setNeedLastMessage(boolean needLastMessage) { this.needLastMessage = needLastMessage; }
}


⸻

7.2 MessageDTO

查询和下发都可以复用这个基础体，再按场景裁剪。

package com.xxx.im.common.api.dto.message;

import java.io.Serializable;
import java.util.Map;

public class MessageDTO implements Serializable {
    private String conversationId;
    private Long seq;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer sessionType;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private Integer status;
    private MessageOptions options;
    private Boolean revoked;
    private Boolean deleted;
    private Boolean read;
    private Map<String, String> ext;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getRecvId() { return recvId; }
    public void setRecvId(String recvId) { this.recvId = recvId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Integer getSessionType() { return sessionType; }
    public void setSessionType(Integer sessionType) { this.sessionType = sessionType; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public MessageOptions getOptions() { return options; }
    public void setOptions(MessageOptions options) { this.options = options; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
    public Map<String, String> getExt() { return ext; }
    public void setExt(Map<String, String> ext) { this.ext = ext; }
}


⸻

7.3 MessageSummaryDTO

package com.xxx.im.common.api.dto.message;

import java.io.Serializable;

public class MessageSummaryDTO implements Serializable {
    private Long seq;
    private String serverMsgId;
    private String senderId;
    private Integer contentType;
    private String previewText;
    private Long sendTime;

    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
}


⸻

8. 发送接口 DTO

8.1 SendMessageReq

package com.xxx.im.common.api.dto.message;

import java.io.Serializable;
import java.util.Map;

public class SendMessageReq implements Serializable {
    private String requestId;
    private String senderId;
    private Integer sessionType;
    private String recvId;
    private String groupId;
    private String clientMsgId;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public Integer getSessionType() { return sessionType; }
    public void setSessionType(Integer sessionType) { this.sessionType = sessionType; }
    public String getRecvId() { return recvId; }
    public void setRecvId(String recvId) { this.recvId = recvId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
    public MessageOptions getOptions() { return options; }
    public void setOptions(MessageOptions options) { this.options = options; }
    public Map<String, String> getExt() { return ext; }
    public void setExt(Map<String, String> ext) { this.ext = ext; }
}


⸻

8.2 SendMessageResp

package com.xxx.im.common.api.dto.message;

import java.io.Serializable;

public class SendMessageResp implements Serializable {
    private boolean accepted;
    private String requestId;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
    private String code;
    private String message;

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}


⸻

9. Conversation DTO

9.1 ConversationStateDTO

package com.xxx.im.common.api.dto.conversation;

import com.xxx.im.common.api.dto.message.MessageSummaryDTO;
import java.io.Serializable;

public class ConversationStateDTO implements Serializable {
    private String conversationId;
    private Integer sessionType;
    private Long maxSeq;
    private Long minSeq;
    private MessageSummaryDTO lastMessage;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Integer getSessionType() { return sessionType; }
    public void setSessionType(Integer sessionType) { this.sessionType = sessionType; }
    public Long getMaxSeq() { return maxSeq; }
    public void setMaxSeq(Long maxSeq) { this.maxSeq = maxSeq; }
    public Long getMinSeq() { return minSeq; }
    public void setMinSeq(Long minSeq) { this.minSeq = minSeq; }
    public MessageSummaryDTO getLastMessage() { return lastMessage; }
    public void setLastMessage(MessageSummaryDTO lastMessage) { this.lastMessage = lastMessage; }
}


⸻

9.2 UserConversationStateDTO

package com.xxx.im.common.api.dto.conversation;

import java.io.Serializable;

public class UserConversationStateDTO implements Serializable {
    private String userId;
    private String conversationId;
    private Long readSeq;
    private Long userMinSeq;
    private Long userMaxSeq;
    private Integer unreadCount;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getReadSeq() { return readSeq; }
    public void setReadSeq(Long readSeq) { this.readSeq = readSeq; }
    public Long getUserMinSeq() { return userMinSeq; }
    public void setUserMinSeq(Long userMinSeq) { this.userMinSeq = userMinSeq; }
    public Long getUserMaxSeq() { return userMaxSeq; }
    public void setUserMaxSeq(Long userMaxSeq) { this.userMaxSeq = userMaxSeq; }
    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
}


⸻

10. Query DTO

10.1 PullBySeqRangeReq / Resp

package com.xxx.im.common.api.dto.query;

import com.xxx.im.common.api.dto.message.MessageDTO;
import java.io.Serializable;
import java.util.List;

public class PullBySeqRangeReq implements Serializable {
    private String userId;
    private String conversationId;
    private Long beginSeq;
    private Long endSeq;
    private Integer pageSize;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getBeginSeq() { return beginSeq; }
    public void setBeginSeq(Long beginSeq) { this.beginSeq = beginSeq; }
    public Long getEndSeq() { return endSeq; }
    public void setEndSeq(Long endSeq) { this.endSeq = endSeq; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}

package com.xxx.im.common.api.dto.query;

import com.xxx.im.common.api.dto.message.MessageDTO;
import java.io.Serializable;
import java.util.List;

public class PullBySeqRangeResp implements Serializable {
    private String conversationId;
    private Long effectiveMinSeq;
    private Long effectiveMaxSeq;
    private Long readSeq;
    private List<MessageDTO> messages;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getEffectiveMinSeq() { return effectiveMinSeq; }
    public void setEffectiveMinSeq(Long effectiveMinSeq) { this.effectiveMinSeq = effectiveMinSeq; }
    public Long getEffectiveMaxSeq() { return effectiveMaxSeq; }
    public void setEffectiveMaxSeq(Long effectiveMaxSeq) { this.effectiveMaxSeq = effectiveMaxSeq; }
    public Long getReadSeq() { return readSeq; }
    public void setReadSeq(Long readSeq) { this.readSeq = readSeq; }
    public List<MessageDTO> getMessages() { return messages; }
    public void setMessages(List<MessageDTO> messages) { this.messages = messages; }
}


⸻

10.2 PullBySeqListReq / Resp

package com.xxx.im.common.api.dto.query;

import java.io.Serializable;
import java.util.List;

public class PullBySeqListReq implements Serializable {
    private String userId;
    private String conversationId;
    private List<Long> seqList;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public List<Long> getSeqList() { return seqList; }
    public void setSeqList(List<Long> seqList) { this.seqList = seqList; }
}

package com.xxx.im.common.api.dto.query;

import com.xxx.im.common.api.dto.message.MessageDTO;
import java.io.Serializable;
import java.util.List;

public class PullBySeqListResp implements Serializable {
    private String conversationId;
    private List<MessageDTO> messages;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public List<MessageDTO> getMessages() { return messages; }
    public void setMessages(List<MessageDTO> messages) { this.messages = messages; }
}


⸻

11. Dispatch DTO

11.1 DispatchMessageReq / Resp

package com.xxx.im.common.api.dto.dispatch;

import java.io.Serializable;
import java.util.List;

public class DispatchMessageReq implements Serializable {
    private String userId;
    private List<String> connectionIds;
    private DispatchPayload payload;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getConnectionIds() { return connectionIds; }
    public void setConnectionIds(List<String> connectionIds) { this.connectionIds = connectionIds; }
    public DispatchPayload getPayload() { return payload; }
    public void setPayload(DispatchPayload payload) { this.payload = payload; }
}

package com.xxx.im.common.api.dto.dispatch;

import java.io.Serializable;
import java.util.Map;

public class DispatchPayload implements Serializable {
    private String conversationId;
    private Long seq;
    private String clientMsgId;
    private String serverMsgId;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private Map<String, String> ext;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
    public Map<String, String> getExt() { return ext; }
    public void setExt(Map<String, String> ext) { this.ext = ext; }
}

package com.xxx.im.common.api.dto.dispatch;

import java.io.Serializable;
import java.util.List;

public class DispatchMessageResp implements Serializable {
    private List<DispatchResult> results;

    public List<DispatchResult> getResults() { return results; }
    public void setResults(List<DispatchResult> results) { this.results = results; }
}

package com.xxx.im.common.api.dto.dispatch;

import java.io.Serializable;

public class DispatchResult implements Serializable {
    private String connectionId;
    private boolean success;
    private String code;
    private String message;

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}


⸻

12. RPC 接口代码骨架

12.1 MessageSendRpc

package com.xxx.im.common.api.rpc;

import com.xxx.im.common.api.dto.message.SendMessageReq;
import com.xxx.im.common.api.dto.message.SendMessageResp;

public interface MessageSendRpc {
    SendMessageResp sendMessage(SendMessageReq req);
}


⸻

12.2 MessageQueryRpc

package com.xxx.im.common.api.rpc;

import com.xxx.im.common.api.dto.conversation.ConversationStateDTO;
import com.xxx.im.common.api.dto.conversation.UserConversationStateDTO;
import com.xxx.im.common.api.dto.query.PullBySeqListReq;
import com.xxx.im.common.api.dto.query.PullBySeqListResp;
import com.xxx.im.common.api.dto.query.PullBySeqRangeReq;
import com.xxx.im.common.api.dto.query.PullBySeqRangeResp;

public interface MessageQueryRpc {
    PullBySeqRangeResp pullBySeqRange(PullBySeqRangeReq req);
    PullBySeqListResp pullBySeqList(PullBySeqListReq req);
    ConversationStateDTO getConversationState(String conversationId);
    UserConversationStateDTO getUserConversationState(String userId, String conversationId);
}


⸻

12.3 OnlineDispatchRpc

package com.xxx.im.common.api.rpc;

import com.xxx.im.common.api.dto.dispatch.DispatchMessageReq;
import com.xxx.im.common.api.dto.dispatch.DispatchMessageResp;

public interface OnlineDispatchRpc {
    DispatchMessageResp dispatchMessage(DispatchMessageReq req);
}


⸻

13. Kafka Event 代码骨架

13.1 IngressEvent

package com.xxx.im.common.api.event;

import com.xxx.im.common.api.dto.message.MessageOptions;
import java.io.Serializable;
import java.util.Map;

public class IngressEvent implements Serializable {
    private String requestId;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer sessionType;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getRecvId() { return recvId; }
    public void setRecvId(String recvId) { this.recvId = recvId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Integer getSessionType() { return sessionType; }
    public void setSessionType(Integer sessionType) { this.sessionType = sessionType; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
    public MessageOptions getOptions() { return options; }
    public void setOptions(MessageOptions options) { this.options = options; }
    public Map<String, String> getExt() { return ext; }
    public void setExt(Map<String, String> ext) { this.ext = ext; }
}


⸻

13.2 SequencedMessage

package com.xxx.im.common.api.event;

import com.xxx.im.common.api.dto.message.MessageOptions;
import java.io.Serializable;
import java.util.Map;

public class SequencedMessage implements Serializable {
    private String conversationId;
    private Long seq;
    private String clientMsgId;
    private String serverMsgId;
    private String senderId;
    private String recvId;
    private String groupId;
    private Integer sessionType;
    private Integer contentType;
    private byte[] content;
    private Long sendTime;
    private MessageOptions options;
    private Map<String, String> ext;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getRecvId() { return recvId; }
    public void setRecvId(String recvId) { this.recvId = recvId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Integer getSessionType() { return sessionType; }
    public void setSessionType(Integer sessionType) { this.sessionType = sessionType; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long sendTime) { this.sendTime = sendTime; }
    public MessageOptions getOptions() { return options; }
    public void setOptions(MessageOptions options) { this.options = options; }
    public Map<String, String> getExt() { return ext; }
    public void setExt(Map<String, String> ext) { this.ext = ext; }
}


⸻

13.3 HistoryEvent / DeliveryEvent / OfflinePushEvent

package com.xxx.im.common.api.event;

import java.io.Serializable;
import java.util.List;

public class HistoryEvent implements Serializable {
    private String conversationId;
    private Long beginSeq;
    private Long endSeq;
    private List<SequencedMessage> messages;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getBeginSeq() { return beginSeq; }
    public void setBeginSeq(Long beginSeq) { this.beginSeq = beginSeq; }
    public Long getEndSeq() { return endSeq; }
    public void setEndSeq(Long endSeq) { this.endSeq = endSeq; }
    public List<SequencedMessage> getMessages() { return messages; }
    public void setMessages(List<SequencedMessage> messages) { this.messages = messages; }
}

package com.xxx.im.common.api.event;

import java.io.Serializable;
import java.util.List;

public class DeliveryEvent implements Serializable {
    private String conversationId;
    private SequencedMessage message;
    private List<String> targetUserIds;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public SequencedMessage getMessage() { return message; }
    public void setMessage(SequencedMessage message) { this.message = message; }
    public List<String> getTargetUserIds() { return targetUserIds; }
    public void setTargetUserIds(List<String> targetUserIds) { this.targetUserIds = targetUserIds; }
}

package com.xxx.im.common.api.event;

import java.io.Serializable;
import java.util.Map;

public class OfflinePushEvent implements Serializable {
    private String userId;
    private String conversationId;
    private Long seq;
    private String serverMsgId;
    private String title;
    private String content;
    private Map<String, String> ext;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Map<String, String> getExt() { return ext; }
    public void setExt(Map<String, String> ext) { this.ext = ext; }
}


⸻

14. 各服务 package 结构建议

14.1 postoffice

com.xxx.im.postoffice
├── config
├── controller
├── ws
│   ├── handler
│   ├── session
│   └── codec
├── rpc
│   └── OnlineDispatchRpcImpl.java
├── service
│   ├── OnlineSessionManager.java
│   ├── MessageIngressFacade.java
│   └── OnlineRegistryService.java
├── repository
│   └── RedisOnlineRegistryRepository.java
└── metric


⸻

14.2 postbox

com.xxx.im.postbox
├── config
├── rpc
│   ├── MessageSendRpcImpl.java
│   └── MessageQueryRpcImpl.java
├── producer
│   └── IngressProducer.java
├── consumer
│   └── HistoryConsumer.java
├── service
│   ├── MessageSendService.java
│   ├── HistoryPersistService.java
│   ├── HistoryQueryService.java
│   └── MessageProjectionService.java
├── repository
│   ├── MessageBlockMongoRepository.java
│   ├── MessageIdMappingRepository.java
│   ├── RedisMessageCacheRepository.java
│   └── RedisConversationStateRepository.java
└── convert


⸻

14.3 postman

com.xxx.im.postman
├── config
├── consumer
│   └── IngressBatchConsumer.java
├── producer
│   ├── HistoryProducer.java
│   └── DeliveryProducer.java
├── service
│   ├── MessageArrangeService.java
│   ├── SeqAllocator.java
│   ├── MessagePolicyEngine.java
│   ├── ConversationStateWriter.java
│   └── MessageCacheWriter.java
├── repository
│   └── RedisStateRepository.java
└── convert


⸻

14.4 push

com.xxx.im.push
├── config
├── consumer
│   ├── DeliveryConsumer.java
│   └── OfflinePushConsumer.java
├── producer
│   └── OfflinePushProducer.java
├── rpc
│   └── OnlineDispatchClient.java
├── service
│   ├── DeliveryService.java
│   ├── OnlineTargetResolver.java
│   ├── OfflinePushService.java
│   └── VendorPushAdapter.java
├── repository
│   └── RedisOnlineQueryRepository.java
└── metric


⸻

15. 下一步最合适的工作

现在你已经有了：
	•	架构边界
	•	模块任务
	•	接口清单
	•	Redis / Mongo / MySQL 结构
	•	公共 DTO 骨架
	•	包结构建议