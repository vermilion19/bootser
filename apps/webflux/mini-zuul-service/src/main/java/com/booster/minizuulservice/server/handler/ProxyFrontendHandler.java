package com.booster.minizuulservice.server.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Queue;

@Slf4j
public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private final String remoteHost;
    private final int remotePort;
    private Channel outboundChannel;

    // 큐에 담아둘 때 메모리가 해제되지 않도록 주의해야 함
    private final Queue<Object> pendingMessages = new LinkedList<>();
    private boolean isConnecting = false;

    public ProxyFrontendHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();

        if (isConnecting) return;
        isConnecting = true;

        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<Channel>() {
                             @Override
                             protected void initChannel(Channel ch) {
                                 ChannelPipeline p = ch.pipeline();
                                 // 이 코덱이 있어야 우리가 보낸 HttpRequest 객체를 바이트로 변환해서 백엔드에 줍니다.
                                 p.addLast(new HttpClientCodec());
                                 p.addLast(new HttpObjectAggregator(65536));
                                 p.addLast(new ProxyBackendHandler(inboundChannel));
                             }
                         });

        ChannelFuture f = b.connect(remoteHost, remotePort);
        outboundChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("Target Connected: {}:{}", remoteHost, remotePort);
                isConnecting = false;
                // 연결 성공 시 쌓인 메시지 발송
                flushPendingMessages();
            } else {
                log.error("Target Connection Failed");
                // 연결 실패 시 큐에 있는 데이터 메모리 해제 필수!
                clearPendingMessages();
                inboundChannel.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;

            // 헤더 추가: X-Forwarded-For (나를 거쳐갔음을 표시)
            request.headers().set("X-Forwarded-For", "Mini-Zuul-Proxy");
            request.headers().set("My-Custom-Header", "Netty-Is-Cool"); // 테스트용 커스텀 헤더

            // 호스트 헤더 수정 (백엔드가 인식하도록)
            request.headers().set(HttpHeaderNames.HOST, remoteHost + ":" + remotePort);

            // retain(): 큐에 넣거나 전송할 때 메모리 해제 방지
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
