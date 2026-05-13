import type { AuthSession, GatewayConnection, LoginCredentials, SessionState, WsTicket } from '../domain/types';

export interface SessionStore {
  getState(): SessionState;
  subscribe(listener: () => void): () => void;
  getRestorableSession(): AuthSession | null;
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

const STORAGE_KEY = 'cheeseim.web.auth-session';

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
  const restoredSession = loadPersistedSession();
  if (restoredSession != null) {
    state = {
      ...state,
      stage: 'issuing_ticket',
      lifecycle: 'offline',
      profile: restoredSession.profile,
      sessionId: restoredSession.sessionId,
      deviceId: restoredSession.deviceId,
      deviceName: restoredSession.deviceName,
      platform: restoredSession.platform,
      accessToken: restoredSession.tokens.accessToken,
      refreshToken: restoredSession.tokens.refreshToken,
      accessExpireAt: restoredSession.tokens.accessExpireAt,
      refreshExpireAt: restoredSession.tokens.refreshExpireAt,
      statusLabel: 'Restoring session',
      ticketStatusLabel: 'Reissuing ws ticket',
    };
  }
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
    getRestorableSession() {
      return toAuthSession(state);
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
      persistSession(session);
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
      clearPersistedSession();
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
      clearPersistedSession();
      state = {
        ...initialState,
        environmentLabel: options.environmentLabel ?? initialState.environmentLabel,
        transportLabel: options.transportLabel ?? initialState.transportLabel,
      };
      emit();
    },
  };
}

function toAuthSession(state: SessionState): AuthSession | null {
  if (
    state.profile == null ||
    state.sessionId == null ||
    state.deviceId == null ||
    state.accessToken == null ||
    state.refreshToken == null ||
    state.accessExpireAt == null ||
    state.refreshExpireAt == null
  ) {
    return null;
  }
  return {
    sessionId: state.sessionId,
    deviceId: state.deviceId,
    deviceName: state.deviceName,
    platform: state.platform,
    profile: state.profile,
    tokens: {
      accessToken: state.accessToken,
      refreshToken: state.refreshToken,
      accessExpireAt: state.accessExpireAt,
      refreshExpireAt: state.refreshExpireAt,
    },
  };
}

function persistSession(session: AuthSession): void {
  const storage = getBrowserStorage();
  if (storage == null) {
    return;
  }
  storage.setItem(STORAGE_KEY, JSON.stringify(session));
}

function clearPersistedSession(): void {
  const storage = getBrowserStorage();
  if (storage == null) {
    return;
  }
  storage.removeItem(STORAGE_KEY);
}

function loadPersistedSession(): AuthSession | null {
  const storage = getBrowserStorage();
  if (storage == null) {
    return null;
  }
  const raw = storage.getItem(STORAGE_KEY);
  if (raw == null || raw.trim() === '') {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as AuthSession;
    if (
      parsed == null ||
      parsed.sessionId == null ||
      parsed.deviceId == null ||
      parsed.profile == null ||
      parsed.tokens?.accessToken == null ||
      parsed.tokens?.refreshToken == null ||
      parsed.tokens?.accessExpireAt == null ||
      parsed.tokens?.refreshExpireAt == null
    ) {
      clearPersistedSession();
      return null;
    }
    return parsed;
  } catch {
    clearPersistedSession();
    return null;
  }
}

function getBrowserStorage(): Storage | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const storage = window.localStorage as Partial<Storage> | undefined;
  if (
    storage == null ||
    typeof storage.getItem !== 'function' ||
    typeof storage.setItem !== 'function' ||
    typeof storage.removeItem !== 'function'
  ) {
    return null;
  }
  return storage as Storage;
}
