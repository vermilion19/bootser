package com.booster.minizuulservice.server.handler;

import com.booster.minizuulservice.server.ProxyAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel; // 클라이언트와 연결된 채널

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Backend Connected: {}", ctx.channel().remoteAddress());
        ctx.read(); // 데이터 읽기 시작
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        // 클라이언트에게 토스
        inboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("<<< Forwarded to Client Success");
                recordAccessLog(ctx);
                // 다음 데이터 읽기 (Flow Control)
//                ctx.channel().read();
                inboundChannel.close();
            } else {
                log.error("<<< Forwarding to Client Failed", future.cause());
                future.channel().close();
            }
        });
    }

    private void recordAccessLog(ChannelHandlerContext backendCtx) {
        // inboundChannel(클라이언트 연결)에 붙여둔 포스트잇을 떼어봅니다.
        Long startTime = inboundChannel.attr(ProxyAttributes.START_TIME).get();
        String uri = inboundChannel.attr(ProxyAttributes.REQUEST_URI).get();

        if (startTime != null) {
            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0; // 밀리초 변환

            // [Access Log Format]
            // [Client IP] -> [Target Backend] : URI (Time ms)
            log.info("ACCESS_LOG: [{}] -> [{}] : {} (Took {} ms)",
                    inboundChannel.remoteAddress(),
                    backendCtx.channel().remoteAddress(),
                    uri,
                    String.format("%.2f", durationMs)
            );
        }
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
