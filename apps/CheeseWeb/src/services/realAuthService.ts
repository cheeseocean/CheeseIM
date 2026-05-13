import type { AuthService } from './contracts';
import type { AuthSession, LoginCredentials, WsTicket } from '../domain/types';
import { fetchJson } from './http';

interface RealAuthServiceOptions {
  authBaseUrl: string;
  wsUrl: string;
}

interface AuthResponsePayload {
  userId: string;
  sessionId: string;
  accessToken: string;
  refreshToken: string;
  accessExpireAt: number;
  refreshExpireAt: number;
}

interface WsTicketPayload {
  ticket: string;
  expire_at: number;
  ws_url: string;
}

export function createRealAuthService(options: RealAuthServiceOptions): AuthService {
  return {
    async login(input: LoginCredentials): Promise<AuthSession> {
      const response = await fetchJson<AuthResponsePayload>(
        `${options.authBaseUrl}/api/auth/login`,
        {
          method: 'POST',
          body: JSON.stringify({
            userId: input.account,
            password: input.password,
            deviceId: slugify(input.deviceName),
            deviceName: input.deviceName,
            platformId: mapPlatformId(input.platform),
            platform: input.platform,
          }),
        },
      );

      return {
        sessionId: response.sessionId,
        deviceId: slugify(input.deviceName),
        platform: input.platform,
        deviceName: input.deviceName,
        profile: {
          userId: response.userId,
          displayName: deriveDisplayName(input.account),
          title: 'Relay Operator',
          tenantName: 'Cheese Ocean Studio',
          avatarSeed: deriveAvatarSeed(input.account),
        },
        tokens: {
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          accessExpireAt: response.accessExpireAt,
          refreshExpireAt: response.refreshExpireAt,
        },
      };
    },
    async issueWsTicket(session: AuthSession): Promise<WsTicket> {
      const response = await fetchJson<WsTicketPayload>(
        `${options.authBaseUrl}/api/im/ws-ticket`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${session.tokens.accessToken}`,
          },
          body: JSON.stringify({
            device_id: session.deviceId,
            platform: session.platform,
            client_version: 'im_web_client',
          }),
        },
      );

      return {
        ticket: response.ticket,
        expireAt: response.expire_at,
        wsUrl: normalizeWsUrl(response.ws_url, options.wsUrl),
      };
    },
  };
}

function normalizeWsUrl(serverValue: string, fallback: string): string {
  if (serverValue.startsWith('ws://') || serverValue.startsWith('wss://')) {
    return serverValue;
  }
  if (serverValue === '/ws') {
    return fallback;
  }
  return fallback;
}

function slugify(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'web-client';
}

function deriveDisplayName(account: string): string {
  const [local] = account.split('@');
  return local
    .split(/[._-]/g)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ') || 'Relay Operator';
}

function deriveAvatarSeed(account: string): string {
  const [local] = account.split('@');
  return local
    .split(/[._-]/g)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || 'IM';
}

function mapPlatformId(platform: LoginCredentials['platform']): number {
  switch (platform) {
    case 'ios':
      return 1;
    case 'android':
      return 2;
    case 'pc':
      return 4;
    case 'web':
    default:
      return 5;
  }
}
