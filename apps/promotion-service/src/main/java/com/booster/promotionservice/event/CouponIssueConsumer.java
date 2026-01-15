package com.booster.promotionservice.event;

import com.booster.promotionservice.domain.CouponIssueRepository;
import com.booster.promotionservice.domain.entity.CouponIssue;
import com.booster.promotionservice.exception.InvalidMessageFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponIssueRepository couponIssueRepository;

    @KafkaListener(topics = "coupon.issue.request", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void listen(String message) {
        Long userId;
        Long couponId;

        // 1. 메시지 파싱 (Format: "userId:couponId")
        try {
            String[] parts = message.split(":");
            if (parts.length != 2) {
                throw new InvalidMessageFormatException(message);
            }
            userId = Long.parseLong(parts[0]);
            couponId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new InvalidMessageFormatException(message);
        }

        log.info("쿠폰 발급 요청 수신 - CouponId: {}, UserId: {}", couponId, userId);

        // 2. 엔티티 생성 및 저장
        try {
            CouponIssue couponIssue = new CouponIssue(couponId, userId);
            couponIssueRepository.save(couponIssue);
        } catch (DataIntegrityViolationException e) {
            // 중복 발급은 "에러"가 아니라 "이미 처리된 건"으로 보고 로그만 남기고 정상 처리
            // DLQ로 보내지 않음 (재시도해도 계속 실패하므로)
            log.warn("이미 발급된 쿠폰입니다. (중복 이슈 무시) - CouponId: {}, UserId: {}", couponId, userId);
            return;
        }

        // TODO: (선택사항) Coupon 테이블의 issuedQuantity 증가 로직 추가 가능
        // couponRepository.findById(couponId).ifPresent(Coupon::increaseIssuedQuantity);
    }
}
