package com.cheeseocean.im.apiserver.config;

import com.cheeseocean.im.common.api.auth.AuthenticationService;
import com.cheeseocean.im.common.api.conversation.ConversationControlEventQueryService;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.api.message.MessageHistoryQueryService;
import com.cheeseocean.im.common.api.message.MessageMutationService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.common.api.user.UserInfoService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * HTTP adapter 的远程契约装配，每种契约只声明一个共享引用。
 *
 * <p>Dubbo 在 Bean 工厂方法上解析引用配置，Controller/Facade 只需构造器注入契约，
 * 独立进程无需本地 provider。Primary 保证 all-in-one 中存在本地 provider 时，
 * HTTP 入口仍使用同一引用及重试策略；是否走 injvm 由部署配置决定。
 * 含写操作的混合契约整体禁重试，避免后续新增写调用继承查询的自动重试。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ApiServerConsumerConfiguration {

    /** 登录、刷新和撤销会改变认证状态，不自动重试。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<AuthenticationService> authenticationService() {
        return new ReferenceBean<>();
    }

    /** 签发/消费一次性票据，不自动重试。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<SessionIssueService> sessionIssueService() {
        return new ReferenceBean<>();
    }

    /** HTTP 鉴权共享会话查询引用。 */
    @Bean
    @Primary
    @DubboReference(check = false)
    public ReferenceBean<SessionQueryService> sessionQueryService() {
        return new ReferenceBean<>();
    }

    /** 好友与黑名单入口共享含写操作的契约。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<FriendRelationService> friendRelationService() {
        return new ReferenceBean<>();
    }

    /** 用户设置与会话展示共享用户契约，契约包含资料写操作。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<UserInfoService> userInfoService() {
        return new ReferenceBean<>();
    }

    /** 群列表与会话入口共享契约，会话设置及删除禁重试。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<ConversationService> conversationService() {
        return new ReferenceBean<>();
    }

    /** 同步契约还包含已读确认，因此整体禁重试。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<ConversationSyncService> conversationSyncService() {
        return new ReferenceBean<>();
    }

    /** 已读状态更新不自动重试。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<ReadStateService> readStateService() {
        return new ReferenceBean<>();
    }

    /** 会话可见性查询沿用全局查询策略。 */
    @Bean
    @Primary
    @DubboReference(check = false)
    public ReferenceBean<ConversationPermissionService> conversationPermissionService() {
        return new ReferenceBean<>();
    }

    /** 控制事件补拉沿用全局查询策略。 */
    @Bean
    @Primary
    @DubboReference(check = false)
    public ReferenceBean<ConversationControlEventQueryService> conversationControlEventQueryService() {
        return new ReferenceBean<>();
    }

    /** 历史查询只消费 postbox 的公共契约。 */
    @Bean
    @Primary
    @DubboReference(check = false)
    public ReferenceBean<MessageHistoryQueryService> messageHistoryQueryService() {
        return new ReferenceBean<>();
    }

    /** 撤回等 mutation 写操作不自动重试。 */
    @Bean
    @Primary
    @DubboReference(check = false, retries = 0)
    public ReferenceBean<MessageMutationService> messageMutationService() {
        return new ReferenceBean<>();
    }

    /** 群资料与成员资格只通过查询契约获取。 */
    @Bean
    @Primary
    @DubboReference(check = false)
    public ReferenceBean<GroupMembershipQueryService> groupMembershipQueryService() {
        return new ReferenceBean<>();
    }
}
