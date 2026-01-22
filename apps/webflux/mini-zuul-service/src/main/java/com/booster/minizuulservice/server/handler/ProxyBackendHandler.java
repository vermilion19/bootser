package com.booster.minizuulservice.server.handler;

import com.booster.minizuulservice.server.ProxyAttributes;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RequiredArgsConstructor
public class ProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final Channel inboundChannel; // 클라이언트와 연결된 채널

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Backend Connected: {}", ctx.channel().remoteAddress());
        ctx.read(); // 데이터 읽기 시작
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // [P2] HTTP 상태 코드 추출
        int statusCode = 0;
        if (msg instanceof FullHttpResponse response) {
            statusCode = response.status().code();
            inboundChannel.attr(ProxyAttributes.HTTP_STATUS).set(statusCode);
        }

        final int finalStatusCode = statusCode;

        // 클라이언트에게 토스
        inboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                recordAccessLog(ctx, finalStatusCode);
                // 다음 데이터 읽기 (Flow Control)
                ctx.channel().read();
            } else {
                log.error("<<< Forwarding to Client Failed", future.cause());
                future.channel().close();
            }
        });
    }

    /**
     * [P2] 개선된 Access Log 형식
     * 타임스탬프 | HTTP Method | URI | 상태코드 | 지연시간 | 클라이언트 | 백엔드
     */
    private void recordAccessLog(ChannelHandlerContext backendCtx, int statusCode) {
        Long startTime = inboundChannel.attr(ProxyAttributes.START_TIME).get();
        String uri = inboundChannel.attr(ProxyAttributes.REQUEST_URI).get();
        String method = inboundChannel.attr(ProxyAttributes.HTTP_METHOD).get();

        if (startTime != null) {
            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
            String clientAddr = extractAddress(inboundChannel.remoteAddress());
            String backendAddr = extractAddress(backendCtx.channel().remoteAddress());

            // [P2] 개선된 Access Log Format
            // 타임스탬프 | METHOD URI | STATUS | TIME | client -> backend
            log.info("ACCESS_LOG: {} | {} {} | {} | {}ms | {} -> {}",
                    timestamp,
                    method != null ? method : "UNKNOWN",
                    uri != null ? uri : "/",
                    statusCode > 0 ? statusCode : "-",
                    String.format("%.2f", durationMs),
                    clientAddr,
                    backendAddr
            );
        }
    }

    private String extractAddress(java.net.SocketAddress addr) {
        if (addr instanceof java.net.InetSocketAddress inet) {
            return inet.getAddress().getHostAddress() + ":" + inet.getPort();
        }
        return addr != null ? addr.toString() : "unknown";
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        // 백엔드 연결이 끊기면 클라이언트 연결도 끊어줌 (Short-lived)
        log.info("Backend Disconnected");
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(io.netty.buffer.Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Backend Handler Error", cause);
        ctx.close();
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(io.netty.buffer.Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
