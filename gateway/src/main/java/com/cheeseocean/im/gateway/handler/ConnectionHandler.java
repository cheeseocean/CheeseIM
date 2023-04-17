package com.cheeseocean.im.gateway.handler;

import com.cheeseocean.im.common.entity.GetMaxAndMinSeqReq;
import com.cheeseocean.im.common.entity.GetMaxAndMinSeqResp;
import com.cheeseocean.im.common.entity.Req;
import com.cheeseocean.im.common.constant.IMConstant;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ConnectionHandler extends ChannelInboundHandlerAdapter {
    
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Req) {
            switch (((Req) msg).getReqIdentifier()) {
                case IMConstant.WSGetNewestSeq:
                case IMConstant.WSSendMsg:
                case IMConstant.WSSendSignalMsg:
                case IMConstant.WSPullMsgBySeqList:
                case IMConstant.WsLogoutMsg:
                default:
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    private void getNewestSeq(Req req) {
        GetMaxAndMinSeqResp seqResp = new GetMaxAndMinSeqResp();
        GetMaxAndMinSeqReq seqReq = new GetMaxAndMinSeqReq();
        seqReq.setUserID(req.getSendID());
        seqReq.setOperationID(req.getOperationID());
        
    }
}
