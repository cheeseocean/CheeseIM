package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.entity.UserRequest;
import com.cheeseocean.im.common.constant.IMConstant;
import com.cheeseocean.im.message.api.MessageService;
import com.cheeseocean.im.message.api.SendMessageReq;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        if (msg instanceof UserRequest) {
            switch (((UserRequest) msg).getType()) {
                case IMConstant.WSGetNewestSeq:
                case IMConstant.WSSendMsg:
                    log.info("receive user send message request:{}", msg);
                    sendMessage(((UserRequest) msg));
                    break;
                case IMConstant.WSSendSignalMsg:
                case IMConstant.WSPullMsgBySeqList:
                case IMConstant.WsLogoutMsg:
                default:
            }
        }
        super.channelRead(ctx, msg);
    }

    private void getNewestSeq(UserRequest UserRequest) {

    }

    private void sendMessage(UserRequest request) {
        SendMessageReq sendMessageReq = new SendMessageReq();
        sendMessageReq.setPayload(request.getPayload());
        messageService.sendMessage(sendMessageReq);
    }
}
