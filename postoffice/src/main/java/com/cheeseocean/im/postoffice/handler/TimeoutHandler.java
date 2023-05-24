package com.cheeseocean.im.postoffice.handler;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Timeout when user idle
 * @author xxxcrel
 * Created on 2023/03/07
 */
public class TimeoutHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(TimeoutHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState e = ((IdleStateEvent) evt).state();
            if (e == IdleState.READER_IDLE) {
                log.debug("reader idle event");
                // fire a close that then fire channelInactive to trigger publish of Will
                ctx.close().addListener(CLOSE_ON_FAILURE);
            }
        } else {
            if (log.isTraceEnabled()) {
            }
            super.userEventTriggered(ctx, evt);
        }
    }
}
