import React from 'react';

import type { ConversationSummary, MessageItem } from '../../domain/types';
import type { UiCopy } from '../../app/ui';

export function MessagePanel({
  copy,
  conversation,
  messages,
  isLoadingOlder,
  hasMore,
  typingDisplay,
  onLoadOlder,
  onClear,
  onTyping,
  onRecall,
  onSend,
}: {
  copy: UiCopy;
  conversation: ConversationSummary | null;
  messages: MessageItem[];
  isLoadingOlder: boolean;
  hasMore: boolean;
  typingDisplay: string | null;
  onLoadOlder(): Promise<void>;
  onClear(): void;
  onTyping(text: string): void;
  onRecall(message: MessageItem): Promise<void>;
  onSend(text: string): Promise<void>;
}) {
  const [text, setText] = React.useState('');
  const messageStreamRef = React.useRef<HTMLDivElement | null>(null);
  const lastMessageKeyRef = React.useRef<string | null>(null);

  React.useEffect(() => {
    const container = messageStreamRef.current;
    if (container == null) {
      return;
    }
    const latest = messages[messages.length - 1];
    const nextKey =
      latest == null
        ? null
        : `${latest.localId}:${latest.serverId ?? ''}:${latest.timestamp}:${latest.status}:${latest.text}`;
    if (nextKey != null && nextKey !== lastMessageKeyRef.current) {
      container.scrollTop = container.scrollHeight;
    }
    lastMessageKeyRef.current = nextKey;
  }, [messages]);

  if (conversation == null) {
    return (
      <section className="right-panel message-panel empty">
        <p className="section-label">{copy.chat.noSelection}</p>
        <h3>{copy.chat.selectConversation}</h3>
      </section>
    );
  }

  return (
    <section className="right-panel message-panel">
      <header className="chat-header">
        <div>
          <p className="section-label">{copy.chat.activeConversation}</p>
          <div className="chat-name">{conversation.title}</div>
          <div className="chat-status">{typingDisplay ?? conversation.subtitle}</div>
        </div>
        <div className="chat-actions">
          <button className="action-btn" type="button" onClick={onClear}>
            {copy.chat.clear}
          </button>
          <button className="action-btn" type="button">
            {conversation.kind === 'DIRECT' ? copy.chat.direct : copy.chat.group}
          </button>
          <button className="action-btn" type="button">
            {new Date(conversation.lastMessageTime).toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </button>
        </div>
      </header>

      <div className="messages message-stream" ref={messageStreamRef}>
        {hasMore ? (
          <button className="ghost-action" type="button" onClick={() => void onLoadOlder()} disabled={isLoadingOlder}>
            {isLoadingOlder ? copy.chat.loadingOlder : copy.chat.loadOlder}
          </button>
        ) : (
          <div className="history-cap">{copy.chat.historyLoaded}</div>
        )}

        <ol className="message-list">
          {messages.map((message) => (
            <li
              className={message.direction === 'outgoing' ? 'message-row outgoing' : 'message-row incoming'}
              key={message.localId}
            >
              <article
                className={message.direction === 'outgoing' ? 'message-bubble self' : 'message-bubble other'}
              >
                <header className="message-meta">
                  <strong>{message.senderDisplay}</strong>
                  <span>
                    {new Date(message.timestamp).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </header>
                <p className="message-text">{message.recalled ? copy.chat.messageRecalled : message.text}</p>
                <footer className="message-meta">
                  <span>{labelForStatus(message.status, copy)}</span>
                  {message.direction === 'outgoing' && message.serverId != null && !message.recalled ? (
                    <button className="message-action" type="button" onClick={() => void onRecall(message)}>
                      {copy.chat.recall}
                    </button>
                  ) : null}
                  {message.failureReason == null ? null : <em>{message.failureReason}</em>}
                </footer>
              </article>
            </li>
          ))}
        </ol>
      </div>

      <form
        className="input-area composer"
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
        <div className="input-toolbar" aria-hidden="true">
          <button className="tool-btn" type="button">
            +
          </button>
          <button className="tool-btn" type="button">
            @
          </button>
          <button className="tool-btn" type="button">
            #
          </button>
        </div>
        <div className="input-row">
          <label className="input-wrap composer-field">
            <textarea
              aria-label="Message input"
              value={text}
              onChange={(event) => {
                setText(event.target.value);
                onTyping(event.target.value);
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  const next = text.trim();
                  if (next === '') {
                    return;
                  }
                  setText('');
                  void onSend(next);
                }
              }}
              placeholder={copy.chat.composerPlaceholder}
              rows={3}
            />
          </label>
          <button className="send-btn" type="submit">
            {copy.chat.send}
          </button>
        </div>
      </form>
    </section>
  );
}

function labelForStatus(status: MessageItem['status'], copy: UiCopy): string {
  switch (status) {
    case 'sending':
      return copy.chat.statusSending;
    case 'delivered':
      return copy.chat.statusDelivered;
    case 'read':
      return copy.chat.statusRead;
    case 'failed':
      return copy.chat.statusFailed;
    case 'received':
      return copy.chat.statusReceived;
  }
}
