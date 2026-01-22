package com.booster.minizuulservice.server.handler;

import com.booster.minizuulservice.server.LoadBalancer;
import com.booster.minizuulservice.server.ProxyAttributes;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;

@Slf4j
public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    /**
     * Pending Queue 최대 크기 - OOM 방지
     * 이 값을 초과하면 503 Service Unavailable 응답
     */
    private static final int MAX_PENDING_MESSAGES = 100;

    private final LoadBalancer loadBalancer;
    private final int maxRetries;
    private Channel outboundChannel;

    // 큐에 담아둘 때 메모리가 해제되지 않도록 주의해야 함
    private final Queue<Object> pendingMessages = new LinkedList<>();
    private int currentRetry = 0;
    private boolean isConnecting = false;
    // [P2] 현재 연결 시도 중인 서버 (Health Check용)
    private InetSocketAddress currentTarget;

    public ProxyFrontendHandler(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        // [P2] maxRetries를 서버 수에 맞게 동적 설정
        this.maxRetries = loadBalancer.getServerCount();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final Channel inboundChannel = ctx.channel();
        if (isConnecting) return;

        // [P0 FIX] 새 연결 시 재시도 카운트 초기화
        currentRetry = 0;
        isConnecting = true;

        attemptConnection(inboundChannel);
    }

    // [핵심] 연결 시도 및 재시도 로직 분리
    private void attemptConnection(Channel inboundChannel) {
        currentTarget = loadBalancer.getNextServer();
        log.info("Attempting connection to backend: {}:{} (Retry: {}/{})",
                currentTarget.getHostString(), currentTarget.getPort(), currentRetry, maxRetries);

        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(inboundChannel.getClass())
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(65536));
                        p.addLast(new ProxyBackendHandler(inboundChannel));
                    }
                });

        ChannelFuture f = b.connect(currentTarget.getHostString(), currentTarget.getPort());
        outboundChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // [P2] Health Check: 연결 성공 기록
                loadBalancer.recordSuccess(currentTarget);
                log.info("Target Connected: {}:{}", currentTarget.getHostString(), currentTarget.getPort());
                isConnecting = false;
                flushPendingMessages();
            } else {
                // [P2] Health Check: 연결 실패 기록
                loadBalancer.recordFailure(currentTarget);
                log.warn("Failed to connect to {}:{}", currentTarget.getHostString(), currentTarget.getPort());

                if (currentRetry < maxRetries) {
                    currentRetry++;
                    log.info("Failover triggered! Trying next server...");
                    // 재귀 호출로 다음 서버 시도 (큐에 있는 메시지는 그대로 유지됨)
                    attemptConnection(inboundChannel);
                } else {
                    log.error("All backends are down or max retries reached.");
                    clearPendingMessages();
                    // [P0 FIX] 에러 응답 전송 후 연결 종료
                    sendErrorResponse(inboundChannel, HttpResponseStatus.BAD_GATEWAY,
                            "All backend servers are unavailable.");
                }
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // [P0 FIX] Pending Queue 크기 제한 - OOM 방지
        if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
            log.warn("Pending queue full ({} messages). Rejecting request.", MAX_PENDING_MESSAGES);
            ReferenceCountUtil.release(msg);
            sendErrorResponse(ctx.channel(), HttpResponseStatus.SERVICE_UNAVAILABLE,
                    "Server is overloaded. Please try again later.");
            return;
        }

        if (msg instanceof FullHttpRequest request) {
            // [추가] 1. 요청 시작 시간 기록 (나노초 단위가 정밀함)
            ctx.channel().attr(ProxyAttributes.START_TIME).set(System.nanoTime());

            // [추가] 2. 요청 URI 기록
            ctx.channel().attr(ProxyAttributes.REQUEST_URI).set(request.uri());

            // [P2] 3. HTTP Method 기록 (Access Log용)
            ctx.channel().attr(ProxyAttributes.HTTP_METHOD).set(request.method().name());

            // [P2] Proxy 헤더 보완
            String clientIp = extractClientIp(ctx.channel());
            request.headers().set("X-Forwarded-For", clientIp);
            request.headers().set("X-Real-IP", clientIp);
            request.headers().set("X-Forwarded-Proto", "http");  // TODO: TLS 지원 시 동적으로
            if (request.headers().contains(HttpHeaderNames.HOST)) {
                request.headers().set("X-Forwarded-Host", request.headers().get(HttpHeaderNames.HOST));
            }

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
            ReferenceCountUtil.release(msg);
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

    /**
     * 클라이언트 IP 추출
     */
    private String extractClientIp(Channel channel) {
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * HTTP 에러 응답을 클라이언트에게 전송
     */
    private void sendErrorResponse(Channel channel, HttpResponseStatus status, String message) {
        byte[] content = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(content)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
