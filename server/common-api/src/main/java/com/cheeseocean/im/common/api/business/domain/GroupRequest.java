package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 入群申请领域对象。
 *
 * <p>记录用户主动申请加群或被邀请入群的完整审批流程。
 * 处理结果用 {@link HandleResultEnum} 枚举表达。
 */
@Data
public class GroupRequest implements Serializable {

    /**
     * 文档唯一标识
     */
    private String           id;
    /**
     * 申请人用户 ID
     */
    private String           userId;
    /**
     * 目标群组 ID
     */
    private String           groupId;
    /**
     * 处理结果：PENDING / ACCEPTED / REJECTED
     */
    private HandleResultEnum handleResult;
    /**
     * 申请附言
     */
    private String           reqMsg;
    /**
     * 处理说明
     */
    private String           handledMsg;
    /**
     * 实际处理者用户 ID
     */
    private String           handleUserId;
    /**
     * 处理时间（毫秒时间戳），0 表示尚未处理
     */
    private long             handledTime;
    /**
     * 申请来源方式（业务方自定义）
     */
    private int              joinSource;
    /**
     * 邀请人用户 ID（被动邀请时填写）
     */
    private String           inviterUserId;
    /**
     * 扩展字段（JSON 字符串）
     */
    private String           ex;
    /**
     * 申请提交时间（毫秒时间戳）
     */
    private long             reqTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 判断当前申请是否仍待审批。
     */
    public boolean isPending() {
        return handleResult == HandleResultEnum.PENDING;
    }

}
