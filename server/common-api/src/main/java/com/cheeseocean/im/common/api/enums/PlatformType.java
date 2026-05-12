package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 客户端平台类型枚举。
 *
 * @author xxxcrel
 */
public enum PlatformType implements IEnum {
    /** iOS 平台。 */
    IOS(1, "ios", "iOS"),
    /** Android 平台。 */
    ANDROID(2, "android", "Android"),
    /** Windows 平台。 */
    WINDOWS(3, "windows", "Windows"),
    /** macOS 平台。 */
    OSX(4, "osx", "OSX"),
    /** Web 平台。 */
    WEB(5, "web", "WEB"),
    /** 小程序 Web 平台。 */
    MINI_WEB(6, "miniweb", "MiniWeb"),
    /** Linux 平台。 */
    LINUX(7, "linux", "Linux"),
    /**
     * 命令行平台
     */
    CLI(8, "cli", "CLI"),
    /** 未知平台。 */
    UNKNOWN(0, "unknown", "Unknown");

    private final int code;
    private final String wireName;
    private final String displayName;

    PlatformType(int code, String wireName, String displayName) {
        this.code = code;
        this.wireName = wireName;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getWireName() {
        return wireName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDesc() {
        return displayName;
    }

    public boolean isMobile() {
        return this == IOS || this == ANDROID;
    }

    public boolean isPc() {
        return this == WINDOWS || this == OSX || this == LINUX;
    }

    public boolean isWeb() {
        return this == WEB || this == MINI_WEB;
    }

    public static PlatformType fromCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElse(UNKNOWN);
    }

    public static PlatformType fromName(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN;
        }
        String normalized = name.trim().toLowerCase();
        return switch (normalized) {
            case "ios" -> IOS;
            case "android" -> ANDROID;
            case "windows" -> WINDOWS;
            case "osx", "mac", "macos" -> OSX;
            case "web" -> WEB;
            case "miniweb", "mini_web" -> MINI_WEB;
            case "linux" -> LINUX;
            default -> UNKNOWN;
        };
    }
}
