package config

import "os"

const (
	defaultAPIBaseURL = "http://127.0.0.1:8080"
	defaultTCPAddr    = "127.0.0.1:9000"
	defaultDeviceID   = "cheesebox-dev"
	defaultPlatform   = "desktop"
)

type RuntimeConfig struct {
	APIBaseURL string
	TCPAddr    string
	DeviceID   string
	Platform   string
}

func LoadRuntimeConfig() RuntimeConfig {
	return RuntimeConfig{
		APIBaseURL: valueOrDefault("CHEESEBOX_API_BASE_URL", defaultAPIBaseURL),
		TCPAddr:    valueOrDefault("CHEESEBOX_TCP_ADDR", defaultTCPAddr),
		DeviceID:   valueOrDefault("CHEESEBOX_DEVICE_ID", defaultDeviceID),
		Platform:   valueOrDefault("CHEESEBOX_PLATFORM", defaultPlatform),
	}
}

func valueOrDefault(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok && value != "" {
		return value
	}
	return fallback
}
