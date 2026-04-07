package com.cheeseocean.im.postbox.facade;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.cheeseocean.im.common.api.dto.user.RegisterUserRequest;
import com.cheeseocean.im.common.api.dto.user.UpdateUserInfoRequest;
import com.cheeseocean.im.common.api.dto.user.UserInfoDTO;
import com.cheeseocean.im.common.api.user.UserInfoService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author xxxcrel
 * @date 2026/4/3 13:47
 */
@Service
public class UserServiceFacade {

    @DubboReference(check = false)
    private UserInfoService userInfoService;

    /** 透传批量用户信息查询。 */
    public List<UserInfoDTO> getUsersInfo(List<String> userIds) {
        return userInfoService.getUsersInfo(userIds);
    }

    /** 透传单用户信息查询。 */
    public UserInfoDTO getUserInfo(String userId) {
        return userInfoService.getUserInfo(userId);
    }

    /** 透传分页用户查询。 */
    public List<UserInfoDTO> pageQueryUsers(int pageNum, int pageSize, String keyword) {
        return userInfoService.pageQueryUsers(pageNum, pageSize, keyword);
    }

    /** 透传用户数量统计。 */
    public long countUsers(String keyword) {
        return userInfoService.countUsers(keyword);
    }

    /** 透传全量用户 ID 分页查询。 */
    public List<String> getAllUserIds(int pageNum, int pageSize) {
        return userInfoService.getAllUserIds(pageNum, pageSize);
    }

    /** 透传用户存在性过滤。 */
    public List<String> filterExistingUserIds(List<String> userIds) {
        return userInfoService.filterExistingUserIds(userIds);
    }

    /** 透传批量注册。 */
    public void registerUsers(List<RegisterUserRequest> requests) {
        userInfoService.registerUsers(requests);
    }

    /** 透传用户资料更新。 */
    public void updateUserInfo(String userId, UpdateUserInfoRequest request) {
        userInfoService.updateUserInfo(userId, request);
    }

    /** 透传通知账号创建。 */
    public String addNotificationAccount(String userId, String nickname, String faceUrl, int appManagerLevel) {
        return userInfoService.addNotificationAccount(userId, nickname, faceUrl, appManagerLevel);
    }

    /** 透传通知账号更新。 */
    public void updateNotificationAccount(String userId, String nickname, String faceUrl) {
        userInfoService.updateNotificationAccount(userId, nickname, faceUrl);
    }

    /** 透传通知账号搜索。 */
    public List<UserInfoDTO> searchNotificationAccounts(String keyword, Integer appManagerLevel, int pageNum, int pageSize) {
        return userInfoService.searchNotificationAccounts(keyword, appManagerLevel, pageNum, pageSize);
    }

    /** 透传单个通知账号查询。 */
    public UserInfoDTO getNotificationAccount(String userId) {
        return userInfoService.getNotificationAccount(userId);
    }

    /** 对全局接收选项做 facade 级缓存，避免消息链路重复远程查询。 */
    @Cached(name = RedisKeys.FIELD_GLOBAL_RECEIVE_OPTIONS, key = "#userId", expire = 300, cacheType = CacheType.REMOTE)
    public int getReceiveOptions(String userId) {
        return userInfoService.getReceiveOptions(userId);
    }
}
