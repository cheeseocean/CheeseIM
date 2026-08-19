package devauth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"
)

// GenerateAssertion 为本地联调模拟外部账户域签发一次性 HS256 登录断言。
// 该函数不属于 IM SDK，生产客户端不得持有签发密钥。
func GenerateAssertion(userID, issuer, audience, secret, jwtID string, now time.Time, lifetime time.Duration) (string, error) {
	if userID == "" || issuer == "" || audience == "" || jwtID == "" {
		return "", fmt.Errorf("user, issuer, audience and jti are required")
	}
	if len([]byte(secret)) < 32 {
		return "", fmt.Errorf("secret must contain at least 32 bytes")
	}
	if lifetime <= 0 || lifetime > time.Minute {
		return "", fmt.Errorf("lifetime must be within (0, 1m]")
	}
	header, _ := json.Marshal(map[string]string{"alg": "HS256", "typ": "JWT"})
	claims, _ := json.Marshal(map[string]any{
		"sub": userID, "iss": issuer, "aud": audience, "jti": jwtID,
		"iat": now.Unix(), "exp": now.Add(lifetime).Unix(),
	})
	unsigned := encode(header) + "." + encode(claims)
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(unsigned))
	return unsigned + "." + encode(mac.Sum(nil)), nil
}

func encode(value []byte) string {
	return base64.RawURLEncoding.EncodeToString(value)
}
