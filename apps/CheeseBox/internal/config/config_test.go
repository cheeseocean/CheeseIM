package config

import "testing"

func TestLoadRuntimeConfig_UsesDefaultValuesWhenEnvUnset(t *testing.T) {
	t.Setenv("CHEESEBOX_API_BASE_URL", "")
	t.Setenv("CHEESEBOX_TCP_ADDR", "")
	t.Setenv("CHEESEBOX_DEVICE_ID", "")
	t.Setenv("CHEESEBOX_PLATFORM", "")

	cfg := LoadRuntimeConfig()

	if got, want := cfg.APIBaseURL, "http://127.0.0.1:18079"; got != want {
		t.Fatalf("APIBaseURL = %q, want %q", got, want)
	}
	if got, want := cfg.TCPAddr, "127.0.0.1:5148"; got != want {
		t.Fatalf("TCPAddr = %q, want %q", got, want)
	}
	if got, want := cfg.DeviceID, "cheesebox-dev"; got != want {
		t.Fatalf("DeviceID = %q, want %q", got, want)
	}
	if got, want := cfg.Platform, "cli"; got != want {
		t.Fatalf("Platform = %q, want %q", got, want)
	}
}

func TestLoadRuntimeConfig_UsesEnvOverrides(t *testing.T) {
	t.Setenv("CHEESEBOX_API_BASE_URL", "https://example.invalid/api")
	t.Setenv("CHEESEBOX_TCP_ADDR", "127.0.0.1:19191")
	t.Setenv("CHEESEBOX_DEVICE_ID", "device-override")
	t.Setenv("CHEESEBOX_PLATFORM", "web")

	cfg := LoadRuntimeConfig()

	if got, want := cfg.APIBaseURL, "https://example.invalid/api"; got != want {
		t.Fatalf("APIBaseURL = %q, want %q", got, want)
	}
	if got, want := cfg.TCPAddr, "127.0.0.1:19191"; got != want {
		t.Fatalf("TCPAddr = %q, want %q", got, want)
	}
	if got, want := cfg.DeviceID, "device-override"; got != want {
		t.Fatalf("DeviceID = %q, want %q", got, want)
	}
	if got, want := cfg.Platform, "web"; got != want {
		t.Fatalf("Platform = %q, want %q", got, want)
	}
}
