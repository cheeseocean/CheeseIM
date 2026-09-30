package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 慢读客户端的失败通知、连接关闭与帧释放回归测试。
 *
 * @author wxc
 */
class OutboundBackpressureHandlerTest {
    @Test
    void writableChannelPreservesResponse() {
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundBackpressureHandler());
        try {
            ServerEnvelope response = ServerEnvelope.error("request", 500, "失败");
            assertTrue(channel.writeOutbound(response));
            assertSame(response, channel.readOutbound());
            assertTrue(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void unwritableChannelFailsTcpResponseAndCloses() {
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundBackpressureHandler());
        try {
            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
            ChannelFuture future = channel.writeAndFlush(ServerEnvelope.connect("request", "连接成功"));
            assertTrue(future.isDone());
            assertFalse(future.isSuccess());
            assertFalse(channel.isActive());
            assertNull(channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void unwritableChannelReleasesWebsocketFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new OutboundBackpressureHandler());
        try {
            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
            BinaryWebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.buffer().writeByte(1));
            assertFalse(channel.writeAndFlush(frame).isSuccess());
            assertEquals(0, frame.refCnt());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
