export const commandTypes = {
  connect: 1,
  auth: 10,
  heartbeat: 20,
  chatSend: 30,
  chatRecv: 32,
  chatRevoke: 34,
  forceLogout: 35,
  error: 90,
} as const;

export type EnvelopeCommand = (typeof commandTypes)[keyof typeof commandTypes];

export interface ClientEnvelope<T = unknown> {
  command: EnvelopeCommand;
  requestId: string;
  body: T;
}

export interface ServerEnvelope<T = unknown> {
  command: EnvelopeCommand;
  requestId: string;
  body: T;
}

export interface WsTicketAuthBody {
  ticket: string;
}

export interface SendMessagePayload {
  clientMsgID: string;
  recvID: string;
  content: string;
  contentType: number;
  sessionType: number;
  attachedInfo?: string;
}

export function buildConnectRequest(requestId: string): ClientEnvelope<Record<string, never>> {
  return {
    command: commandTypes.connect,
    requestId,
    body: {},
  };
}

export function buildAuthRequest<T extends Record<string, unknown>>(
  requestId: string,
  body: T,
): ClientEnvelope<T> {
  return {
    command: commandTypes.auth,
    requestId,
    body,
  };
}

export function buildHeartbeatRequest(
  requestId: string,
): ClientEnvelope<Record<string, never>> {
  return {
    command: commandTypes.heartbeat,
    requestId,
    body: {},
  };
}

export function buildSendMessageRequest(
  requestId: string,
  body: SendMessagePayload,
): ClientEnvelope<SendMessagePayload> {
  return {
    command: commandTypes.chatSend,
    requestId,
    body,
  };
}
