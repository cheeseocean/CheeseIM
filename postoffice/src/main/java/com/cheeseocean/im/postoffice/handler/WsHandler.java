package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.util.IMUtils;
import com.cheeseocean.im.postoffice.connection.ConnectionHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.Map;

public class WsHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    public final static AttributeKey<Map<String, List<String>>> requestParams = AttributeKey.newInstance("requestParams");

    private ConnectionHolder connectionHolder;

    public WsHandler(ConnectionHolder connectionHolder) {
        this.connectionHolder = connectionHolder;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String uri = handshake.requestUri();
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
            Map<String, List<String>> params = queryStringDecoder.parameters();
            String operationID = "";
            if (params.containsKey("operationID")) {
                operationID = params.get("operationID").toString();
            } else {
                operationID = IMUtils.generateOperationID();
            }
            connectionHolder.addUserConn(ctx.channel(), params.get("sendID").toString(), Integer.parseInt(params.get("platformID").toString()), params.get("token").toString(), operationID);
            ctx.channel().attr(requestParams).set(params);
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        //Map<String, List<String>> requestParamsMap = ctx.channel().attr(requestParams).get();
        msg.content();
    }
}
