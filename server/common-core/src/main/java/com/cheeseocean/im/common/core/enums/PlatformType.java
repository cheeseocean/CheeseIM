package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

public enum PlatformType {
    IOS(1, "ios", "iOS"),
    ANDROID(2, "android", "Android"),
    WINDOWS(3, "windows", "Windows"),
    OSX(4, "osx", "OSX"),
    WEB(5, "web", "WEB"),
    MINI_WEB(6, "miniweb", "MiniWeb"),
    LINUX(7, "linux", "Linux"),
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
