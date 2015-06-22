package org.github.faaipdo.loadgen;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This ChannelHandler sends the given payload as many times as possible.
 */
public class LoadGenHandlerManager {

    private static final Logger LOG = LoggerFactory.getLogger(LoadGenHandlerManager.class);

    private static final MetricRegistry METRICS = TCPLoadGen.METRICS;
    private static final Meter METER = METRICS.meter("requests");

    private String host;
    private int port;
    private byte[] payload;
    private Class<? extends Channel> channelClass;

    private static final class Handler extends ChannelInboundHandlerAdapter {

        private LoadGenHandlerManager manager;
        private final ByteBuf payload;

        public Handler(LoadGenHandlerManager manager, byte[] payload) {
            this.manager = manager;
            this.payload = Unpooled.copiedBuffer(payload);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            METRICS.counter("2-established").inc();
            writeIfPossible(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            METRICS.counter("3-closed").inc();
            //open new channel
            if (ctx.executor() instanceof EventLoopGroup) {
                EventLoopGroup group = (EventLoopGroup) ctx.executor();
                //open a new connection if the group isn't already shutting down
                if (!group.isShuttingDown()) {
                    manager.connectAndRun((EventLoopGroup) ctx.executor());
                }
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            writeIfPossible(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            //no need to do anything with the received object
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            writeIfPossible(ctx);

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            //Ignore "Connection reset by peer" errors
            if (!(cause instanceof IOException)
                    || cause.getMessage() == null
                    || !cause.getMessage().contains("Connection reset by peer")) {
                TCPLoadGen.METRICS.counter("exceptions").inc();
                LOG.error("Error caught while writing payload", cause);
            }
            //close channel anyway
            if (ctx.channel().isOpen()) {
                ctx.close();
            }
        }

        private void writeIfPossible(ChannelHandlerContext ctx) {
            if (ctx.channel().isWritable()) {
                ctx.writeAndFlush(payload.copy());
                METER.mark();
            }
        }
    }

    public static LoadGenHandlerManager initConfig(byte[] payload, String host, int port, Class<? extends Channel> channelClass) {
        LoadGenHandlerManager config = new LoadGenHandlerManager();
        config.payload = payload;
        config.channelClass = channelClass;
        config.host = host;
        config.port = port;
        return config;
    }

    public ChannelFuture connectAndRun(EventLoopGroup group) {
        METRICS.counter("1-connect").inc();
        return new Bootstrap()
                .group(group)
                .channel(channelClass)
                .handler(new Handler(this, payload))
                .connect(host, port);
    }
}
