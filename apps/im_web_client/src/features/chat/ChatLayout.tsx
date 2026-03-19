import type {
  ConversationSummary,
  FriendRequestSummary,
  FriendSummary,
  MessageItem,
  SessionState,
} from '../../domain/types';
import { ConnectionStatus } from './ConnectionStatus';
import { ConversationList } from './ConversationList';
import { MessagePanel } from './MessagePanel';

export function ChatLayout({
  session,
  conversations,
  friends,
  incomingRequests,
  activeConversationId,
  messages,
  isLoadingOlder,
  hasMore,
  onSelectConversation,
  onSendFriendRequest,
  onAcceptFriendRequest,
  onStartDirectConversation,
  onLoadOlder,
  onSend,
}: {
  session: SessionState;
  conversations: ConversationSummary[];
  friends: FriendSummary[];
  incomingRequests: FriendRequestSummary[];
  activeConversationId: string | null;
  messages: MessageItem[];
  isLoadingOlder: boolean;
  hasMore: boolean;
  onSelectConversation(conversationId: string): Promise<void>;
  onSendFriendRequest(friendUserId: string): Promise<void>;
  onAcceptFriendRequest(friendUserId: string): Promise<void>;
  onStartDirectConversation(friendUserId: string): Promise<void>;
  onLoadOlder(): Promise<void>;
  onSend(text: string): Promise<void>;
}) {
  const activeConversation =
    conversations.find((conversation) => conversation.conversationId === activeConversationId) ?? null;

  return (
    <main className="chat-shell">
      <ConnectionStatus session={session} />
      <div className="chat-grid">
        <ConversationList
          session={session}
          conversations={conversations}
          friends={friends}
          incomingRequests={incomingRequests}
          activeConversationId={activeConversationId}
          onSelect={(conversationId) => {
            void onSelectConversation(conversationId);
          }}
          onSendFriendRequest={(friendUserId) => {
            void onSendFriendRequest(friendUserId);
          }}
          onAcceptFriendRequest={(friendUserId) => {
            void onAcceptFriendRequest(friendUserId);
          }}
          onStartDirectConversation={(friendUserId) => {
            void onStartDirectConversation(friendUserId);
          }}
        />
        <MessagePanel
          conversation={activeConversation}
          messages={messages}
          isLoadingOlder={isLoadingOlder}
          hasMore={hasMore}
          onLoadOlder={onLoadOlder}
          onSend={onSend}
        />
      </div>
    </main>
  );
}
