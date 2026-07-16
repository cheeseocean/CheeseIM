package com.cheeseocean.im.postoffice.codec;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;

import java.util.List;

/**
 * TCP消息解码器
 * 负责将字节流解码为客户端统一消息包
 * <p>
 * 协议格式：
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * | Magic  | Version| CommandType| Length |  OperationID (16 bytes)       |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                    Timestamp (8 bytes)                               |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                    Data (Length bytes)                               |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 *
 * @author xxxcrel
 */
public class TcpEnvelopeDecoder extends ByteToMessageDecoder {

    private static final Logger logger = CommonLoggers.POSTOFFICE;
    private final int maxDataLength;

    public TcpEnvelopeDecoder(int maxDataLength) {
        this.maxDataLength = Math.max(1, maxDataLength);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            // 检查是否有足够的字节读取头部
            if (in.readableBytes() < TcpEnvelopeCodecSupport.HEADER_LENGTH) {
                return; // 等待更多数据
            }

            // 标记读取位置，以便在解析失败时回滚
            in.markReaderIndex();

            // 读取Magic
            short magic = in.readShort();
            if (magic != TcpEnvelopeCodecSupport.MAGIC) {
                logger.warn("Invalid magic number: 0x{}, expected: 0x{}",
                        Integer.toHexString(magic & 0xFFFF),
                        Integer.toHexString(TcpEnvelopeCodecSupport.MAGIC & 0xFFFF));

                // 重置读取位置并跳过一个字节，继续寻找有效的Magic
                in.resetReaderIndex();
                in.readByte();
                return;
            }

            // 读取Version
            byte version = in.readByte();
            if (version != TcpEnvelopeCodecSupport.VERSION) {
                logger.warn("Unsupported protocol version: {}, expected: {}",
                        version, TcpEnvelopeCodecSupport.VERSION);
                in.resetReaderIndex();
                in.readByte();
                return;
            }

            // 读取消息类型
            byte msgType = in.readByte();

            // 读取数据长度
            int dataLength = in.readInt();

            // 验证数据长度
            if (dataLength < 0 || dataLength > maxDataLength) {
                logger.warn("Invalid data length: {}, max allowed: {}",
                        dataLength, maxDataLength);
                in.resetReaderIndex();
                in.readByte();
                return;
            }

            // 检查是否有足够的字节读取完整消息
            int totalLength = TcpEnvelopeCodecSupport.HEADER_LENGTH + dataLength;
            if (in.readableBytes() < totalLength - 8) { // 已经读取了8字节头部
                in.resetReaderIndex();
                return; // 等待更多数据
            }

            // 读取OperationID (16 bytes)
            byte[] operationIDBytes = new byte[16];
            in.readBytes(operationIDBytes);
            String operationID = new String(operationIDBytes, "UTF-8").trim();

            // 读取时间戳
            long timestamp = in.readLong();

            // 读取数据
            byte[] dataBytes = new byte[dataLength];
            if (dataLength > 0) {
                in.readBytes(dataBytes);
            }

            // 添加到输出列表
            ClientEnvelope envelope = TcpEnvelopeCodecSupport.decode(msgType, operationID, dataBytes);
            out.add(envelope);

            logger.debug("Decoded TCP message: msgType={}, operationID={}, command={}",
                    msgType, operationID, envelope.getCommand());

        } catch (Exception e) {
            logger.error("Failed to decode TCP message from {}", ctx.channel().remoteAddress(), e);

            // 重置读取位置并跳过一个字节
            in.resetReaderIndex();
            if (in.readableBytes() > 0) {
                in.readByte();
            }

            // 关闭连接以防止进一步的错误
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in TcpMessageDecoder from {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
