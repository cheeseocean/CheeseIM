import type { AuthSession, GatewayConnection, LoginCredentials, SessionState, WsTicket } from '../domain/types';

export interface SessionStore {
  getState(): SessionState;
  subscribe(listener: () => void): () => void;
  startSignIn(input: LoginCredentials): void;
  setAuthenticated(session: AuthSession): void;
  setTicket(ticket: WsTicket): void;
  setConnected(connection: GatewayConnection): void;
  setReconnecting(message?: string): void;
  handleForceLogout(message: string): void;
  setError(message: string): void;
  reset(): void;
}

interface SessionStoreOptions {
  environmentLabel?: string;
  transportLabel?: string;
}

const initialState: SessionState = {
  stage: 'signed_out',
  lifecycle: 'offline',
  profile: null,
  sessionId: null,
  deviceId: null,
  deviceName: 'Studio Browser',
  platform: 'web',
  accessToken: null,
  refreshToken: null,
  accessExpireAt: null,
  refreshExpireAt: null,
  wsTicket: null,
  wsTicketExpireAt: null,
  wsUrl: null,
  connId: null,
  statusLabel: 'Offline',
  ticketStatusLabel: 'Awaiting login',
  transportLabel: 'Mock Gateway',
  environmentLabel: 'Fake Auth · Fake Gateway · Local Mode',
};

export function createSessionStore(options: SessionStoreOptions = {}): SessionStore {
  let state: SessionState = {
    ...initialState,
    environmentLabel: options.environmentLabel ?? initialState.environmentLabel,
    transportLabel: options.transportLabel ?? initialState.transportLabel,
  };
  const listeners = new Set<() => void>();

  function emit(): void {
    listeners.forEach((listener) => listener());
  }

  function patch(partial: Partial<SessionState>): void {
    state = { ...state, ...partial };
    emit();
  }

  return {
    getState() {
      return state;
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    startSignIn(input) {
      patch({
        stage: 'signing_in',
        lifecycle: 'offline',
        deviceName: input.deviceName,
        platform: input.platform,
        statusLabel: 'Signing in',
        ticketStatusLabel: 'Awaiting login',
        errorMessage: undefined,
      });
    },
    setAuthenticated(session) {
      patch({
        stage: 'issuing_ticket',
        profile: session.profile,
        sessionId: session.sessionId,
        deviceId: session.deviceId,
        deviceName: session.deviceName,
        platform: session.platform,
        accessToken: session.tokens.accessToken,
        refreshToken: session.tokens.refreshToken,
        accessExpireAt: session.tokens.accessExpireAt,
        refreshExpireAt: session.tokens.refreshExpireAt,
        statusLabel: 'Session ready',
        ticketStatusLabel: 'Issuing ws ticket',
      });
    },
    setTicket(ticket) {
      patch({
        stage: 'connecting',
        lifecycle: 'connecting',
        wsTicket: ticket.ticket,
        wsTicketExpireAt: ticket.expireAt,
        wsUrl: ticket.wsUrl,
        statusLabel: 'Connecting',
        ticketStatusLabel: 'WS ticket issued',
      });
    },
    setConnected(connection) {
      patch({
        stage: 'connected',
        lifecycle: connection.lifecycle,
        connId: connection.connId,
        statusLabel: 'Connected',
        transportLabel: connection.transportLabel,
        errorMessage: undefined,
      });
    },
    setReconnecting(message) {
      patch({
        stage: 'connecting',
        lifecycle: 'reconnecting',
        statusLabel: 'Reconnecting',
        errorMessage: message,
      });
    },
    handleForceLogout(message) {
      patch({
        stage: 'error',
        lifecycle: 'offline',
        statusLabel: 'Session revoked',
        errorMessage: message,
      });
    },
    setError(message) {
      patch({
        stage: 'error',
        lifecycle: 'offline',
        statusLabel: 'Connection failed',
        errorMessage: message,
      });
    },
    reset() {
      state = {
        ...initialState,
        environmentLabel: options.environmentLabel ?? initialState.environmentLabel,
        transportLabel: options.transportLabel ?? initialState.transportLabel,
      };
      emit();
    },
  };
}
