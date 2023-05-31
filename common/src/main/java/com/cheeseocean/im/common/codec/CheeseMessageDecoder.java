package com.cheeseocean.im.common.codec;

import com.cheeseocean.im.common.entity.CheeseRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CheeseMessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        log.info("decode request");
        Hessian2ObjectInput hessianObjIn = new Hessian2ObjectInput(new ByteBufInputStream(in));
        out.add(hessianObjIn.readObject(CheeseRequest.class));
    }
}
