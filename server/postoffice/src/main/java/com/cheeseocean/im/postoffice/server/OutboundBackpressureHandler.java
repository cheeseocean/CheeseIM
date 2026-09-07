package com.cheeseocean.im.postoffice.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;

/**
 * 在 EventLoop 中统一保护业务响应与在线投递，慢读连接不能继续积压出站消息。
 *
 * @author wxc
 */
final class OutboundBackpressureHandler extends ChannelOutboundHandlerAdapter {

    /** 不可写时失败关闭，由客户端重连补齐，禁止再写错误响应形成递归积压。 */
    @Override
    public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
        if (!ctx.channel().isActive() || !ctx.channel().isWritable()) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(new IOException("Channel unavailable for outbound write"));
            ctx.close();
            return;
        }
        ctx.write(message, promise);
    }
}
