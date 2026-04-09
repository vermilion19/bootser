package com.booster.queryburst.order.application;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburst.order.application.dto.OrderItemCommand;
import com.booster.queryburst.order.application.dto.OrderResult;
import com.booster.queryburst.order.event.FlashSaleOrderPayload;
import com.booster.queryburst.product.domain.Product;
import com.booster.queryburst.product.domain.ProductRepository;
import com.booster.storage.kafka.core.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private static final String STOCK_KEY_PREFIX = "FLASH:";
    private static final Duration STOCK_TTL = Duration.ofHours(24);
    private static final String RESERVE_STOCK_SCRIPT = """
            local key = KEYS[1]
            local initialStock = tonumber(ARGV[1])
            local quantity = tonumber(ARGV[2])
            local ttlMillis = tonumber(ARGV[3])
            local current = redis.call('GET', key)
            if not current then
                current = initialStock
                redis.call('SET', key, current, 'PX', ttlMillis)
            else
                current = tonumber(current)
            end
            if current < quantity then
                return -1
            end
            local remaining = redis.call('DECRBY', key, quantity)
            redis.call('PEXPIRE', key, ttlMillis)
            return remaining
            """;

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderService orderService;

    public OrderResult requestOrder(Long memberId, Long productId, int quantity) {
        validateRequest(memberId, quantity);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + productId));

        boolean reserved = reserveStock(productId, product.getStock(), quantity);
        if (!reserved) {
            throw new IllegalStateException("플래시 세일 재고가 부족합니다.");
        }

        Long orderId = SnowflakeGenerator.nextId();
        FlashSaleOrderPayload payload = FlashSaleOrderPayload.of(orderId, memberId, productId, quantity);
        kafkaTemplate.send(KafkaTopic.FLASH_SALE_ORDERS.getTopic(), String.valueOf(orderId), payload);
        log.info("[FlashSale] 주문 접수 완료. orderId={}, productId={}, quantity={}", orderId, productId, quantity);
        return new OrderResult(orderId, product.getPrice() * quantity);
    }

    public OrderResult processOrder(FlashSaleOrderPayload payload) {
        return orderService.createFlashSaleOrder(
                payload.orderId(),
                payload.memberId(),
                List.of(new OrderItemCommand(payload.productId(), payload.quantity()))
        );
    }

    public void compensateStock(Long productId, int quantity) {
        String key = buildStockKey(productId);
        redissonClient.getAtomicLong(key).addAndGet(quantity);
        redissonClient.getBucket(key).expire(STOCK_TTL);
        log.warn("[FlashSale] 재고 보상 처리. productId={}, quantity={}", productId, quantity);
    }

    private boolean reserveStock(Long productId, int initialStock, int quantity) {
        String key = buildStockKey(productId);
        Long result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                RESERVE_STOCK_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(key),
                initialStock,
                quantity,
                STOCK_TTL.toMillis()
        );
        return result != null && result >= 0;
    }

    private void validateRequest(Long memberId, int quantity) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }

    private String buildStockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId + ":stock";
    }
}
