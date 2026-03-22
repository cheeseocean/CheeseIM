package com.cheeseocean.im.client.cli;

import com.cheeseocean.im.client.auth.AuthLoginResponse;
import com.cheeseocean.im.client.session.ClientSession;
import com.cheeseocean.im.client.tcp.TcpImClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DemoState {

    private final ClientSession session = new ClientSession();
    private final Map<String, ConversationView> conversations = new LinkedHashMap<>();
    private AuthLoginResponse loginResponse;
    private TcpImClient tcpImClient;
    private String activePeerUserId;

    public ClientSession session() {
        return session;
    }

    public AuthLoginResponse loginResponse() {
        return loginResponse;
    }

    public void setLoginResponse(AuthLoginResponse loginResponse) {
        this.loginResponse = loginResponse;
    }

    public TcpImClient tcpImClient() {
        return tcpImClient;
    }

    public void setTcpImClient(TcpImClient tcpImClient) {
        this.tcpImClient = tcpImClient;
    }

    public void setActivePeerUserId(String activePeerUserId) {
        if (activePeerUserId != null && !activePeerUserId.isBlank()) {
            this.activePeerUserId = activePeerUserId;
        }
    }

    public String activePeerUserId() {
        return activePeerUserId;
    }

    public ConversationView conversation(String peerUserId) {
        return conversations.computeIfAbsent(peerUserId, ignored -> new ConversationView(conversationIdFor(peerUserId)));
    }

    public void recordOutgoingText(String peerUserId, String clientMsgId, String content) {
        setActivePeerUserId(peerUserId);
        conversation(peerUserId).messages.add(ChatMessage.outgoing(clientMsgId, content));
    }

    public void confirmOutgoing(String clientMsgId, String serverMsgId) {
        findByClientMsgId(clientMsgId).ifPresent(message -> {
            message.serverMsgId = serverMsgId;
            message.delivery = DeliveryState.SENT;
        });
    }

    public void bindSequence(String serverMsgId, Long seq) {
        if (serverMsgId == null || seq == null) {
            return;
        }
        conversations.values().forEach(conversation -> conversation.messages.stream()
                .filter(message -> serverMsgId.equals(message.serverMsgId))
                .findFirst()
                .ifPresent(message -> {
                    message.seq = seq;
                    conversation.latestSeq = Math.max(conversation.latestSeq, seq);
                }));
    }

    public void recordIncomingText(String peerUserId, String serverMsgId, Long seq, String content) {
        setActivePeerUserId(peerUserId);
        ConversationView conversation = conversation(peerUserId);
        conversation.peerTyping = false;
        if (seq != null) {
            conversation.latestSeq = Math.max(conversation.latestSeq, seq);
        }
        conversation.messages.add(ChatMessage.incoming(serverMsgId, seq, content));
    }

    public void markPeerTyping(String peerUserId, boolean typing) {
        setActivePeerUserId(peerUserId);
        conversation(peerUserId).peerTyping = typing;
    }

    public void markRead(String conversationId, long seq) {
        String peerUserId = peerFromConversationId(conversationId);
        if (peerUserId == null) {
            return;
        }
        setActivePeerUserId(peerUserId);
        ConversationView conversation = conversation(peerUserId);
        conversation.latestSeq = Math.max(conversation.latestSeq, seq);
        boolean marked = false;
        for (ChatMessage message : conversation.messages) {
            if (message.outgoing() && message.delivery != DeliveryState.RECALLED) {
                if (message.seq != null && message.seq <= seq) {
                    message.delivery = DeliveryState.READ;
                    marked = true;
                }
            }
        }
        if (!marked) {
            for (int i = conversation.messages.size() - 1; i >= 0; i--) {
                ChatMessage message = conversation.messages.get(i);
                if (message.outgoing() && message.delivery == DeliveryState.SENT) {
                    message.delivery = DeliveryState.READ;
                    break;
                }
            }
        }
    }

    public void markRevoked(String conversationId, String serverMsgId) {
        if (serverMsgId == null || serverMsgId.isBlank()) {
            return;
        }
        String peerUserId = peerFromConversationId(conversationId);
        if (peerUserId != null) {
            setActivePeerUserId(peerUserId);
        }
        conversations.values().forEach(conversation -> conversation.messages.stream()
                .filter(message -> serverMsgId.equals(message.serverMsgId))
                .findFirst()
                .ifPresent(message -> message.delivery = DeliveryState.RECALLED));
    }

    public Long latestSeq(String peerUserId) {
        return conversation(peerUserId).latestSeq == 0 ? null : conversation(peerUserId).latestSeq;
    }

    public String latestServerMsgId(String peerUserId) {
        List<ChatMessage> messages = conversation(peerUserId).messages;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.outgoing() && message.serverMsgId != null && message.delivery != DeliveryState.RECALLED) {
                return message.serverMsgId;
            }
        }
        return null;
    }

    public String conversationIdFor(String peerUserId) {
        String currentUserId = session.getUserId();
        if (currentUserId == null || peerUserId == null) {
            return null;
        }
        return currentUserId.compareTo(peerUserId) < 0
                ? "c1:" + currentUserId + ":" + peerUserId
                : "c1:" + peerUserId + ":" + currentUserId;
    }

    public String peerFromConversationId(String conversationId) {
        if (conversationId == null || !conversationId.startsWith("c1:") || session.getUserId() == null) {
            return null;
        }
        String[] parts = conversationId.split(":");
        if (parts.length != 3) {
            return null;
        }
        if (Objects.equals(parts[1], session.getUserId())) {
            return parts[2];
        }
        if (Objects.equals(parts[2], session.getUserId())) {
            return parts[1];
        }
        return null;
    }

    public List<ChatMessage> messages(String peerUserId) {
        return List.copyOf(conversation(peerUserId).messages);
    }

    public boolean isPeerTyping(String peerUserId) {
        return conversation(peerUserId).peerTyping;
    }

    private java.util.Optional<ChatMessage> findByClientMsgId(String clientMsgId) {
        return conversations.values().stream()
                .flatMap(conversation -> conversation.messages.stream())
                .filter(message -> clientMsgId != null && clientMsgId.equals(message.clientMsgId))
                .findFirst();
    }

    static final class ConversationView {
        private final String conversationId;
        private final List<ChatMessage> messages = new ArrayList<>();
        private boolean peerTyping;
        private long latestSeq;

        private ConversationView(String conversationId) {
            this.conversationId = conversationId;
        }

        String conversationId() {
            return conversationId;
        }
    }

    static final class ChatMessage {
        private final boolean outgoing;
        private final String clientMsgId;
        private String serverMsgId;
        private Long seq;
        private final String content;
        private DeliveryState delivery;

        private ChatMessage(boolean outgoing,
                            String clientMsgId,
                            String serverMsgId,
                            Long seq,
                            String content,
                            DeliveryState delivery) {
            this.outgoing = outgoing;
            this.clientMsgId = clientMsgId;
            this.serverMsgId = serverMsgId;
            this.seq = seq;
            this.content = content;
            this.delivery = delivery;
        }

        static ChatMessage outgoing(String clientMsgId, String content) {
            return new ChatMessage(true, clientMsgId, null, null, content, DeliveryState.SENDING);
        }

        static ChatMessage incoming(String serverMsgId, Long seq, String content) {
            return new ChatMessage(false, null, serverMsgId, seq, content, DeliveryState.SENT);
        }

        boolean outgoing() {
            return outgoing;
        }

        Long seq() {
            return seq;
        }

        String displayContent() {
            return delivery == DeliveryState.RECALLED ? "[recalled]" : content;
        }

        String statusLabel() {
            return switch (delivery) {
                case SENDING -> "[sending]";
                case SENT -> "[sent]";
                case READ -> "[read]";
                case RECALLED -> "[recalled]";
            };
        }
    }

    enum DeliveryState {
        SENDING,
        SENT,
        READ,
        RECALLED
    }
}
