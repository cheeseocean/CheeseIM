package com.cheeseocean.im.postoffice.codec;

import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;

/**
 * TCP消息编码器
 * 负责将统一服务端消息包编码为TCP字节流
 * 
 * @author xxxcrel
 */
public class TcpEnvelopeEncoder extends MessageToByteEncoder<ServerEnvelope> {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;
    
    @Override
    protected void encode(ChannelHandlerContext ctx, ServerEnvelope msg, ByteBuf out) throws Exception {
        try {
            byte[] messageBytes = TcpEnvelopeCodecSupport.encode(msg);
            
            // 写入ByteBuf
            out.writeBytes(messageBytes);
            
            logger.debug("Encoded TCP message: command={}, requestId={}",
                    msg.getCommand(), msg.getRequestId());
            
        } catch (Exception e) {
            logger.error("Failed to encode TCP message to {}", ctx.channel().remoteAddress(), e);
            throw e;
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in TcpMessageEncoder to {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
