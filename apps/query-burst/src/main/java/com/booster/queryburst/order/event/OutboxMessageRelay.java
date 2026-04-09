package com.booster.queryburst.order.event;

import com.booster.common.JsonUtils;
import com.booster.queryburst.lock.DistributedLock;
import com.booster.queryburst.lock.FencingToken;
import com.booster.queryburst.lock.LockAcquisitionException;
import com.booster.queryburst.order.domain.outbox.OutboxEvent;
import com.booster.queryburst.order.domain.outbox.OutboxEventRepository;
import com.booster.queryburst.order.domain.outbox.OutboxStatus;
import com.booster.storage.kafka.core.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 이벤트 릴레이 (Polling Publisher).
 *
 * 3초마다 PENDING 이벤트를 읽어 Kafka로 발행한다.
 *
 * <h2>동시성 제어</h2>
 * Scale-out 환경에서 여러 인스턴스가 동시에 실행되면 중복 발행이 발생할 수 있다.
 * Redis 분산 락을 이용해 단일 인스턴스만 실행되도록 보장한다.
 *
 * <h2>AOP self-invocation 방지</h2>
 * @Scheduled 메서드(schedule)는 @Transactional을 직접 붙이지 않고
 * TransactionTemplate을 통해 프로그래밍 방식으로 트랜잭션을 관리한다.
 * → 같은 Bean 내 self-invocation으로 인한 @Transactional 무시 문제 원천 차단.
 *
 * <h2>전달 보장</h2>
 * Kafka ACK 성공 후 PUBLISHED 마킹 → At-Least-Once Delivery.
 * Consumer는 멱등성 처리 필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageRelay {

    private static final String RELAY_LOCK_KEY = "outbox:relay:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DistributedLock distributedLock;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 3000)
    public void schedule() {
        FencingToken token;
        try {
            token = distributedLock.tryLock(RELAY_LOCK_KEY, LOCK_TTL);
        } catch (LockAcquisitionException e) {
            // 다른 인스턴스가 실행 중 → 이번 턴은 스킵
            log.debug("[OutboxRelay] 다른 인스턴스 실행 중, 스킵.");
            return;
        }

        try {
            publishPendingEvents();
        } finally {
            distributedLock.unlock(RELAY_LOCK_KEY, token);
        }
    }

    /**
     * PENDING 이벤트 조회 → Kafka 발행 → 상태 갱신.
     *
     * TransactionTemplate으로 트랜잭션 범위를 명시적으로 제어한다.
     * Kafka send().get() 호출이 블로킹이므로 트랜잭션 내에서 커넥션이 유지되지만,
     * 배치 크기(100건)와 로컬 Kafka RTT(~2ms) 기준으로 약 200ms 이내 완료된다.
     */
    private void publishPendingEvents() {
        transactionTemplate.execute(status -> {
            List<OutboxEvent> events = outboxEventRepository
                    .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

            if (events.isEmpty()) return null;

            log.info("[OutboxRelay] {} 건 발행 시작", events.size());

            for (OutboxEvent event : events) {
                try {
                    OrderEventPayload payload = JsonUtils.MAPPER
                            .readValue(event.getPayload(), OrderEventPayload.class);

                    kafkaTemplate.send(
                                    KafkaTopic.ORDER_EVENTS.getTopic(),
                                    String.valueOf(event.getAggregateId()),
                                    payload)
                            .get(5, TimeUnit.SECONDS);  // 동기 ACK 대기

                    event.markPublished();
                    log.debug("[OutboxRelay] 발행 성공. eventId={}, type={}", event.getId(), event.getEventType());

                } catch (Exception e) {
                    log.error("[OutboxRelay] 발행 실패. eventId={}, retryCount={}",
                            event.getId(), event.getRetryCount(), e);
                    event.incrementRetry();
                }
            }
            return null;
        });
    }
}
