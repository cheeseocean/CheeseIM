package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 群权限元数据缓存边界。
 *
 * <p>成员 mutation 只需精确失效群元数据；发送者权限缓存 key 包含成员版本，
 * 因而无需扫描删除上一版本的所有发送者 key。</p>
 */
@Component
public class GroupPermissionMetadataCache {

    private final CacheRegion<GroupPermissionMetadataSnapshot> region;

    public GroupPermissionMetadataCache(CacheStore cacheStore) {
        this.region = cacheStore.region(
                "group:send-metadata:v1:",
                GroupPermissionMetadataSnapshot.class,
                Duration.ofSeconds(2));
    }

    public GroupPermissionMetadataSnapshot getOrLoad(
            String groupId,
            Supplier<GroupPermissionMetadataSnapshot> loader) {
        return region.getOrLoad(groupId, loader);
    }

    public void evict(String groupId) {
        region.evict(groupId);
    }
}
