import type {
  ConversationSummary,
  FriendRequestSummary,
  FriendSummary,
  MessageItem,
  SessionState,
} from '../../domain/types';
import type { UiCopy, UiLocale, UiTheme } from '../../app/ui';
import { ConnectionStatus } from './ConnectionStatus';
import { ConversationList } from './ConversationList';
import { MessagePanel } from './MessagePanel';

export function ChatLayout({
  copy,
  theme,
  locale,
  onThemeChange,
  onLocaleChange,
  onSignOut,
  session,
  conversations,
  friends,
  incomingRequests,
  outgoingRequests,
  activeConversationId,
  messages,
  typingDisplay,
  isLoadingOlder,
  hasMore,
  onSelectConversation,
  onSendFriendRequest,
  onAcceptFriendRequest,
  onRejectFriendRequest,
  onCancelFriendRequest,
  onStartDirectConversation,
  onLoadOlder,
  onClearMessages,
  onTyping,
  onRecallMessage,
  onSend,
}: {
  copy: UiCopy;
  theme: UiTheme;
  locale: UiLocale;
  onThemeChange(theme: UiTheme): void;
  onLocaleChange(locale: UiLocale): void;
  onSignOut(): void;
  session: SessionState;
  conversations: ConversationSummary[];
  friends: FriendSummary[];
  incomingRequests: FriendRequestSummary[];
  outgoingRequests: FriendRequestSummary[];
  activeConversationId: string | null;
  messages: MessageItem[];
  typingDisplay: string | null;
  isLoadingOlder: boolean;
  hasMore: boolean;
  onSelectConversation(conversationId: string): Promise<void>;
  onSendFriendRequest(friendUserId: string, requestMessage: string): Promise<void>;
  onAcceptFriendRequest(friendUserId: string): Promise<void>;
  onRejectFriendRequest(friendUserId: string): Promise<void>;
  onCancelFriendRequest(friendUserId: string): Promise<void>;
  onStartDirectConversation(friendUserId: string): Promise<void>;
  onLoadOlder(): Promise<void>;
  onClearMessages(): void;
  onTyping(text: string): void;
  onRecallMessage(message: MessageItem): Promise<void>;
  onSend(text: string): Promise<void>;
}) {
  const activeConversation =
    conversations.find((conversation) => conversation.conversationId === activeConversationId) ?? null;

  return (
    <main className="chat-shell">
      <ConnectionStatus
        copy={copy}
        locale={locale}
        theme={theme}
        onThemeChange={onThemeChange}
        onLocaleChange={onLocaleChange}
        onSignOut={onSignOut}
        session={session}
      />
      <div className="chat-grid">
        <ConversationList
          copy={copy}
          conversations={conversations}
          friends={friends}
          incomingRequests={incomingRequests}
          outgoingRequests={outgoingRequests}
          activeConversationId={activeConversationId}
          onSelect={(conversationId) => {
            void onSelectConversation(conversationId);
          }}
          onSendFriendRequest={(friendUserId, requestMessage) => {
            void onSendFriendRequest(friendUserId, requestMessage);
          }}
          onAcceptFriendRequest={(friendUserId) => {
            void onAcceptFriendRequest(friendUserId);
          }}
          onRejectFriendRequest={(friendUserId) => {
            void onRejectFriendRequest(friendUserId);
          }}
          onCancelFriendRequest={(friendUserId) => {
            void onCancelFriendRequest(friendUserId);
          }}
          onStartDirectConversation={(friendUserId) => {
            void onStartDirectConversation(friendUserId);
          }}
        />
        <MessagePanel
          copy={copy}
          conversation={activeConversation}
          messages={messages}
          typingDisplay={typingDisplay}
          isLoadingOlder={isLoadingOlder}
          hasMore={hasMore}
          onLoadOlder={onLoadOlder}
          onClear={onClearMessages}
          onTyping={onTyping}
          onRecall={onRecallMessage}
          onSend={onSend}
        />
      </div>
    </main>
  );
}
