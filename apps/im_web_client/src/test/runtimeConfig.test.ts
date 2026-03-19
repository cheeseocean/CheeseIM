import { describe, expect, it, vi } from 'vitest';

import { createAppRuntimeConfig } from '../services/runtimeConfig';

describe('runtimeConfig', () => {
  it('defaults to fake services when no env overrides are provided', () => {
    const config = createAppRuntimeConfig({});

    expect(config.mode).toBe('fake');
    expect(config.authBaseUrl).toBe('');
    expect(config.imBaseUrl).toBe('');
    expect(config.wsUrl).toBe('ws://localhost:5147/ws');
  });

  it('reads real service endpoints from vite-style env', () => {
    const config = createAppRuntimeConfig({
      VITE_IM_SERVICE_MODE: 'real',
      VITE_AUTH_BASE_URL: 'http://127.0.0.1:18084',
      VITE_IM_BASE_URL: 'http://127.0.0.1:18082',
      VITE_IM_WS_URL: 'ws://127.0.0.1:5147/ws',
    });

    expect(config.mode).toBe('real');
    expect(config.authBaseUrl).toBe('http://127.0.0.1:18084');
    expect(config.imBaseUrl).toBe('http://127.0.0.1:18082');
    expect(config.wsUrl).toBe('ws://127.0.0.1:5147/ws');
  });
});
