import React from 'react';

import type {
  ConversationSummary,
  FriendRequestSummary,
  FriendSummary,
  SessionState,
} from '../../domain/types';

export function ConversationList({
  session,
  conversations,
  friends,
  incomingRequests,
  activeConversationId,
  onSelect,
  onSendFriendRequest,
  onAcceptFriendRequest,
  onStartDirectConversation,
}: {
  session: SessionState;
  conversations: ConversationSummary[];
  friends: FriendSummary[];
  incomingRequests: FriendRequestSummary[];
  activeConversationId: string | null;
  onSelect(conversationId: string): void;
  onSendFriendRequest(friendUserId: string): void;
  onAcceptFriendRequest(friendUserId: string): void;
  onStartDirectConversation(friendUserId: string): void;
}) {
  const [friendUserId, setFriendUserId] = React.useState('');

  return (
    <aside className="conversation-panel">
      <div className="identity-card">
        <div className="identity-avatar">{session.profile?.avatarSeed ?? 'IM'}</div>
        <div>
          <p className="eyebrow">Connected As</p>
          <h2>{session.profile?.displayName ?? 'Offline Operator'}</h2>
          <p>{session.profile?.title ?? 'Awaiting session'}</p>
        </div>
      </div>

      <label className="conversation-search">
        <span>Search</span>
        <input aria-label="Search conversations" placeholder="Filter conversations" />
      </label>

      <div className="conversation-list-header">
        <h3>Conversations</h3>
        <span>{conversations.length}</span>
      </div>

      <ul className="conversation-list">
        {conversations.map((conversation) => (
          <li key={conversation.conversationId}>
            <button
              className={conversation.conversationId === activeConversationId ? 'conversation-row active' : 'conversation-row'}
              type="button"
              aria-pressed={conversation.conversationId === activeConversationId}
              onClick={() => onSelect(conversation.conversationId)}
            >
              <span
                className="conversation-accent"
                style={{ background: conversation.accentColor }}
                aria-hidden="true"
              />
              <span className="conversation-copy">
                <strong>{conversation.title}</strong>
                <small>{conversation.subtitle}</small>
                <em>{conversation.lastMessagePreview}</em>
              </span>
              {conversation.unreadCount <= 0 ? null : (
                <span className="unread-badge">{conversation.unreadCount}</span>
              )}
            </button>
          </li>
        ))}
      </ul>

      <div className="conversation-list-header friend-header">
        <h3>Requests</h3>
        <span>{incomingRequests.length}</span>
      </div>

      <ul className="friend-list">
        {incomingRequests.map((request) => (
          <li key={request.userId}>
            <div className="friend-row">
              <span className="friend-avatar">{request.avatarSeed}</span>
              <span className="friend-copy">
                <strong>{request.displayName}</strong>
                <small>{request.userId}</small>
              </span>
              <button
                className="friend-inline-action"
                type="button"
                onClick={() => onAcceptFriendRequest(request.userId)}
              >
                Accept
              </button>
            </div>
          </li>
        ))}
      </ul>

      <div className="conversation-list-header friend-header">
        <h3>Friends</h3>
        <span>{friends.length}</span>
      </div>

      <form
        className="friend-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (friendUserId.trim() === '') {
            return;
          }
          onSendFriendRequest(friendUserId.trim());
          setFriendUserId('');
        }}
      >
        <label>
          <span>Send request</span>
          <input
            aria-label="Send friend request"
            placeholder="Enter user ID"
            value={friendUserId}
            onChange={(event) => setFriendUserId(event.target.value)}
          />
        </label>
        <button className="ghost-action friend-add-action" type="submit">
          Request
        </button>
      </form>

      <ul className="friend-list">
        {friends.map((friend) => (
          <li key={friend.userId}>
            <button className="friend-row" type="button" onClick={() => onStartDirectConversation(friend.userId)}>
              <span className="friend-avatar">{friend.avatarSeed}</span>
              <span className="friend-copy">
                <strong>{friend.displayName}</strong>
                <small>{friend.userId}</small>
              </span>
              <span className="friend-action">Chat</span>
            </button>
          </li>
        ))}
      </ul>
    </aside>
  );
}
