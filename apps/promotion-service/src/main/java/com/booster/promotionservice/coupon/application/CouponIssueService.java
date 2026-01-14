package com.booster.promotionservice.coupon.application;

import com.booster.promotionservice.coupon.application.dto.IssueCouponCommand;
import com.booster.promotionservice.coupon.domain.*;
import com.booster.promotionservice.coupon.exception.AlreadyIssuedCouponException;
import com.booster.promotionservice.coupon.exception.CouponPolicyNotFoundException;
import com.booster.promotionservice.coupon.exception.CouponSoldOutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CouponIssueService {

    private final CouponPolicyRepository couponPolicyRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";

    private static final String ISSUE_COUPON_SCRIPT = """
            -- KEYS[1]: 재고 키, KEYS[2]: 발급자 Set
            -- ARGV[1]: 사용자 ID

            -- 1. 중복 발급 확인
            local alreadyIssued = redis.call('SISMEMBER', KEYS[2], ARGV[1])
            if alreadyIssued == 1 then
                return -2
            end

            -- 2. 재고 확인
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil or stock <= 0 then
                return -1
            end

            -- 3. 원자적으로 재고 차감 + 발급자 기록
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])

            return 1
            """;

    public IssuedCoupon issue(IssueCouponCommand command) {
        Long policyId = command.couponPolicyId();
        Long userId = command.userId();

        // 1. Redis Lua Script로 원자적 발급 처리
        Long result = executeIssueCouponScript(policyId, userId);

        // 2. 결과에 따른 처리
        if (result == -2) {
            throw new AlreadyIssuedCouponException();
        }
        if (result == -1) {
            throw new CouponSoldOutException();
        }

        // 3. DB에 발급 이력 저장
        CouponPolicy policy = couponPolicyRepository.findById(policyId)
                .orElseThrow(CouponPolicyNotFoundException::new);

        policy.incrementIssuedQuantity();

        IssuedCoupon issuedCoupon = IssuedCoupon.create(policyId, userId, policy.getExpireAt());
        return issuedCouponRepository.save(issuedCoupon);
    }

    public void initializeCouponStock(Long policyId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        redisTemplate.opsForValue().set(stockKey, quantity);
        log.info("쿠폰 재고 초기화: policyId={}, quantity={}", policyId, quantity);
    }

    public int getRemainingStock(Long policyId) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        Object stock = redisTemplate.opsForValue().get(stockKey);
        return stock != null ? ((Number) stock).intValue() : 0;
    }

    public boolean hasAlreadyIssued(Long policyId, Long userId) {
        String issuedKey = ISSUED_KEY_PREFIX + policyId;
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedKey, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    private Long executeIssueCouponScript(Long policyId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        String issuedKey = ISSUED_KEY_PREFIX + policyId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ISSUE_COUPON_SCRIPT, Long.class);

        return redisTemplate.execute(
                script,
                List.of(stockKey, issuedKey),
                userId.toString()
        );
    }
}
