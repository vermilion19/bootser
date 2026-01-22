package com.booster.minizuulservice.server.handler;

import com.booster.minizuulservice.server.LoadBalancer;
import com.booster.minizuulservice.server.ProxyAttributes;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;

@Slf4j
public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private final LoadBalancer loadBalancer;
    private Channel outboundChannel;

    // 큐에 담아둘 때 메모리가 해제되지 않도록 주의해야 함
    private final Queue<Object> pendingMessages = new LinkedList<>();
    private int maxRetries = 3;
    private int currentRetry = 0;
    private boolean isConnecting = false;

    public ProxyFrontendHandler(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();
        if (isConnecting) return;
        isConnecting = true;

        attemptConnection(inboundChannel);
    }

    // [핵심] 연결 시도 및 재시도 로직 분리
    private void attemptConnection(Channel inboundChannel) {
        InetSocketAddress target = loadBalancer.getNextServer();
        log.info("Attempting connection to backend: {}:{} (Retry: {}/{})",
                target.getHostString(), target.getPort(), currentRetry, maxRetries);

        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(inboundChannel.getClass())
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(65536));
                        p.addLast(new ProxyBackendHandler(inboundChannel));
                    }
                });

        ChannelFuture f = b.connect(target.getHostString(), target.getPort());
        outboundChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 성공하면 재시도 카운트 초기화 및 메시지 전송
                log.info("Target Connected: {}:{}", target.getHostString(), target.getPort());
                isConnecting = false;
                flushPendingMessages();
            } else {
                // [핵심] 실패 시 Failover 로직
                log.warn("Failed to connect to {}:{}", target.getHostString(), target.getPort());

                if (currentRetry < maxRetries) {
                    currentRetry++;
                    log.info("Failover triggered! Trying next server...");
                    // 재귀 호출로 다음 서버 시도 (큐에 있는 메시지는 그대로 유지됨)
                    attemptConnection(inboundChannel);
                } else {
                    log.error("All backends are down or max retries reached.");
                    clearPendingMessages();
                    inboundChannel.close(); // 진짜 포기
                }
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;

            // [추가] 1. 요청 시작 시간 기록 (나노초 단위가 정밀함)
            ctx.channel().attr(ProxyAttributes.START_TIME).set(System.nanoTime());

            // [추가] 2. 요청 URI 기록
            ctx.channel().attr(ProxyAttributes.REQUEST_URI).set(request.uri());

            request.headers().set("X-Forwarded-For", "Mini-Zuul-Failover-Proxy");
            request.retain();
        }

        if (outboundChannel != null && outboundChannel.isActive()) {
            writeToBackend(msg);
        } else {
            pendingMessages.add(msg);
        }
    }



    private void flushPendingMessages() {
        log.info("Flushing {} buffered messages to backend", pendingMessages.size());
        while (!pendingMessages.isEmpty()) {
            Object msg = pendingMessages.poll();
            writeToBackend(msg);
        }
    }

    private void writeToBackend(Object msg) {
        outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.error("Write to backend failed", future.cause());
                future.channel().close();
            }
        });
    }

    private void clearPendingMessages() {
        while (!pendingMessages.isEmpty()) {
            Object msg = pendingMessages.poll();
            io.netty.util.ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Client Disconnected");
        clearPendingMessages();
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Frontend Error", cause);
        clearPendingMessages();
        closeOnFlush(ctx.channel());
    }

    private void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
