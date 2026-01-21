package com.booster.minizuulservice.server;

import com.booster.minizuulservice.config.NettyProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NettyServer {

    private final NettyProperties nettyProperties;
    private final NettyChannelInitializer nettyChannelInitializer;

    public void start() {
        log.info("Netty Server started on port: {}", nettyProperties.port());

        try (EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
             EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())) {

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(nettyChannelInitializer);
            ChannelFuture future = b.bind(nettyProperties.port()).sync();

            future.channel().closeFuture().sync();


        } catch (InterruptedException e) {
            log.warn("Server interrupted", e);
            Thread.currentThread().interrupt();
        }

    }
}
