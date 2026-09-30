package com.cheeseocean.im.apiserver.config;

import com.cheeseocean.im.apiserver.ApiServerApplication;
import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.ApiAuthenticationInterceptor;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.apiserver.controller.AuthController;
import com.cheeseocean.im.apiserver.controller.BlacklistController;
import com.cheeseocean.im.apiserver.controller.ConversationController;
import com.cheeseocean.im.apiserver.controller.FriendController;
import com.cheeseocean.im.apiserver.controller.GroupController;
import com.cheeseocean.im.apiserver.controller.UserController;
import com.cheeseocean.im.apiserver.controller.WsTicketController;
import com.cheeseocean.im.apiserver.facade.ConversationFacade;
import com.cheeseocean.im.apiserver.facade.FriendFacade;
import com.cheeseocean.im.apiserver.facade.UserFacade;
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
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 独立 API 的真实 Spring/Dubbo consumer 装配验收。
 *
 * <p>只替换 Redis adapter 的 IO 入口；远程契约全部由生产 ReferenceBean 创建。
 * 禁用注册中心和引用预连接，保留 Dubbo 启动及引用配置解析，不需要外部中间件。</p>
 */
class ApiServerConsumerContextTest {

    private static final Set<Class<?>> WRITE_CONTRACTS = Set.of(
            AuthenticationService.class, SessionIssueService.class, FriendRelationService.class,
            UserInfoService.class, ConversationService.class, ConversationSyncService.class,
            ReadStateService.class, MessageMutationService.class);

    private static final Set<Class<?>> READ_CONTRACTS = Set.of(
            SessionQueryService.class, ConversationPermissionService.class,
            ConversationControlEventQueryService.class, MessageHistoryQueryService.class,
            GroupMembershipQueryService.class);

    @Test
    void consumerOnlyContextShouldWireAllHttpDependenciesWithUniqueReferencesAndSafeWriteRetries() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        new WebApplicationContextRunner()
                .withUserConfiguration(ApiServerApplication.class)
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withPropertyValues(
                        "cheeseim.state.auto-config-enabled=false",
                        "dubbo.application.name=api-consumer-context-test",
                        "dubbo.application.qos-enable=false",
                        "dubbo.application.register-consumer=false",
                        "dubbo.metrics.export-metrics-service=false",
                        "dubbo.registry.address=N/A",
                        "dubbo.registry.register=false",
                        "dubbo.consumer.injvm=false",
                        "dubbo.consumer.init=false",
                        "dubbo.consumer.check=false",
                        "dubbo.consumer.retries=2",
                        "dubbo.consumer.timeout=5000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(ServiceBean.class)).isEmpty();

                    Map<String, ReferenceBean> references = context.getBeansOfType(ReferenceBean.class);
                    assertThat(references).hasSize(WRITE_CONTRACTS.size() + READ_CONTRACTS.size());
                    assertThat(references.values()).extracting(ReferenceBean::getInterfaceClass)
                            .containsExactlyInAnyOrderElementsOf(allContracts());
                    for (ReferenceBean<?> reference : references.values()) {
                        Class<?> contract = reference.getInterfaceClass();
                        assertThat(context.getBeanNamesForType(contract)).hasSize(1);
                        assertThat(context.getBean(contract)).isSameAs(reference.getObject());
                        ReferenceConfig<?> config = reference.getReferenceConfig();
                        assertThat(config).isNotNull();
                        assertThat(config.isCheck()).isFalse();
                        assertThat(config.getConsumer().isInjvm()).isFalse();
                        assertThat(config.getConsumer().getRetries()).isEqualTo(2);
                        assertThat(config.getConsumer().getTimeout()).isEqualTo(5000);
                        if (WRITE_CONTRACTS.contains(contract)) {
                            assertThat(config.getRetries()).as(contract.getSimpleName()).isZero();
                        } else {
                            assertThat(config.getRetries()).as(contract.getSimpleName()).isEqualTo(2);
                        }
                        assertThat(config.getScopeModel().getConfigManager().getServices()).isEmpty();
                        for (var module : config.getScopeModel().getApplicationModel().getModuleModels()) {
                            assertThat(module.getConfigManager().getServices()).isEmpty();
                            assertThat(module.getServiceRepository().getExportedServices()).isEmpty();
                        }
                    }

                    // 验证真实 MVC 入口、编排及鉴权链均已创建，且共享同一个构造器代理。
                    for (Class<?> adapter : List.of(AuthController.class, WsTicketController.class,
                            UserController.class, FriendController.class, BlacklistController.class,
                            ConversationController.class, GroupController.class, UserFacade.class,
                            FriendFacade.class, ConversationFacade.class, AccessTokenSessionResolver.class,
                            ApiAuthenticationInterceptor.class, CurrentPrincipalArgumentResolver.class)) {
                        Object bean = context.getBean(adapter);
                        assertThat(bean).isNotNull();
                        for (var field : adapter.getDeclaredFields()) {
                            if (!allContracts().contains(field.getType())) {
                                continue;
                            }
                            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
                            assertThat(ReflectionTestUtils.getField(bean, field.getName()))
                                    .as(adapter.getSimpleName() + "." + field.getName())
                                    .isSameAs(context.getBean(field.getType()));
                        }
                    }
                });
    }

    private static Set<Class<?>> allContracts() {
        Set<Class<?>> contracts = new java.util.HashSet<>(WRITE_CONTRACTS);
        contracts.addAll(READ_CONTRACTS);
        return contracts;
    }
}
