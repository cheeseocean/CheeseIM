package com.cheeseocean.im.client.tcp;

import com.cheeseocean.im.client.protocol.TcpMessageTypes;
import com.cheeseocean.im.client.protocol.TcpPacket;
import com.cheeseocean.im.client.protocol.TcpPacketCodec;
import com.cheeseocean.im.client.session.ClientSession;
import com.cheeseocean.im.client.session.ConnectionState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpImClientTest {

    @Test
    void connectShouldReadInitialConnectPushAndTransitionConnected() throws Exception {
        byte[] inbound = TcpPacketCodec.encode(new TcpPacket(
                TcpMessageTypes.TCP_CONNECT_SUCCESS,
                "system",
                1710000000000L,
                "连接成功"
        ));
        FakeTransport transport = new FakeTransport(inbound);
        CountDownLatch latch = new CountDownLatch(1);
        ClientSession session = new ClientSession();
        TcpImClient client = new TcpImClient(
                new TcpClientConfig("127.0.0.1", 5148),
                session,
                transportFactory(transport),
                listener(latch, null, null)
        );

        client.connect();

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(ConnectionState.CONNECTED, session.getConnectionState());
    }

    @Test
    void authenticateShouldSendTcpAuthReqWithTicket() throws Exception {
        FakeTransport transport = new FakeTransport();
        ClientSession session = new ClientSession();
        session.setUserId("userA");
        session.setPlatformId(2);
        session.setWsTicket("ws-ticket-1");
        TcpImClient client = new TcpImClient(
                new TcpClientConfig("127.0.0.1", 5148),
                session,
                transportFactory(transport),
                IncomingMessageListener.noop()
        );
        client.attachTransportForTest(transport);

        client.authenticate();

        TcpPacket outbound = transport.firstOutboundPacket();
        assertEquals(TcpMessageTypes.TCP_AUTH_REQ, outbound.msgType());
        assertTrue(outbound.data().contains("\"ticket\":\"ws-ticket-1\""));
    }

    @Test
    void sendTextShouldEmitSingleChatPayloadAndTrackAck() throws Exception {
        FakeTransport transport = new FakeTransport();
        ClientSession session = new ClientSession();
        session.setConnectionState(ConnectionState.AUTHENTICATED);
        TcpImClient client = new TcpImClient(
                new TcpClientConfig("127.0.0.1", 5148),
                session,
                transportFactory(transport),
                IncomingMessageListener.noop()
        );
        client.attachTransportForTest(transport);

        String operationId = client.sendText("userB", "hello");

        TcpPacket outbound = transport.firstOutboundPacket();
        assertEquals(TcpMessageTypes.TCP_SEND_MSG_REQ, outbound.msgType());
        assertTrue(outbound.data().contains("\"recvID\":\"userB\""));
        assertTrue(outbound.data().contains("\"content\":\"hello\""));
        assertTrue(outbound.data().contains("\"contentType\":101"));
        assertTrue(outbound.data().contains("\"sessionType\":1"));
        assertTrue(client.requestTracker().find(operationId).isPresent());
    }

    @Test
    void inboundNotifyShouldBeDeliveredToListener() throws Exception {
        byte[] inbound = TcpPacketCodec.encode(new TcpPacket(
                TcpMessageTypes.TCP_RECV_MSG_NOTIFY,
                "op-notify-00001",
                1710000000002L,
                "{\"serverMsgID\":\"msg-1\",\"clientMsgID\":\"client-1\",\"sendID\":\"userA\",\"recvID\":\"userB\",\"content\":\"hello\",\"contentType\":101,\"sessionType\":1,\"sendTime\":1710000000002}"
        ));
        FakeTransport transport = new FakeTransport(inbound);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TcpPacket> packetRef = new AtomicReference<>();
        TcpImClient client = new TcpImClient(
                new TcpClientConfig("127.0.0.1", 5148),
                new ClientSession(),
                transportFactory(transport),
                listener(null, latch, packetRef)
        );

        client.connect();

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(packetRef.get());
        assertEquals(TcpMessageTypes.TCP_RECV_MSG_NOTIFY, packetRef.get().msgType());
    }

    private static TcpImClient.TransportFactory transportFactory(FakeTransport transport) {
        return config -> transport;
    }

    private static IncomingMessageListener listener(CountDownLatch connectedLatch,
                                                    CountDownLatch messageLatch,
                                                    AtomicReference<TcpPacket> packetRef) {
        return new IncomingMessageListener() {
            @Override
            public void onConnected() {
                if (connectedLatch != null) {
                    connectedLatch.countDown();
                }
            }

            @Override
            public void onMessage(TcpPacket packet) {
                if (packetRef != null) {
                    packetRef.set(packet);
                }
                if (messageLatch != null) {
                    messageLatch.countDown();
                }
            }
        };
    }

    private static final class FakeTransport implements TcpImClient.Transport {

        private final InputStream inputStream;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        private FakeTransport() {
            this(new byte[0]);
        }

        private FakeTransport(byte[] inboundBytes) {
            this.inputStream = new ByteArrayInputStream(inboundBytes);
        }

        @Override
        public InputStream inputStream() {
            return inputStream;
        }

        @Override
        public OutputStream outputStream() {
            return outputStream;
        }

        @Override
        public void close() {
        }

        TcpPacket firstOutboundPacket() throws IOException {
            return TcpPacketCodec.read(new ByteArrayInputStream(outputStream.toByteArray()));
        }
    }
}
