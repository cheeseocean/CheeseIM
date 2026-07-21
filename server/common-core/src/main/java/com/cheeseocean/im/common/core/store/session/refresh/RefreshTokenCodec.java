package com.cheeseocean.im.common.core.store.session.refresh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh token 不透明值的生成、解析与哈希。
 *
 * <p>familyId 只用于定位同槽状态，授权强度来自随机 secret；Redis 只保存完整 token 的 SHA-256。</p>
 */
public final class RefreshTokenCodec {

    private static final String PREFIX = "rt";
    private static final int ID_LENGTH = 32;

    private RefreshTokenCodec() {
    }

    public static String newFamilyId() {
        return randomId();
    }

    public static String issue(String familyId) {
        if (!isFamilyId(familyId)) {
            throw new IllegalArgumentException("Invalid refresh token family id");
        }
        return PREFIX + "." + familyId + "." + randomId();
    }

    public static String familyId(String refreshToken) {
        if (refreshToken == null) {
            return null;
        }
        String[] parts = refreshToken.split("\\.", -1);
        if (parts.length != 3
                || !PREFIX.equals(parts[0])
                || !isFamilyId(parts[1])
                || !isFamilyId(parts[2])) {
            return null;
        }
        return parts[1];
    }

    public static String hash(String refreshToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * familyId 同时用于 Redis hash tag，因此只接受固定长度的小写十六进制值。
     */
    public static boolean isFamilyId(String value) {
        if (value == null || value.length() != ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
