package com.cheeseocean.im.common.util;

import java.util.UUID;

public class IMUtils {

    private IMUtils() {}

    public static String generateOperationID() {
        return UUID.randomUUID().toString();
    }
}
