package com.cheeseocean.im.common.api.user;

import com.cheeseocean.im.common.api.dto.user.UserCommandDTO;

import java.util.List;

/**
 * 用户自定义命令 Dubbo 服务接口。
 *
 * <p>提供通用的用户端 key-value 数据存储能力，业务方可自定义 type 的含义
 * （如 1=收藏消息，2=快捷回复，3=置顶联系人等）。
 * 对应 OpenIM userServer 中 ProcessUserCommand* 系列接口。
 */
public interface UserCommandService {

    /**
     * 添加一条用户命令。
     *
     * @param userId 操作用户 ID
     * @param type   命令类型（业务自定义）
     * @param uuid   命令唯一标识
     * @param value  命令值
     * @param ex     扩展字段
     */
    void addUserCommand(String userId, int type, String uuid, String value, String ex);

    /**
     * 删除一条用户命令。
     *
     * @param userId 操作用户 ID
     * @param type   命令类型
     * @param uuid   命令唯一标识
     */
    void deleteUserCommand(String userId, int type, String uuid);

    /**
     * 更新一条用户命令的 value 和/或 ex 字段。
     * null 参数表示不修改对应字段。
     */
    void updateUserCommand(String userId, int type, String uuid, String value, String ex);

    /**
     * 查询指定类型下的所有命令。
     */
    List<UserCommandDTO> getUserCommands(String userId, int type);

    /**
     * 查询用户所有类型的所有命令。
     */
    List<UserCommandDTO> getAllUserCommands(String userId);
}
