package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 好友申请领域对象。
 *
 * <p>每对用户之间最多保留一条申请记录，重新申请时覆盖旧记录。
 * 处理结果用 {@link HandleResultEnum} 枚举表达，避免魔法值。
 */
@Data
public class FriendRequest implements Serializable {

    /**
     * 文档唯一标识（"{fromUserId}:{toUserId}"）
     */
    private String           id;
    /**
     * 发起申请的用户 ID
     */
    private String           fromUserId;
    /**
     * 接收申请的用户 ID
     */
    private String           toUserId;
    /**
     * 处理结果。
     * PENDING=待处理，ACCEPTED=已同意，REJECTED=已拒绝。
     */
    private HandleResultEnum handleResult;
    /**
     * 申请附言
     */
    private String           reqMsg;
    /**
     * 实际处理者用户 ID
     */
    private String           handlerUserId;
    /**
     * 处理回复/原因
     */
    private String           handleMsg;
    /**
     * 处理时间（毫秒时间戳），0 表示尚未处理
     */
    private long             handleTime;
    /**
     * 扩展字段（JSON 字符串）
     */
    private String           ex;
    /**
     * 申请提交时间（毫秒时间戳）
     */
    private long             createTime;
    /**
     * 最近更新时间（毫秒时间戳）
     */
    private long             updatedAt;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 判断当前申请是否仍处于待处理状态。
     */
    public boolean isPending() {
        return handleResult == HandleResultEnum.PENDING;
    }

}
