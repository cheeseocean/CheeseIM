import type { AuthSession } from '../domain/types';

export const wsMessageTypes = {
  connectReq: 1001,
  connectSuccess: 1002,
  connectFailed: 1003,
  authReq: 1101,
  authSuccess: 1102,
  authFailed: 1103,
  heartbeatReq: 1201,
  heartbeatResp: 1202,
  sendMsgReq: 2001,
  sendMsgResp: 2002,
  recvMsgNotify: 2003,
  forceLogoutNotify: 7002,
  errorResp: 9001,
  paramError: 9002,
  permissionError: 9003,
  internalError: 9004,
} as const;

export interface WSMessage<T = unknown> {
  msgType: number;
  operationID: string;
  data: T;
}

export interface SendMessagePayload {
  clientMsgID: string;
  recvID: string;
  content: string;
  contentType: number;
  sessionType: number;
}

export function buildConnectRequest(operationID: string): WSMessage<Record<string, never>> {
  return {
    msgType: wsMessageTypes.connectReq,
    operationID,
    data: {},
  };
}

export function buildAuthRequest(
  operationID: string,
  session: Pick<AuthSession, 'token' | 'userID' | 'platformID'>,
): WSMessage<Pick<AuthSession, 'token' | 'userID' | 'platformID'>> {
  return {
    msgType: wsMessageTypes.authReq,
    operationID,
    data: {
      token: session.token,
      userID: session.userID,
      platformID: session.platformID,
    },
  };
}

export function buildSendMessageRequest(
  operationID: string,
  payload: SendMessagePayload,
): WSMessage<SendMessagePayload> {
  return {
    msgType: wsMessageTypes.sendMsgReq,
    operationID,
    data: payload,
  };
}
