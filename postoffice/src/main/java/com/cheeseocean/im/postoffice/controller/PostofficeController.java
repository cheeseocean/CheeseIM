package com.cheeseocean.im.postoffice.controller;

import com.cheeseocean.im.postoffice.auth.JwtAuthService;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.server.TcpServer;
import com.cheeseocean.im.postoffice.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Postoffice网关控制器
 * 提供REST API用于管理和监控WebSocket网关
 * 
 * @author CheeseIM
 */
@RestController
@RequestMapping("/api/v1/postoffice")
public class PostofficeController {
    
    private static final Logger logger = LoggerFactory.getLogger(PostofficeController.class);
    
    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private TcpServer tcpServer;

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private JwtAuthService jwtAuthService;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "CheeseIM Postoffice Gateway");
        result.put("timestamp", System.currentTimeMillis());

        // WebSocket服务器状态
        WebSocketServer.ServerStatus wsServerStatus = webSocketServer.getStatus();
        result.put("websocketServer", wsServerStatus);

        // TCP服务器状态
        TcpServer.ServerStatus tcpServerStatus = tcpServer.getStatus();
        result.put("tcpServer", tcpServerStatus);

        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取服务器状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getServerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("websocket", webSocketServer.getStatus());
        status.put("tcp", tcpServer.getStatus());
        return ResponseEntity.ok(status);
    }

    /**
     * 获取WebSocket服务器状态
     */
    @GetMapping("/status/websocket")
    public ResponseEntity<WebSocketServer.ServerStatus> getWebSocketServerStatus() {
        return ResponseEntity.ok(webSocketServer.getStatus());
    }

    /**
     * 获取TCP服务器状态
     */
    @GetMapping("/status/tcp")
    public ResponseEntity<TcpServer.ServerStatus> getTcpServerStatus() {
        return ResponseEntity.ok(tcpServer.getStatus());
    }
    
    /**
     * 获取连接统计信息
     */
    @GetMapping("/connections/stats")
    public ResponseEntity<Map<String, Object>> getConnectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConnections", connectionManager.getTotalConnectionCount());
        stats.put("onlineUsers", connectionManager.getOnlineUserCount());
        stats.put("multiLoginStrategy", connectionManager.getMultiLoginStrategy().getName());
        stats.put("connectionTimeout", connectionManager.getConnectionTimeoutMs());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取在线用户列表
     */
    @GetMapping("/users/online")
    public ResponseEntity<Set<String>> getOnlineUsers() {
        Set<String> onlineUsers = connectionManager.getOnlineUsers();
        return ResponseEntity.ok(onlineUsers);
    }
    
    /**
     * 检查用户是否在线
     */
    @GetMapping("/users/{userID}/online")
    public ResponseEntity<Map<String, Object>> checkUserOnline(@PathVariable String userID) {
        boolean isOnline = connectionManager.isUserOnline(userID);
        List<UserConnection> connections = connectionManager.getUserConnections(userID);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userID", userID);
        result.put("online", isOnline);
        result.put("connectionCount", connections.size());
        result.put("connections", connections.stream().map(conn -> {
            Map<String, Object> connInfo = new HashMap<>();
            connInfo.put("connectionID", conn.getConnectionID());
            connInfo.put("platformID", conn.getPlatformID());
            connInfo.put("platformName", conn.getPlatformName());
            connInfo.put("clientIP", conn.getClientIP());
            connInfo.put("connectTime", conn.getConnectTime());
            connInfo.put("lastActiveTime", conn.getLastActiveTime());
            connInfo.put("authenticated", conn.isAuthenticated());
            connInfo.put("heartbeatCount", conn.getHeartbeatCount());
            connInfo.put("sendMsgCount", conn.getSendMsgCount());
            connInfo.put("recvMsgCount", conn.getRecvMsgCount());
            return connInfo;
        }).toList());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 生成Token（用于测试）
     */
    @PostMapping("/auth/token")
    public ResponseEntity<Map<String, Object>> generateToken(@RequestParam String userID,
                                                            @RequestParam Integer platformID) {
        try {
            String token = jwtAuthService.generateToken(userID, platformID);
            
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userID", userID);
            result.put("platformID", platformID);
            result.put("expiration", System.currentTimeMillis() + 86400000); // 24小时后过期
            
            logger.info("Token generated via API: userID={}, platformID={}", userID, platformID);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to generate token: userID={}, platformID={}", userID, platformID, e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Token生成失败");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 验证Token
     */
    @PostMapping("/auth/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        try {
            var authResult = jwtAuthService.validateToken(token);
            
            Map<String, Object> result = new HashMap<>();
            result.put("valid", authResult.isSuccess());
            
            if (authResult.isSuccess()) {
                result.put("userID", authResult.getUserID());
                result.put("platformID", authResult.getPlatformID());
                result.put("expireTime", authResult.getExpireTime());
            } else {
                result.put("error", authResult.getErrorMessage());
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to validate token", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("valid", false);
            error.put("error", "Token验证失败");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 强制用户下线
     */
    @PostMapping("/users/{userID}/kick")
    public ResponseEntity<Map<String, Object>> kickUser(@PathVariable String userID,
                                                       @RequestParam(required = false) Integer platformID,
                                                       @RequestParam(defaultValue = "管理员操作") String reason) {
        try {
            List<UserConnection> connections = connectionManager.getUserConnections(userID);
            
            if (connections.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "用户不在线");
                return ResponseEntity.ok(result);
            }
            
            int kickedCount = 0;
            for (UserConnection connection : connections) {
                // 如果指定了平台ID，只踢掉指定平台的连接
                if (platformID != null && !platformID.equals(connection.getPlatformID())) {
                    continue;
                }
                
                connectionManager.kickConnection(connection, reason);
                kickedCount++;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("kickedConnections", kickedCount);
            result.put("reason", reason);
            
            logger.info("User kicked via API: userID={}, platformID={}, kickedCount={}, reason={}", 
                       userID, platformID, kickedCount, reason);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to kick user: userID={}, platformID={}", userID, platformID, e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "踢人操作失败");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 撤销用户Token
     */
    @PostMapping("/users/{userID}/revoke-token")
    public ResponseEntity<Map<String, Object>> revokeUserToken(@PathVariable String userID,
                                                              @RequestParam(required = false) Integer platformID) {
        try {
            int revokedCount;
            if (platformID != null) {
                // 撤销指定平台的Token
                boolean revoked = jwtAuthService.revokeToken(userID, platformID);
                revokedCount = revoked ? 1 : 0;
            } else {
                // 撤销用户的所有Token
                revokedCount = jwtAuthService.revokeAllUserTokens(userID);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("revokedTokens", revokedCount);
            
            logger.info("User tokens revoked via API: userID={}, platformID={}, revokedCount={}", 
                       userID, platformID, revokedCount);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to revoke user token: userID={}, platformID={}", userID, platformID, e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "撤销Token失败");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }
}
