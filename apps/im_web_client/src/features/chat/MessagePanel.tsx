import React from 'react';

import type { ConversationSummary, MessageItem } from '../../domain/types';

export function MessagePanel({
  conversation,
  messages,
  isLoadingOlder,
  hasMore,
  onLoadOlder,
  onSend,
}: {
  conversation: ConversationSummary | null;
  messages: MessageItem[];
  isLoadingOlder: boolean;
  hasMore: boolean;
  onLoadOlder(): Promise<void>;
  onSend(text: string): Promise<void>;
}) {
  const [text, setText] = React.useState('');

  if (conversation == null) {
    return (
      <section className="message-panel empty">
        <p className="eyebrow">No selection</p>
        <h3>Select a conversation</h3>
      </section>
    );
  }

  return (
    <section className="message-panel">
      <header className="message-panel-header">
        <div>
          <p className="eyebrow">Active Conversation</p>
          <h3>{conversation.title}</h3>
          <p>{conversation.subtitle}</p>
        </div>
        <div className="message-panel-meta">
          <span>{conversation.kind}</span>
          <span>{new Date(conversation.lastMessageTime).toLocaleTimeString()}</span>
        </div>
      </header>

      <div className="message-stream">
        {hasMore ? (
          <button className="ghost-action" type="button" onClick={() => void onLoadOlder()} disabled={isLoadingOlder}>
            {isLoadingOlder ? 'Loading older…' : 'Load older'}
          </button>
        ) : (
          <div className="history-cap">History fully loaded</div>
        )}

        <ol className="message-list">
          {messages.map((message) => (
            <li
              className={message.direction === 'outgoing' ? 'message-row outgoing' : 'message-row incoming'}
              key={message.localId}
            >
              <article className="message-bubble">
                <header>
                  <strong>{message.senderDisplay}</strong>
                  <span>{new Date(message.timestamp).toLocaleTimeString()}</span>
                </header>
                <p>{message.text}</p>
                <footer>
                  <span>{labelForStatus(message.status)}</span>
                  {message.failureReason == null ? null : <em>{message.failureReason}</em>}
                </footer>
              </article>
            </li>
          ))}
        </ol>
      </div>

      <form
        className="composer"
        onSubmit={async (event) => {
          event.preventDefault();
          const next = text.trim();
          if (next === '') {
            return;
          }
          setText('');
          await onSend(next);
        }}
      >
        <label className="composer-field">
          <span>Message input</span>
          <textarea
            aria-label="Message input"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Draft a direct message…"
            rows={3}
          />
        </label>
        <button className="primary-action" type="submit">
          Send message
        </button>
      </form>
    </section>
  );
}

function labelForStatus(status: MessageItem['status']): string {
  switch (status) {
    case 'sending':
      return 'Sending';
    case 'delivered':
      return 'Delivered';
    case 'failed':
      return 'Failed';
    case 'received':
      return 'Received';
  }
}
