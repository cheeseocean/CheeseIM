package com.cheeseocean.im.common.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 已读高水位推进结果。
 *
 * <p>当 {@link #changed} 为 {@code true} 时，调用方应将该结果中的通知目标投递到在线端；
 * 业务层不依赖网关连接实现，以保证 HTTP、TCP 与 WS 入口共用同一状态变更路径。
 */
@Data
public class ReadSeqUpdate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID。 */
    private String conversationId;
    /** 产生已读状态的用户 ID。 */
    private String readerUserId;
    /** 实际生效的已读高水位。 */
    private long readSeq;
    /** 本次请求是否实际推进了高水位。 */
    private boolean changed;
    /** 需要收到已读通知的用户 ID，入口层负责按在线路由投递。 */
    private List<String> notifyUserIds = new ArrayList<>();
}
