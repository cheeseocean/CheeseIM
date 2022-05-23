package com.cheeseocean.cheeseim.codec;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;

public class MqttDecoder extends ReplayingDecoder<MqttDecoder.DecoderState> {
    private static final int DEFAULT_MAX_BYTES_IN_MESSAGE = 8092;

    enum DecoderState{
        FIXED_HEADER,
        VARIABLE_HEADER,
        PAYLOAD,
        BAD_MESSAGE
    }
    private int maxBytesInMessage;
    private MqttFixedHeader fixedHeader;
    private Object variableHeader;
    public MqttDecoder(){
        this(DEFAULT_MAX_BYTES_IN_MESSAGE);
    }

    public MqttDecoder(int maxBytesInMessage){
        checkpoint(DecoderState.FIXED_HEADER);
        this.maxBytesInMessage = maxBytesInMessage;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()){
            case FIXED_HEADER: try {
                fixedHeader = decodeFixedHeader(ctx, in);
            }catch(Exception e){
                out.add(MqttMessageFactory.newInvalidMessage(e));
            }
            case VARIABLE_HEADER:
            case PAYLOAD:
            case BAD_MESSAGE:
        }
    }

    /**
     *  [7654_3210]7-4代表messageType，3-dupFlag, 21-qosLevel, 0-retain
     * @param ctx
     * @param buffer
     * @return
     */
    private MqttFixedHeader decodeFixedHeader(ChannelHandlerContext ctx, ByteBuf buffer){
        //读取消息类型, dupFlag, qosLevel, retain标志
        short headerByte = buffer.readUnsignedByte();
        MqttMessageType messageType = MqttMessageType.valueOf(headerByte >> 4);
        boolean dupFlag = (headerByte & 0b0000_1000) == 0b0000_1000;
        int qosLevel = (headerByte & 0b0000_0110) >> 1;
        boolean retain = (headerByte & 0b0000_0001) != 0;

        //读取剩余字节数(最大支持4个字节)256MB
        //case 1: [< 128]
        //      01010001(81)
        //case 2: [128 (0x80, 0x01); - 16383(0xFF, 0x7F]
        //      01010001
        //       00110001
        //case 3 ... case 4
        int remainLength = 0;
        int multiplier = 1;
        int loop = 0;
        short nextByte;
        do{
            nextByte = buffer.readUnsignedByte();
            remainLength += (nextByte & 127) * multiplier;
            multiplier *= 128;
            loop++;
        }while((nextByte & 127) != 0 && loop < 4);
        return null;
    }
}
