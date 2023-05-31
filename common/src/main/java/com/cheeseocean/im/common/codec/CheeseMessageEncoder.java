package com.cheeseocean.im.common.codec;

import com.cheeseocean.im.common.entity.CheeseResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@ChannelHandler.Sharable
@Slf4j
public class CheeseMessageEncoder extends MessageToByteEncoder<CheeseResponse> {

    public static final CheeseMessageEncoder INSTANCE = new CheeseMessageEncoder();
    @Override
    protected void encode(ChannelHandlerContext ctx, CheeseResponse msg, ByteBuf out) throws Exception {
        log.info("decode request:{}", msg);
        Hessian2ObjectOutput output = new Hessian2ObjectOutput(new ByteBufOutputStream(out));
        output.writeObject(msg);
        output.flushBuffer();
    }
}
