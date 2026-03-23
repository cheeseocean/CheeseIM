package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.core.enums.PlatformType;

import java.util.List;

/**
 * Multi-device login strategy constants for the gateway.
 */
public enum MultiLoginStrategy {
    
    /**
     * 默认不踢策略 - 允许所有平台同时在线。
     */
    DEFAULT_NOT_KICK(0, "默认不踢", "允许所有平台同时在线，不进行任何连接冲突处理"),
    
    /**
     * PC和其他策略 - PC端享有特殊权限，不被踢下线。
     */
    PC_AND_OTHER(1, "PC和其他", "PC端享有特殊权限不被踢下线，其他平台按同终端踢下线策略处理"),
    
    /**
     * 同终端踢下线策略 - 同一平台类型只能有一个连接。
     */
    SAME_TERMINAL_KICK(2, "同终端踢下线", "同一平台类型只能有一个连接，新连接会踢掉旧连接"),
    
    /**
     * 同类别踢下线策略 - 同一设备类别只能有一个连接。
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
     * Resolves a strategy by numeric code.
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
     * Returns the existing connections that should be disconnected.
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
    
    private List<UserConnection> handleDefaultNotKick(UserConnection newConnection, 
                                                     List<UserConnection> existingConnections) {
        return List.of();
    }
    
    private List<UserConnection> handlePCAndOther(UserConnection newConnection, 
                                                 List<UserConnection> existingConnections) {
        if (isPCPlatform(newConnection.getPlatformID())) {
            return List.of();
        }
        
        return handleSameTerminalKick(newConnection, existingConnections);
    }
    
    private List<UserConnection> handleSameTerminalKick(UserConnection newConnection, 
                                                       List<UserConnection> existingConnections) {
        return existingConnections.stream()
                .filter(conn -> conn.getPlatformID().equals(newConnection.getPlatformID()))
                .toList();
    }
    
    private List<UserConnection> handleSameClassKick(UserConnection newConnection, 
                                                    List<UserConnection> existingConnections) {
        PlatformClass newPlatformClass = getPlatformClass(newConnection.getPlatformID());
        return existingConnections.stream()
                .filter(conn -> getPlatformClass(conn.getPlatformID()) == newPlatformClass)
                .toList();
    }
    
    private boolean isPCPlatform(Integer platformID) {
        return PlatformType.fromCode(platformID).isPc();
    }
    
    private PlatformClass getPlatformClass(Integer platformID) {
        PlatformType platformType = PlatformType.fromCode(platformID);
        if (platformType.isMobile()) {
            return PlatformClass.MOBILE;
        }
        if (platformType.isPc()) {
            return PlatformClass.PC;
        }
        if (platformType.isWeb()) {
            return PlatformClass.WEB;
        }
        return PlatformClass.UNKNOWN;
    }
    
    private enum PlatformClass {
        MOBILE,
        PC,
        WEB,
        UNKNOWN
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
