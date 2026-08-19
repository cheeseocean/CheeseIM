package devauth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"strings"
	"testing"
	"time"
)

func TestGenerateAssertion(t *testing.T) {
	secret := "local-integration-secret-at-least-32-bytes"
	token, err := GenerateAssertion("user-1", "cheeseim-account", "cheeseim-im", secret, "jti-1", time.Unix(100, 0), time.Minute)
	if err != nil {
		t.Fatalf("GenerateAssertion() error = %v", err)
	}
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		t.Fatalf("token parts = %d", len(parts))
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(parts[0] + "." + parts[1]))
	want := base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
	if !hmac.Equal([]byte(parts[2]), []byte(want)) {
		t.Fatal("signature mismatch")
	}
}

func TestGenerateAssertionRejectsWeakSecret(t *testing.T) {
	if _, err := GenerateAssertion("user-1", "issuer", "audience", "weak", "jti", time.Now(), time.Minute); err == nil {
		t.Fatal("GenerateAssertion() error = nil")
	}
}
