package com.booster.queryburst.order.web;

import com.booster.queryburst.member.web.dto.response.CursorPageResponse;
import com.booster.queryburst.order.application.OrderFacade;
import com.booster.queryburst.order.application.OrderService;
import com.booster.queryburst.order.application.dto.*;
import com.booster.queryburst.order.domain.OrderStatus;
import com.booster.queryburst.order.web.dto.request.OrderCreateRequest;
import com.booster.queryburst.order.web.dto.response.OrderDetailResponse;
import com.booster.queryburst.order.web.dto.response.OrderResponse;
import com.booster.queryburst.order.web.dto.response.OrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderService orderService;

    // ── 조회 ──────────────────────────────────────────────────────────────────

    /**
     * 커서 기반 주문 목록 조회.
     *
     * 필터: memberId, status
     * 정렬: id DESC (Snowflake ID → 최신순)
     *
     * 인덱스 힌트:
     * - memberId만: idx_orders_member_ordered_at
     * - status만: idx_orders_status_ordered_at
     */
    @GetMapping
    public ResponseEntity<CursorPageResponse<OrderSummaryResponse>> getOrders(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<OrderSummaryResult> fetched = orderService.getOrders(cursor, memberId, status, size);

        boolean hasNext = fetched.size() > size;
        List<OrderSummaryResponse> content = fetched.stream()
                .limit(size)
                .map(OrderSummaryResponse::from)
                .toList();
        Long nextCursor = hasNext ? content.getLast().orderId() : null;

        return ResponseEntity.ok(CursorPageResponse.of(content, hasNext, nextCursor));
    }

    /**
     * 주문 상세 조회 (주문 항목 포함).
     *
     * Member 페치 조인 + OrderItem 별도 쿼리로 N+1 방지.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(OrderDetailResponse.from(orderService.getOrderDetail(orderId)));
    }

    /**
     * 월별 매출 집계.
     *
     * 쿼리 실습: DATE_TRUNC('month', ordered_at) + GROUP BY
     * 인덱스: idx_orders_status_ordered_at (DELIVERED 필터 + 기간 조건)
     */
    @GetMapping("/stats/monthly-sales")
    public ResponseEntity<List<MonthlySalesResult>> getMonthlySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(orderService.getMonthlySales(from, to));
    }

    /**
     * 상품별 판매 통계 TOP N.
     *
     * 커버링 인덱스: idx_order_item_covering (product_id, quantity, unit_price)
     */
    @GetMapping("/stats/top-products")
    public ResponseEntity<List<ProductSalesResult>> getTopSellingProducts(
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(orderService.getTopSellingProducts(size));
    }

    // ── 생성 ──────────────────────────────────────────────────────────────────

    /**
     * 주문 생성.
     *
     * - 분산 락 + 펜싱 토큰으로 동시성 제어
     * - Idempotency-Key 헤더: 클라이언트가 UUID를 발급하여 중복 요청 방지 (선택)
     *   동일 키로 재요청 시 캐시된 결과 반환 (24시간 유지)
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        OrderResult result = orderFacade.placeOrder(request, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/api/orders/" + result.orderId()))
                .body(OrderResponse.from(result));
    }

    // ── 상태 전환 ─────────────────────────────────────────────────────────────

    /** 결제 처리 (PENDING → PAID) */
    @PatchMapping("/{orderId}/pay")
    public ResponseEntity<Void> pay(@PathVariable Long orderId) {
        orderService.pay(orderId);
        return ResponseEntity.noContent().build();
    }

    /** 배송 시작 (PAID → SHIPPED) */
    @PatchMapping("/{orderId}/ship")
    public ResponseEntity<Void> ship(@PathVariable Long orderId) {
        orderService.ship(orderId);
        return ResponseEntity.noContent().build();
    }

    /** 배송 완료 (SHIPPED → DELIVERED) */
    @PatchMapping("/{orderId}/deliver")
    public ResponseEntity<Void> deliver(@PathVariable Long orderId) {
        orderService.deliver(orderId);
        return ResponseEntity.noContent().build();
    }

    /** 주문 취소 (DELIVERED 제외 모든 상태에서 가능) */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId);
        return ResponseEntity.noContent().build();
    }
}
