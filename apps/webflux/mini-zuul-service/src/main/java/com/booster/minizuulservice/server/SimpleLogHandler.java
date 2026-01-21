package com.booster.minizuulservice.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class SimpleLogHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // 1. 요청 로그 출력
        log.info(">>> [Request] {} {}", req.method(), req.uri());
        log.info(">>> [Headers] {}", req.headers());

        // 2. 간단한 응답 생성 (프록시 기능 구현 전 테스트용)
        String msg = "Netty Proxy is Alive!";
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(msg, StandardCharsets.UTF_8)
        );

        // 3. 헤더 설정 (필수: Content-Length가 없으면 브라우저는 계속 기다림)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        // 4. 전송 후 채널 닫기 (Short-lived connection)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Pipeline Error", cause);
        ctx.close();
    }
}
