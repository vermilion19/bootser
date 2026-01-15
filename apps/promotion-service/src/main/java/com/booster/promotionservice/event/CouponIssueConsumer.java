package com.booster.promotionservice.event;

import com.booster.promotionservice.domain.CouponIssueRepository;
import com.booster.promotionservice.domain.entity.CouponIssue;
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
        try {
            // 1. 메시지 파싱 (Format: "userId:couponId")
            // 실무에서는 JSON(ObjectMapper)을 쓰지만, 여기선 문자열로 간단히 처리
            String[] parts = message.split(":");
            Long userId = Long.parseLong(parts[0]);
            Long couponId = Long.parseLong(parts[1]);

            log.info("쿠폰 발급 요청 수신 - CouponId: {}, UserId: {}", couponId, userId);

            // 2. 엔티티 생성 및 저장
            CouponIssue couponIssue = new CouponIssue(couponId, userId);
            couponIssueRepository.save(couponIssue);

            // TODO: (선택사항) Coupon 테이블의 issuedQuantity 증가 로직 추가 가능
            // couponRepository.findById(couponId).ifPresent(Coupon::increaseIssuedQuantity);

        } catch (DataIntegrityViolationException e) {
            // 3. 중복 발급 예외 처리 (매우 중요)
            // Redis가 터지거나 네트워크 이슈로 메시지가 두 번 왔을 때 DB 유니크 제약조건이 막아줌.
            // 이건 "에러"가 아니라 "이미 처리된 건"으로 보고 그냥 로그 찍고 넘어가야 함.
            log.warn("이미 발급된 쿠폰입니다. (중복 이슈 무시) - Message: {}", message);
        } catch (Exception e) {
            // 4. 그 외 에러는 로그를 남기고, 추후 Dead Letter Queue(DLQ)로 보내거나 재시도해야 함
            log.error("쿠폰 발급 중 시스템 오류 발생: {}", message, e);
            // 여기서 throw를 던지면 Kafka가 재시도를 수행함 (Default 설정 시)
        }
    }
}
