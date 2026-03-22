package com.cheeseocean.im.client.tcp;

import com.cheeseocean.im.client.protocol.TcpMessageTypes;
import com.cheeseocean.im.client.protocol.TcpPacket;
import com.cheeseocean.im.client.protocol.TcpPacketCodec;
import com.cheeseocean.im.client.session.ClientSession;
import com.cheeseocean.im.client.session.ConnectionState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

public class TcpImClient {

    private final TcpClientConfig config;
    private final ClientSession session;
    private final TransportFactory transportFactory;
    private final IncomingMessageListener listener;
    private final PayloadFactory payloadFactory;
    private final RequestTracker requestTracker;

    private volatile Transport transport;
    private volatile Thread readerThread;

    public TcpImClient(TcpClientConfig config,
                       ClientSession session,
                       TransportFactory transportFactory,
                       IncomingMessageListener listener) {
        this(config, session, transportFactory, listener, new PayloadFactory(), new RequestTracker());
    }

    TcpImClient(TcpClientConfig config,
                ClientSession session,
                TransportFactory transportFactory,
                IncomingMessageListener listener,
                PayloadFactory payloadFactory,
                RequestTracker requestTracker) {
        this.config = config;
        this.session = session;
        this.transportFactory = transportFactory;
        this.listener = listener;
        this.payloadFactory = payloadFactory;
        this.requestTracker = requestTracker;
    }

    public void connect() throws IOException {
        this.transport = transportFactory.open(config);
        startReaderLoop();
    }

    public void authenticate() throws IOException {
        String operationId = nextOperationId();
        requestTracker.remember(operationId, "auth");
        writePacket(new TcpPacket(
                TcpMessageTypes.TCP_AUTH_REQ,
                operationId,
                System.currentTimeMillis(),
                payloadFactory.authPayload(session.getWsTicket())
        ));
    }

    public String sendText(String peerUserId, String text) throws IOException {
        String operationId = nextOperationId();
        String clientMsgId = "cli-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        session.setLatestClientMsgId(clientMsgId);
        requestTracker.remember(operationId, "send");
        writePacket(new TcpPacket(
                TcpMessageTypes.TCP_SEND_MSG_REQ,
                operationId,
                System.currentTimeMillis(),
                payloadFactory.singleChatTextPayload(clientMsgId, peerUserId, text)
        ));
        return operationId;
    }

    public void heartbeat() throws IOException {
        writePacket(new TcpPacket(
                TcpMessageTypes.TCP_HEARTBEAT_REQ,
                nextOperationId(),
                System.currentTimeMillis(),
                "ping"
        ));
    }

    public RequestTracker requestTracker() {
        return requestTracker;
    }

    void attachTransportForTest(Transport transport) {
        this.transport = transport;
    }

    private void startReaderLoop() {
        readerThread = new Thread(() -> {
            try {
                InputStream inputStream = transport.inputStream();
                while (!Thread.currentThread().isInterrupted()) {
                    TcpPacket packet = TcpPacketCodec.read(inputStream);
                    handleIncoming(packet);
                }
            } catch (IOException e) {
                listener.onDisconnected();
            }
        }, "im-java-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void handleIncoming(TcpPacket packet) {
        switch (packet.msgType()) {
            case TcpMessageTypes.TCP_CONNECT_SUCCESS -> {
                session.setConnectionState(ConnectionState.CONNECTED);
                listener.onConnected();
            }
            case TcpMessageTypes.TCP_AUTH_SUCCESS -> {
                session.setConnectionState(ConnectionState.AUTHENTICATED);
                requestTracker.resolve(packet.operationId());
                listener.onAuthSuccess();
            }
            case TcpMessageTypes.TCP_AUTH_FAILED -> {
                requestTracker.resolve(packet.operationId());
                listener.onAuthFailed(packet);
            }
            case TcpMessageTypes.TCP_SEND_MSG_RESP -> {
                requestTracker.resolve(packet.operationId());
                listener.onSendAck(packet);
            }
            case TcpMessageTypes.TCP_RECV_MSG_NOTIFY -> listener.onMessage(packet);
            default -> {
            }
        }
    }

    private void writePacket(TcpPacket packet) throws IOException {
        OutputStream outputStream = transport.outputStream();
        outputStream.write(TcpPacketCodec.encode(packet));
        outputStream.flush();
    }

    private String nextOperationId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public interface TransportFactory {
        Transport open(TcpClientConfig config) throws IOException;
    }

    public interface Transport {
        InputStream inputStream() throws IOException;

        OutputStream outputStream() throws IOException;

        void close() throws IOException;
    }

    public static final class SocketTransportFactory implements TransportFactory {

        @Override
        public Transport open(TcpClientConfig config) throws IOException {
            Socket socket = new Socket(config.host(), config.port());
            return new SocketTransport(socket);
        }
    }

    private static final class SocketTransport implements Transport {

        private final Socket socket;

        private SocketTransport(Socket socket) {
            this.socket = socket;
        }

        @Override
        public InputStream inputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream outputStream() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
