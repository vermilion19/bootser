package com.booster.minizuulservice.integration;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mini-Zuul 프록시 통합 테스트
 *
 * 테스트 구조:
 * Client → Mini-Zuul (8888) → Mock Backend (8081, 8082)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyIntegrationTest {

    private static final int PROXY_PORT = 18888;  // 테스트용 포트
    private static final int BACKEND_PORT_1 = 18081;
    private static final int BACKEND_PORT_2 = 18082;

    private Channel backendChannel1;
    private Channel backendChannel2;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final AtomicInteger backend1RequestCount = new AtomicInteger(0);
    private final AtomicInteger backend2RequestCount = new AtomicInteger(0);

    @BeforeAll
    void startMockBackends() throws InterruptedException {
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        // Backend 1 시작
        backendChannel1 = startMockBackend(BACKEND_PORT_1, "Backend-1", backend1RequestCount);

        // Backend 2 시작
        backendChannel2 = startMockBackend(BACKEND_PORT_2, "Backend-2", backend2RequestCount);

        // 서버가 완전히 시작될 때까지 대기
        Thread.sleep(500);
    }

    @AfterAll
    void stopMockBackends() {
        if (backendChannel1 != null) backendChannel1.close();
        if (backendChannel2 != null) backendChannel2.close();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
    }

    @BeforeEach
    void resetCounters() {
        backend1RequestCount.set(0);
        backend2RequestCount.set(0);
    }

    private Channel startMockBackend(int port, String name, AtomicInteger counter) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new MockBackendHandler(name, counter));
                    }
                });

        return b.bind(port).sync().channel();
    }

    @Nested
    @DisplayName("Mock Backend 테스트")
    class MockBackendTest {

        @Test
        @DisplayName("Mock Backend가 정상 응답한다")
        void mockBackendResponds() throws Exception {
            // given
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + BACKEND_PORT_1 + "/api/test"))
                    .GET()
                    .build();

            // when
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Backend-1");
            assertThat(backend1RequestCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("NettyProperties 테스트")
    class NettyPropertiesTest {

        @Test
        @DisplayName("HealthCheckConfig 기본값이 설정된다")
        void healthCheckConfigDefaults() {
            // given
            var config = new com.booster.minizuulservice.config.NettyProperties.HealthCheckConfig(0, 0);

            // then - 기본값으로 설정됨
            assertThat(config.failureThreshold()).isEqualTo(3);
            assertThat(config.recoveryTimeMs()).isEqualTo(30000);
        }

        @Test
        @DisplayName("HealthCheckConfig 커스텀 값이 유지된다")
        void healthCheckConfigCustomValues() {
            // given
            var config = new com.booster.minizuulservice.config.NettyProperties.HealthCheckConfig(5, 60000);

            // then
            assertThat(config.failureThreshold()).isEqualTo(5);
            assertThat(config.recoveryTimeMs()).isEqualTo(60000);
        }
    }

    /**
     * Mock Backend Handler
     * 간단한 JSON 응답 반환
     */
    private static class MockBackendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final String name;
        private final AtomicInteger counter;

        MockBackendHandler(String name, AtomicInteger counter) {
            this.name = name;
            this.counter = counter;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            counter.incrementAndGet();

            String responseBody = String.format(
                    "{\"server\":\"%s\",\"uri\":\"%s\",\"method\":\"%s\"}",
                    name, request.uri(), request.method()
            );

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(responseBody, CharsetUtil.UTF_8)
            );

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
