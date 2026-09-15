package com.booster.dday.shared.outbox;

import com.booster.common.JsonUtils;
import com.booster.common.SnowflakeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link DomainOutbox} 의 구현. 하는 일은 네이티브 {@code INSERT} 한 줄이다.
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 이 메서드는 <b>부르는 쪽의
 * 트랜잭션에 얹혀야</b> 한다. 여기에 {@code REQUIRED} 를 붙이면 지금은 같은
 * 트랜잭션에 참여하니 문제가 없지만, 누가 {@code REQUIRES_NEW} 로 바꾸는 순간
 * 이벤트와 도메인 변경이 갈라지고 <b>그것을 아무것도 못 잡는다</b>. 애초에 안 붙여
 * 두면 트랜잭션 없이 부를 때 Spring Data 가
 * {@code TransactionRequiredException} 으로 막는다 — 갈라지는 대신 터진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaDomainOutbox implements DomainOutbox {

    private final OutboxEventRepository repository;

    @Override
    public boolean append(AggregateType type,
                          String aggregateId,
                          String eventType,
                          String partitionKey,
                          Object payload,
                          String idempotencyKey) {

        require(type != null, "집합체 갈래");
        require(hasText(aggregateId), "집합체 식별자");
        require(hasText(eventType), "사건 이름");
        require(hasText(partitionKey), "파티션 키");
        require(hasText(idempotencyKey), "멱등키");
        require(payload != null, "페이로드");

        int inserted = repository.insertIfAbsent(
                SnowflakeGenerator.nextId(),
                type.name(),
                aggregateId,
                eventType,
                partitionKey,
                idempotencyKey,
                JsonUtils.toJson(payload),
                /* 감사 칼럼. 사용자에게 보이는 「오늘」이 아니라 우리 기록의 시각이라
                   시계를 주입하지 않는다 — 주입하면 도메인마다 시계가 하나씩 는다 */
                LocalDateTime.now());

        if (inserted == 0) {
            log.debug("[Outbox] 이미 적혀 있다. type={}, idem={}", type, idempotencyKey);
            return false;
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new IllegalArgumentException(what + " 없이는 Outbox 에 적을 수 없다");
        }
    }
}
