const DEFAULT_TOKEN_SECRET_KEY = 'cheeseim-dev-secret-key';
const DEFAULT_TOKEN_EXPIRATION_MS = 24 * 60 * 60 * 1000;

export interface TokenConfig {
  secretKey: string;
  expirationMs: number;
}

export function getTokenConfig(): TokenConfig {
  const env = import.meta.env;
  const expirationMs = Number(env.VITE_IM_TOKEN_EXPIRATION_MS ?? DEFAULT_TOKEN_EXPIRATION_MS);

  return {
    secretKey: env.VITE_IM_TOKEN_SECRET_KEY?.trim() || DEFAULT_TOKEN_SECRET_KEY,
    expirationMs:
      Number.isFinite(expirationMs) && expirationMs > 0
        ? expirationMs
        : DEFAULT_TOKEN_EXPIRATION_MS,
  };
}
