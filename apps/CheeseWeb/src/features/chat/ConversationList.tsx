import React from 'react';

import type {
  ConversationSummary,
  FriendRequestSummary,
  FriendSummary,
} from '../../domain/types';
import type { UiCopy } from '../../app/ui';

export function ConversationList({
  copy,
  conversations,
  friends,
  incomingRequests,
  outgoingRequests,
  activeConversationId,
  onSelect,
  onSendFriendRequest,
  onAcceptFriendRequest,
  onRejectFriendRequest,
  onCancelFriendRequest,
  onStartDirectConversation,
}: {
  copy: UiCopy;
  conversations: ConversationSummary[];
  friends: FriendSummary[];
  incomingRequests: FriendRequestSummary[];
  outgoingRequests: FriendRequestSummary[];
  activeConversationId: string | null;
  onSelect(conversationId: string): void;
  onSendFriendRequest(friendUserId: string, requestMessage: string): void;
  onAcceptFriendRequest(friendUserId: string): void;
  onRejectFriendRequest(friendUserId: string): void;
  onCancelFriendRequest(friendUserId: string): void;
  onStartDirectConversation(friendUserId: string): void;
}) {
  const [friendUserId, setFriendUserId] = React.useState('');
  const [requestMessage, setRequestMessage] = React.useState('');
  const [activeTab, setActiveTab] = React.useState<'conversations' | 'friends'>('conversations');

  return (
    <aside className="left-panel conversation-panel">
      <div className="tabs" role="tablist" aria-label="Conversation sections">
        <button
          className={activeTab === 'conversations' ? 'tab active' : 'tab'}
          type="button"
          onClick={() => setActiveTab('conversations')}
        >
          {copy.chat.conversations}
          <span className="tab-count">{conversations.length}</span>
        </button>
        <button
          className={activeTab === 'friends' ? 'tab active' : 'tab'}
          type="button"
          onClick={() => setActiveTab('friends')}
        >
          {copy.chat.friends}
          <span className="tab-count">{friends.length}</span>
        </button>
      </div>

      <label className="search-box">
        <input aria-label={copy.chat.search} placeholder={copy.chat.search} />
      </label>

      <div className={activeTab === 'conversations' ? 'panel-section is-active' : 'panel-section is-hidden'}>
        <div className="conversation-list-header">
          <h3>{copy.chat.recents}</h3>
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
                  className="conversation-avatar"
                  style={{ background: conversation.accentColor }}
                  aria-hidden="true"
                >
                  {conversation.title.slice(0, 2).toUpperCase()}
                </span>
                <span className="conversation-copy">
                  <strong>{conversation.title}</strong>
                  <small>{conversation.lastMessagePreview}</small>
                </span>
                <span className="conversation-meta">
                  <span className="conversation-time">
                    {new Date(conversation.lastMessageTime).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                  {conversation.unreadCount <= 0 ? null : (
                    <span className="unread-badge">{conversation.unreadCount}</span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className={activeTab === 'friends' ? 'panel-section is-active' : 'panel-section is-hidden'}>
        <div className="conversation-list-header friend-header">
          <h3>{copy.chat.incoming}</h3>
          <span>{incomingRequests.length}</span>
        </div>

        <ul className="friend-list">
          {incomingRequests.map((request) => (
            <li key={request.userId}>
              <div className="friend-row">
                <span className="friend-avatar">{request.avatarSeed}</span>
                <span className="friend-copy">
                  <strong>{request.displayName}</strong>
                  <small>{request.requestMessage ?? request.userId}</small>
                </span>
                <div className="friend-actions">
                  <button
                    className="friend-inline-action"
                    type="button"
                    onClick={() => onAcceptFriendRequest(request.userId)}
                  >
                    {copy.chat.accept}
                  </button>
                  <button
                    className="friend-inline-action"
                    type="button"
                    onClick={() => onRejectFriendRequest(request.userId)}
                  >
                    {copy.chat.reject}
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>

        <div className="conversation-list-header friend-header">
          <h3>{copy.chat.outgoing}</h3>
          <span>{outgoingRequests.length}</span>
        </div>

        <form
          className="friend-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (friendUserId.trim() === '') {
              return;
            }
            onSendFriendRequest(friendUserId.trim(), requestMessage.trim());
            setFriendUserId('');
            setRequestMessage('');
          }}
        >
          <label>
            <span>{copy.chat.sendRequest}</span>
            <input
              aria-label={copy.chat.sendRequest}
              placeholder="user_id"
              value={friendUserId}
              onChange={(event) => setFriendUserId(event.target.value)}
            />
          </label>
          <label>
            <span>{copy.chat.requestMessage}</span>
            <input
              aria-label={copy.chat.requestMessage}
              placeholder={copy.chat.requestMessage}
              value={requestMessage}
              onChange={(event) => setRequestMessage(event.target.value)}
            />
          </label>
          <button className="action-btn friend-add-action" type="submit">
            {copy.chat.request}
          </button>
        </form>

        <ul className="friend-list">
          {outgoingRequests.map((request) => (
            <li key={request.userId}>
              <div className="friend-row">
                <span className="friend-avatar">{request.avatarSeed}</span>
                <span className="friend-copy">
                  <strong>{request.displayName}</strong>
                  <small>{request.requestMessage ?? request.userId}</small>
                </span>
                {request.status === 'accepted' ? (
                  <button className="friend-inline-action" type="button" onClick={() => onStartDirectConversation(request.userId)}>
                    {copy.chat.friendChat}
                  </button>
                ) : (
                  <button className="friend-inline-action" type="button" onClick={() => onCancelFriendRequest(request.userId)}>
                    {copy.chat.cancel}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>

        <div className="conversation-list-header friend-header">
          <h3>{copy.chat.friendsTitle}</h3>
          <span>{friends.length}</span>
        </div>

        <ul className="friend-list">
          {friends.map((friend) => (
            <li key={friend.userId}>
              <button className="friend-row" type="button" onClick={() => onStartDirectConversation(friend.userId)}>
                <span className="friend-avatar">{friend.avatarSeed}</span>
                <span className="friend-copy">
                  <strong>{friend.displayName}</strong>
                  <small>{friend.userId}</small>
                </span>
                <span className="friend-action">{copy.chat.friendChat}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
}
