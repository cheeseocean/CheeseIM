import type { GatewayClient, SendMessageRequest, SendTextRequest, SendTextResult } from './contracts';
import type { AuthSession, GatewayConnection, WsTicket } from '../domain/types';

export function createFakeGatewayClient(): GatewayClient {
  async function sendMessageInternal(_input: SendMessageRequest): Promise<SendTextResult> {
    await sleep(220);
    return {
      serverId: `msg_${Math.random().toString(36).slice(2, 10)}`,
      sentAt: Date.now(),
    };
  }

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
      return sendMessageInternal({
        conversationId: input.conversationId,
        recipientId: input.recipientId,
        localId: input.localId,
        content: input.text,
        contentType: 101,
        session: input.session,
      });
    },
    async sendMessage(_input: SendMessageRequest): Promise<SendTextResult> {
      return sendMessageInternal(_input);
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
