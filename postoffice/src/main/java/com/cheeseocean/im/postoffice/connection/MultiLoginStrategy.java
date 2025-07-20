package com.cheeseocean.im.postoffice.connection;

import java.util.List;

/**
 * 多端登录策略
 * 参照OpenIM Server的多端登录策略实现
 * 
 * @author CheeseIM
 */
public enum MultiLoginStrategy {
    
    /**
     * 默认不踢策略 - 允许所有平台同时在线
     * 对应OpenIM的DefalutNotKick策略
     */
    DEFAULT_NOT_KICK(0, "默认不踢", "允许所有平台同时在线，不进行任何连接冲突处理"),
    
    /**
     * PC和其他策略 - PC端享有特殊权限，不被踢下线
     * 对应OpenIM的PCAndOther策略
     */
    PC_AND_OTHER(1, "PC和其他", "PC端享有特殊权限不被踢下线，其他平台按同终端踢下线策略处理"),
    
    /**
     * 同终端踢下线策略 - 同一平台类型只能有一个连接
     * 对应OpenIM的AllLoginButSameTermKick策略
     */
    SAME_TERMINAL_KICK(2, "同终端踢下线", "同一平台类型只能有一个连接，新连接会踢掉旧连接"),
    
    /**
     * 同类别踢下线策略 - 同一设备类别只能有一个连接
     * 对应OpenIM的AllLoginButSameClassKick策略
     */
    SAME_CLASS_KICK(3, "同类别踢下线", "同一设备类别只能有一个连接，移动端、PC端、Web端分别只能有一个");
    
    private final int code;
    private final String name;
    private final String description;
    
    MultiLoginStrategy(int code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取策略
     */
    public static MultiLoginStrategy fromCode(int code) {
        for (MultiLoginStrategy strategy : values()) {
            if (strategy.code == code) {
                return strategy;
            }
        }
        return DEFAULT_NOT_KICK;
    }
    
    /**
     * 判断是否需要踢掉旧连接
     * 
     * @param newConnection 新连接
     * @param existingConnections 已存在的连接列表
     * @return 需要踢掉的连接列表
     */
    public List<UserConnection> getConnectionsToKick(UserConnection newConnection, 
                                                    List<UserConnection> existingConnections) {
        switch (this) {
            case DEFAULT_NOT_KICK:
                return handleDefaultNotKick(newConnection, existingConnections);
                
            case PC_AND_OTHER:
                return handlePCAndOther(newConnection, existingConnections);
                
            case SAME_TERMINAL_KICK:
                return handleSameTerminalKick(newConnection, existingConnections);
                
            case SAME_CLASS_KICK:
                return handleSameClassKick(newConnection, existingConnections);
                
            default:
                return List.of();
        }
    }
    
    /**
     * 默认不踢策略处理
     */
    private List<UserConnection> handleDefaultNotKick(UserConnection newConnection, 
                                                     List<UserConnection> existingConnections) {
        // 不踢任何连接
        return List.of();
    }
    
    /**
     * PC和其他策略处理
     */
    private List<UserConnection> handlePCAndOther(UserConnection newConnection, 
                                                 List<UserConnection> existingConnections) {
        // 如果新连接是PC端，不踢任何连接
        if (isPCPlatform(newConnection.getPlatformID())) {
            return List.of();
        }
        
        // 如果新连接不是PC端，按同终端踢下线策略处理
        return handleSameTerminalKick(newConnection, existingConnections);
    }
    
    /**
     * 同终端踢下线策略处理
     */
    private List<UserConnection> handleSameTerminalKick(UserConnection newConnection, 
                                                       List<UserConnection> existingConnections) {
        // 找出同一平台的连接并踢掉
        return existingConnections.stream()
                .filter(conn -> conn.getPlatformID().equals(newConnection.getPlatformID()))
                .toList();
    }
    
    /**
     * 同类别踢下线策略处理
     */
    private List<UserConnection> handleSameClassKick(UserConnection newConnection, 
                                                    List<UserConnection> existingConnections) {
        // 找出同一设备类别的连接并踢掉
        PlatformClass newPlatformClass = getPlatformClass(newConnection.getPlatformID());
        return existingConnections.stream()
                .filter(conn -> getPlatformClass(conn.getPlatformID()) == newPlatformClass)
                .toList();
    }
    
    /**
     * 判断是否为PC平台
     */
    private boolean isPCPlatform(Integer platformID) {
        return platformID != null && (platformID == 3 || platformID == 4 || platformID == 7); // Windows, OSX, Linux
    }
    
    /**
     * 获取平台类别
     */
    private PlatformClass getPlatformClass(Integer platformID) {
        if (platformID == null) {
            return PlatformClass.UNKNOWN;
        }
        
        switch (platformID) {
            case 1: // iOS
            case 2: // Android
                return PlatformClass.MOBILE;
                
            case 3: // Windows
            case 4: // OSX
            case 7: // Linux
                return PlatformClass.PC;
                
            case 5: // WEB
            case 6: // MiniWeb
                return PlatformClass.WEB;
                
            default:
                return PlatformClass.UNKNOWN;
        }
    }
    
    /**
     * 平台类别枚举
     */
    private enum PlatformClass {
        MOBILE,   // 移动端
        PC,       // PC端
        WEB,      // Web端
        UNKNOWN   // 未知
    }
    
    @Override
    public String toString() {
        return "MultiLoginStrategy{" +
                "code=" + code +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
