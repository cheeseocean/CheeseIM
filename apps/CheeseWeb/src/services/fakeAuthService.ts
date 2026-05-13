import type { AuthService } from './contracts';
import type { AuthSession, LoginCredentials, WsTicket } from '../domain/types';
import { buildSession } from './fakeData';

export function createFakeAuthService(): AuthService {
  return {
    async login(input: LoginCredentials): Promise<AuthSession> {
      await sleep(180);
      return buildSession(input);
    },
    async issueWsTicket(session): Promise<WsTicket> {
      await sleep(140);
      return {
        ticket: `wst_${Math.random().toString(36).slice(2, 10)}`,
        wsUrl: 'wss://mock-gateway.local/ws',
        expireAt: Date.now() + 60_000,
      };
    },
  };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
