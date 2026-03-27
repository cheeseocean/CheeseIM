package com.cheeseocean.im.common.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局模块日志常量。
 *
 * <p>每个业务模块持有一个专属 Logger，对应 logback-spring.xml 中各自的文件 Appender：
 * <ul>
 *   <li>{@link #POSTMASTER} → postmaster.log（消息路由、seq 分配、会话同步）</li>
 *   <li>{@link #POSTOFFICE}  → postoffice.log（WebSocket/TCP 长连接网关）</li>
 *   <li>{@link #POSTMAN}     → postman.log（在线推送 & 离线推送）</li>
 *   <li>{@link #POSTBOX}     → postbox.log（消息查询、会话 REST API）</li>
 *   <li>{@link #AUTHCENTER}  → authcenter.log（认证中心，JWT / WsTicket 签发）</li>
 *   <li>{@link #SOCIAL}      → social.log（社交关系，好友申请 / 关系维护）</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 *   private static final Logger log = CommonLoggers.POSTMASTER;
 * }</pre>
 *
 * <p>logback-spring.xml 通过 {@code additivity="true"} 保证每条日志同时输出到
 * 对应模块文件和控制台（由 root logger 负责控制台聚合输出）。
 *
 * @author xxxcrel
 */
public final class CommonLoggers {

    /** 消息流转核心模块：seq 分配、ingress 处理、history 持久化、会话状态同步 */
    public static final Logger POSTMASTER = LoggerFactory.getLogger("cheese-im-postmaster");

    /** 接入网关模块：WebSocket / TCP 长连接管理、在线路由维护 */
    public static final Logger POSTOFFICE  = LoggerFactory.getLogger("cheese-im-postoffice");

    /** 推送模块：在线投递（DeliveryEvent）、离线推送（APNs / FCM / 厂商通道） */
    public static final Logger POSTMAN     = LoggerFactory.getLogger("cheese-im-postman");

    /** 邮箱模块：消息历史查询、会话列表 REST API、附件鉴权 */
    public static final Logger POSTBOX     = LoggerFactory.getLogger("cheese-im-postbox");

    /** 认证中心：JWT 签发、WsTicket 颁发、Session 管理 */
    public static final Logger AUTHCENTER  = LoggerFactory.getLogger("cheese-im-authcenter");

    /** 社交关系模块：好友申请、好友关系维护、实时通知 */
    public static final Logger SOCIAL      = LoggerFactory.getLogger("cheese-im-social");

    private CommonLoggers() {}
}
