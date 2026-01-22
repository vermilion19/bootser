package com.booster.minizuulservice.server;

import com.booster.minizuulservice.server.handler.ProxyFrontendHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
@RequiredArgsConstructor
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final LoadBalancer loadBalancer;
    @Override
    protected void initChannel(SocketChannel ch){
        ChannelPipeline p = ch.pipeline();

        // 1. HTTP 코덱: ByteBuf <-> HttpRequest/HttpResponse 변환
        p.addLast(new HttpServerCodec());

        // 2. HTTP 메시지 집계: 헤더와 바디 조각들을 하나의 FullHttpRequest로 합침 (최대 512KB)
        p.addLast(new HttpObjectAggregator(512 * 1024));
        InetSocketAddress nextTarget = loadBalancer.getNextServer();
//        p.addLast(new com.booster.minizuulservice.server.handler.ProxyFrontendHandler("localhost", 8080));
        p.addLast(new ProxyFrontendHandler(loadBalancer));

    }
}
