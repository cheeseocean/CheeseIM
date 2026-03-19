import type { GatewayClient, SendTextRequest, SendTextResult } from './contracts';
import type { AuthSession, GatewayConnection, WsTicket } from '../domain/types';

export function createFakeGatewayClient(): GatewayClient {
  return {
    async connect(_ticket: WsTicket, _session: AuthSession): Promise<GatewayConnection> {
      await sleep(240);
      return {
        connId: `conn_${Math.random().toString(36).slice(2, 10)}`,
        lifecycle: 'connected',
        transportLabel: 'Mock Gateway',
      };
    },
    async sendText(input: SendTextRequest): Promise<SendTextResult> {
      await sleep(220);
      return {
        serverId: `msg_${Math.random().toString(36).slice(2, 10)}`,
        sentAt: Date.now(),
      };
    },
    subscribe() {
      return () => {};
    },
  };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
