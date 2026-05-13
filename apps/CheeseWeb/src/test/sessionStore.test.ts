import { afterEach, describe, expect, it } from 'vitest';

import type { AuthSession } from '../domain/types';
import { createSessionStore } from '../state/sessionStore';

describe('sessionStore', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('restores a persisted auth session from localStorage', () => {
    const session: AuthSession = {
      sessionId: 'sess_test',
      deviceId: 'dev_test',
      deviceName: 'Studio Browser',
      platform: 'web',
      profile: {
        userId: 'u_operator',
        displayName: 'Avery Stone',
        title: 'Relay Operator',
        tenantName: 'Cheese Ocean Studio',
        avatarSeed: 'AS',
      },
      tokens: {
        accessToken: 'atk_test',
        refreshToken: 'rtk_test',
        accessExpireAt: 100,
        refreshExpireAt: 200,
      },
    };

    window.localStorage.setItem('cheeseim.web.auth-session', JSON.stringify(session));

    const store = createSessionStore();

    expect(store.getRestorableSession()).toEqual(session);
    expect(store.getState()).toMatchObject({
      stage: 'issuing_ticket',
      statusLabel: 'Restoring session',
      ticketStatusLabel: 'Reissuing ws ticket',
      profile: session.profile,
      sessionId: session.sessionId,
    });
  });
});
