package com.cheeseocean.im.common.codec;

import com.cheeseocean.im.common.entity.UserResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

@ChannelHandler.Sharable
public class CheeseMessageEncoder extends MessageToByteEncoder<UserResponse> {

    public static final CheeseMessageEncoder INSTANCE = new CheeseMessageEncoder();
    @Override
    protected void encode(ChannelHandlerContext ctx, UserResponse msg, ByteBuf out) throws Exception {
        Hessian2ObjectOutput output = new Hessian2ObjectOutput(new ByteBufOutputStream(out));
        output.writeObject(msg);
        output.flushBuffer();
    }
}
