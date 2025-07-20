package com.cheeseocean.im.postoffice.codec;

import com.cheeseocean.im.postoffice.protocol.TcpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP消息编码器
 * 负责将TcpMessage对象编码为字节流
 * 
 * @author CheeseIM
 */
public class TcpMessageEncoder extends MessageToByteEncoder<TcpMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpMessageEncoder.class);
    
    @Override
    protected void encode(ChannelHandlerContext ctx, TcpMessage msg, ByteBuf out) throws Exception {
        try {
            // 编码消息为字节数组
            byte[] messageBytes = msg.encode();
            
            // 写入ByteBuf
            out.writeBytes(messageBytes);
            
            logger.debug("Encoded TCP message: msgType={}, operationID={}, dataLength={}", 
                        msg.getMsgType(), msg.getOperationID(), msg.getDataLength());
            
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
