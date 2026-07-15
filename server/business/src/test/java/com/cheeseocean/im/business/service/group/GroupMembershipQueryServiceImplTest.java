package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.GroupMember;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link GroupMembershipQueryServiceImpl#queryGroupType(String)}：
 * 验证从 {@link GroupRepository} 读取群资料并映射出 {@link GroupTypeEnum}，
 * 用于 postmaster 在 ingress 热路径决定群消息扩散模式。
 *
 * <p>其它方法（queryConversationMembers/queryGroupMembers/isGroupMember）已有累计行为契约变更，
 * 在 P0-3 单独评审覆盖，本测试不打宽口径。
 */
class GroupMembershipQueryServiceImplTest {

    @Test
    void queryGroupTypeShouldReturnNormalGroupWhenGroupDocHasType() {
        GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
        GroupRepository groupRepo = mock(GroupRepository.class);
        Group group = new Group();
        group.setGroupId("crew");
        group.setGroupType(GroupTypeEnum.NORMAL_GROUP);
        when(groupRepo.findById("crew")).thenReturn(Optional.of(group));

        GroupMembershipQueryServiceImpl service = new GroupMembershipQueryServiceImpl(memberRepo, groupRepo);

        assertEquals(GroupTypeEnum.NORMAL_GROUP, service.queryGroupType("crew"));
    }

    @Test
    void queryGroupTypeShouldReturnSuperGroupForLargeGroup() {
        GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
        GroupRepository groupRepo = mock(GroupRepository.class);
        Group group = new Group();
        group.setGroupId("big");
        group.setGroupType(GroupTypeEnum.SUPER_GROUP);
        when(groupRepo.findById("big")).thenReturn(Optional.of(group));

        GroupMembershipQueryServiceImpl service = new GroupMembershipQueryServiceImpl(memberRepo, groupRepo);

        assertEquals(GroupTypeEnum.SUPER_GROUP, service.queryGroupType("big"));
    }

    @Test
    void queryGroupTypeShouldReturnNullWhenGroupMissing() {
        GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
        GroupRepository groupRepo = mock(GroupRepository.class);
        when(groupRepo.findById("ghost")).thenReturn(Optional.empty());

        GroupMembershipQueryServiceImpl service = new GroupMembershipQueryServiceImpl(memberRepo, groupRepo);

        // null 表示群不存在或异常，postmaster 端按 NORMAL_GROUP 兜底
        assertNull(service.queryGroupType("ghost"));
    }

    @Test
    void queryGroupTypeShouldReturnNullForBlankGroupId() {
        GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
        GroupRepository groupRepo = mock(GroupRepository.class);

        GroupMembershipQueryServiceImpl service = new GroupMembershipQueryServiceImpl(memberRepo, groupRepo);

        assertNull(service.queryGroupType(null));
        assertNull(service.queryGroupType(""));
        assertNull(service.queryGroupType("   "));
    }

    @Test
    void queryConversationMembersShouldAcceptGroupConversationId() {
        GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
        GroupRepository groupRepo = mock(GroupRepository.class);
        GroupMember member = new GroupMember();
        member.setUserId("u1");
        when(memberRepo.findByGroupId("crew")).thenReturn(List.of(member));

        GroupMembershipQueryServiceImpl service = new GroupMembershipQueryServiceImpl(memberRepo, groupRepo);

        // 当前 ConversationIdUtil.group 产出 "g:{groupId}"，新前缀必须支持
        List<String> members = service.queryConversationMembers("g:crew");
        assertEquals(List.of("u1"), members);
    }

    @Test
    void queryConversationMembersShouldRejectUnknownPrefix() {
        GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
        GroupRepository groupRepo = mock(GroupRepository.class);
        GroupMembershipQueryServiceImpl service = new GroupMembershipQueryServiceImpl(memberRepo, groupRepo);

        assertEquals(List.of(), service.queryConversationMembers("unknown:legacy"));
    }
}
