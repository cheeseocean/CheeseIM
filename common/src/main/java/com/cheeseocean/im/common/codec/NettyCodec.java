package com.cheeseocean.im.common.codec;

import com.cheeseocean.im.common.entity.Req;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.dubbo.common.serialize.hessian2.Hessian2ObjectInput;
import org.apache.dubbo.common.serialize.hessian2.Hessian2ObjectOutput;

import java.util.List;

public class NettyCodec {

    private final ChannelHandler encoder = new NettyCodec.InternalEncoder();

    private final ChannelHandler decoder = new NettyCodec.InternalDecoder();

    public NettyCodec() {
    }

    public ChannelHandler getEncoder() {
        return encoder;
    }

    public ChannelHandler getDecoder() {
        return decoder;
    }

    private class InternalEncoder extends MessageToByteEncoder {

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            Channel ch = ctx.channel();
            Hessian2ObjectOutput output = new Hessian2ObjectOutput(new ByteBufOutputStream(out));
            output.writeObject(msg);
            output.flushBuffer();
        }
    }

    private class InternalDecoder extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {
            // decode object.
            Hessian2ObjectInput in = new Hessian2ObjectInput(new ByteBufInputStream(input));
            out.add(in.readObject(Req.class));
        }
    }

}
