package com.cheeseocean.im.postoffice.util;

import io.netty.util.internal.SystemPropertyUtil;

public class SystemUtils {

    public static final String OS_NAME_PROP = "os.name";

    public static boolean isLinux() {
        return SystemPropertyUtil.get(OS_NAME_PROP).contains("Linux");
    }

    public static boolean isWindows(){
        return SystemPropertyUtil.get(OS_NAME_PROP).contains("Windows");
    }

    public static boolean isMac(){
        return SystemPropertyUtil.get(OS_NAME_PROP).contains("Mac");
    }
}
