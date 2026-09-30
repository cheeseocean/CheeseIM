package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.stereotype.Service;

/** 将认证结果交由连接管理器原子绑定，不在生命周期锁外修改连接身份。 */
@Service
public class ConnectionBindService {

    private final ConnectionManager connectionManager;

    public ConnectionBindService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /** 身份字段与认证状态必须由管理器在生命周期锁内一起提升，避免晚返回改写已移除对象。 */
    public boolean bindAuthenticated(UserConnection connection, SessionPrincipal session) {
        return connectionManager.addConnection(connection, session);
    }
}
