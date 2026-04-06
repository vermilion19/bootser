package com.booster.queryburst.order.application;

import com.booster.queryburst.lock.DistributedLock;
import com.booster.queryburst.lock.FencingToken;
import com.booster.queryburst.lock.RedisUnavailableException;
import com.booster.queryburst.order.application.dto.OrderCreateCommand;
import com.booster.queryburst.order.application.dto.OrderItemCommand;
import com.booster.queryburst.order.application.dto.OrderResult;
import com.booster.queryburst.order.exception.DuplicateRequestException;
import com.booster.queryburst.order.web.dto.request.OrderCreateRequest;
import com.booster.queryburst.order.web.dto.request.OrderItemRequest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 생성 파사드.
 *
 * <h2>책임</h2>
 * <ol>
 *   <li>멱등성 검사 — 중복 요청 차단 및 캐시 결과 반환</li>
 *   <li>분산 락 획득 — 펜싱 토큰 발급 후 OrderService 위임</li>
 *   <li>Redis 장애 Fallback — DB 비관적 락으로 자동 전환</li>
 * </ol>
 *
 * <h2>흐름</h2>
 * <pre>
 * placeOrder(request, idempotencyKey)
 *   ├─ idempotencyKey 존재 시 → checkAndMarkProcessing
 *   │    ├─ AlreadyCompleted  → 캐시 결과 즉시 반환
 *   │    └─ AlreadyProcessing → DuplicateRequestException
 *   │
 *   ├─ Redis 분산 락 획득 시도
 *   │    └─ RedisUnavailableException → DB 비관적 락 Fallback
 *   │
 *   └─ 완료 후 idempotencyKey 있으면 markCompleted
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final String LOCK_KEY_PREFIX = "product:%d:stock";

    private final DistributedLock distributedLock;
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;

    public OrderResult placeOrder(OrderCreateRequest request, String idempotencyKey) {

        // ── 1. 멱등성 검사 ─────────────────────────────────────────
        if (idempotencyKey != null) {
            IdempotencyCheck check = idempotencyService.checkAndMarkProcessing(idempotencyKey);
            switch (check) {
                case IdempotencyCheck.AlreadyCompleted c -> {
                    log.info("[OrderFacade] 멱등성 캐시 반환. key={}", idempotencyKey);
                    return c.result();
                }
                case IdempotencyCheck.AlreadyProcessing ignored ->
                    throw new DuplicateRequestException(idempotencyKey);
                case IdempotencyCheck.Proceed ignored -> { /* 새 요청, 계속 진행 */ }
            }
        }

        // ── 2. 주문 처리 (Redis 락 or DB 비관적 락) ─────────────────
        OrderResult result;
        try {
            result = placeOrderWithDistributedLock(request);
        } catch (RedisUnavailableException e) {
            log.warn("[OrderFacade] Redis 장애 감지 — DB 비관적 락으로 fallback. 사유: {}", e.getMessage());
            meterRegistry.counter("order_redis_fallback_total").increment();
            result = placeOrderWithPessimisticLock(request);
        }

        // ── 3. 멱등성 완료 마킹 ────────────────────────────────────
        if (idempotencyKey != null) {
            idempotencyService.markCompleted(idempotencyKey, result);
        }

        return result;
    }

    // ── Redis 분산 락 경로 ────────────────────────────────────────────

    private OrderResult placeOrderWithDistributedLock(OrderCreateRequest request) {
        // 다중 상품 데드락 방지: productId 오름차순 정렬
        List<Long> productIds = request.items().stream()
                .map(OrderItemRequest::productId)
                .sorted()
                .distinct()
                .toList();

        List<String> lockKeys = productIds.stream()
                .map(id -> LOCK_KEY_PREFIX.formatted(id))
                .toList();

        Map<Long, Long> fencingTokens = new HashMap<>();
        List<FencingToken> acquiredTokens = new ArrayList<>();

        try {
            for (int i = 0; i < productIds.size(); i++) {
                FencingToken token = distributedLock.tryLock(lockKeys.get(i), LOCK_TTL);
                acquiredTokens.add(token);
                fencingTokens.put(productIds.get(i), token.value());
            }

            List<OrderItemCommand> itemCommands = toItemCommands(request);
            return orderService.createOrder(new OrderCreateCommand(
                    request.memberId(), itemCommands, fencingTokens));

        } finally {
            // 역순 해제 (Redis 장애 시 unlock 내부에서 swallow)
            for (int i = acquiredTokens.size() - 1; i >= 0; i--) {
                distributedLock.unlock(lockKeys.get(i), acquiredTokens.get(i));
            }
        }
    }

    // ── DB 비관적 락 Fallback 경로 ────────────────────────────────────

    private OrderResult placeOrderWithPessimisticLock(OrderCreateRequest request) {
        List<OrderItemCommand> itemCommands = toItemCommands(request);
        return orderService.createOrderWithPessimisticLock(request.memberId(), itemCommands);
    }

    private List<OrderItemCommand> toItemCommands(OrderCreateRequest request) {
        return request.items().stream()
                .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
                .toList();
    }
}
