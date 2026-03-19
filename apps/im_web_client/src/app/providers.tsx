import React, { createContext, useContext } from 'react';

import { createConversationStore, type ConversationStore } from '../state/conversationStore';
import { createSessionStore, type SessionStore } from '../state/sessionStore';
import { createFakeAuthService } from '../services/fakeAuthService';
import { createFakeChatService } from '../services/fakeChatService';
import { createFakeGatewayClient } from '../services/fakeGatewayClient';
import { createRealAuthService } from '../services/realAuthService';
import { createRealChatService } from '../services/realChatService';
import { createRealGatewayClient } from '../services/realGatewayClient';
import type { AuthService, ChatService, GatewayClient } from '../services/contracts';
import { createAppRuntimeConfig } from '../services/runtimeConfig';

export interface AppDependencies {
  sessionStore: SessionStore;
  conversationStore: ConversationStore;
  authService: AuthService;
  chatService: ChatService;
  gatewayClient: GatewayClient;
}

const AppDependenciesContext = createContext<AppDependencies | null>(null);

export function AppProviders({
  dependencies,
  children,
}: React.PropsWithChildren<{ dependencies: AppDependencies }>) {
  return (
    <AppDependenciesContext.Provider value={dependencies}>
      {children}
    </AppDependenciesContext.Provider>
  );
}

export function useAppDependencies(): AppDependencies {
  const value = useContext(AppDependenciesContext);
  if (value == null) {
    throw new Error('AppDependencies are not available.');
  }
  return value;
}

export function createDefaultDependencies(): AppDependencies {
  const config = createAppRuntimeConfig();
  const usingReal = config.mode === 'real';

  return {
    sessionStore: createSessionStore({
      environmentLabel: usingReal
        ? 'AuthCenter · PostOffice · Postbox · Live Mode'
        : 'Fake Auth · Fake Gateway · Local Mode',
      transportLabel: usingReal ? 'PostOffice WebSocket' : 'Mock Gateway',
    }),
    conversationStore: createConversationStore(),
    authService: usingReal
      ? createRealAuthService({
          authBaseUrl: config.authBaseUrl,
          wsUrl: config.wsUrl,
        })
      : createFakeAuthService(),
    chatService: usingReal
      ? createRealChatService({
        authBaseUrl: config.authBaseUrl,
        imBaseUrl: config.imBaseUrl,
        })
      : createFakeChatService(),
    gatewayClient: usingReal ? createRealGatewayClient() : createFakeGatewayClient(),
  };
}
