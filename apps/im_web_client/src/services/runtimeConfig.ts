export interface AppRuntimeConfig {
  mode: 'fake' | 'real';
  authBaseUrl: string;
  imBaseUrl: string;
  wsUrl: string;
}

export function createAppRuntimeConfig(
  env: Record<string, string | undefined> = import.meta.env as Record<string, string | undefined>,
): AppRuntimeConfig {
  return {
    mode: env.VITE_IM_SERVICE_MODE === 'real' ? 'real' : 'fake',
    authBaseUrl: env.VITE_AUTH_BASE_URL ?? '',
    imBaseUrl: env.VITE_IM_BASE_URL ?? '',
    wsUrl: env.VITE_IM_WS_URL ?? 'ws://localhost:5147/ws',
  };
}
