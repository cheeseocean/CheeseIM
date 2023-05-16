package com.cheeseocean.im.gateway.handler;

import com.cheeseocean.im.common.entity.CheeseRequest;
import com.cheeseocean.im.common.constant.IMConstant;
import com.cheeseocean.im.message.api.MessageService;
import com.cheeseocean.im.message.api.SendMessageReq;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ConnectionHandler extends ChannelInboundHandlerAdapter {

    /**
     * 消息服务
     */
    private MessageService messageService;

    public ConnectionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof CheeseRequest) {
            switch (((CheeseRequest) msg).getType()) {
                case IMConstant.WSGetNewestSeq:
                case IMConstant.WSSendMsg:
                    sendMessage(((CheeseRequest) msg));
                    break;
                case IMConstant.WSSendSignalMsg:
                case IMConstant.WSPullMsgBySeqList:
                case IMConstant.WsLogoutMsg:
                default:
            }
        }
        super.channelRead(ctx, msg);
    }

    private void getNewestSeq(CheeseRequest CheeseRequest) {

    }

    private void sendMessage(CheeseRequest request) {
        SendMessageReq sendMessageReq = new SendMessageReq();
        sendMessageReq.setPayload(request.getPayload());
        messageService.sendMessage(sendMessageReq);
    }
}
