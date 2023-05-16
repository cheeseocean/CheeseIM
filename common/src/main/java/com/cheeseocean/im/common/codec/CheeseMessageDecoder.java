package com.cheeseocean.im.common.codec;

import com.cheeseocean.im.common.entity.CheeseRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class CheeseMessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Hessian2ObjectInput hessianObjIn = new Hessian2ObjectInput(new ByteBufInputStream(in));
        out.add(hessianObjIn.readObject(CheeseRequest.class));
    }
}
