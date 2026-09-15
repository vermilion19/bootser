package com.booster.dday.shared.outbox;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 를 Kafka 로 옮기는 릴레이 — 저장소의 <b>3세대</b>를 베낀 것.
 *
 * <p>ARCHITECTURE §3.1 이 저장소의 세 세대를 재 보고 3세대를 고른 자리다.
 *
 * <table>
 *   <caption>세대별 갖춘 것</caption>
 *   <tr><td>1세대 ({@code waiting-service})</td>
 *       <td>분산 락 없음 → 인스턴스 2대면 같은 이벤트를 둘 다 발행.
 *           배치 상한 없음 → 밀린 이벤트가 많으면 한 트랜잭션이 무한정 길어진다</td></tr>
 *   <tr><td>2세대 ({@code query-burst-msa})</td>
 *       <td><b>깨져 있다.</b> {@code relay()} 가 같은 클래스의
 *           {@code @Transactional} 메서드를 직접 불러 프록시를 안 거친다 →
 *           <b>상태기계가 DB 에 한 글자도 안 쓰인다</b></td></tr>
 *   <tr><td>3세대 ({@code query-burst})</td>
 *       <td>분산 락 · {@link TransactionTemplate} · stale 회수 · 배치 상한 100 ·
 *           {@code get(5, SECONDS)}</td></tr>
 * </table>
 *
 * <p><b>{@code @Transactional} 이 이 클래스에 한 글자도 없는 것이 2세대와의 차이다.</b>
 * 트랜잭션은 {@link TransactionTemplate} 으로 <b>명시적으로</b> 연다. 자기호출 함정이
 * 있을 자리를 아예 없앤다 ({@code .claude/rules/behavior.md} 의 AOP 체크리스트).
 *
 * <p>3세대에서 바꾼 것은 <b>락 하나</b>다. 3세대의 락은 {@code apps/query-burst}
 * 안에 살아서 {@code apps/* → apps/*} 금지에 걸린다. ShedLock 은
 * {@code waiting-service} 가 이미 쓰고 있다 (§3.2).
 *
 * <h2>{@code @SchedulerLock} 을 안 쓰고 {@link LockProvider} 를 직접 잡는다</h2>
 *
 * <p>ARCHITECTURE §5.6 의 규칙이다. {@code @SchedulerLock} 은 스케줄 메서드에만
 * 붙는데, 운영자가 누르는 수동 트리거는 스케줄 메서드가 아니다. 애노테이션만 믿으면
 * 스케줄러가 도는 중에 운영자가 눌러 <b>이중 실행</b>이 된다. 릴레이에는 아직 수동
 * 트리거가 없지만, <b>락을 잡는 방식이 스케줄 일곱 개에서 같아야</b> 그 규칙이
 * 지켜진다.
 *
 * <h2>락이 배치보다 짧다 — 알고 그렇게 둔다</h2>
 *
 * <p>{@code lockAtMostFor} 가 30초인데 한 배치는 최악의 경우 100건 × 5초다. Kafka 가
 * 죽어 있으면 락이 먼저 풀리고 다른 인스턴스가 들어온다. <b>그래도 짧게 둔다</b> —
 * 길게 잡으면 릴레이를 든 JVM 이 죽었을 때 그 시간만큼 알림이 멈춘다. 겹침은
 * {@code SKIP LOCKED} 와 소비 쪽 dedup 이 받아 낸다 (§3.5 의 멱등 두 겹).
 * <b>Outbox 는 원래 at-least-once 다.</b>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dday.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    static final String LOCK_NAME = "dday-outbox-relay";
    static final int BATCH_SIZE = 100;

    private static final Duration LOCK_AT_MOST_FOR = Duration.ofSeconds(30);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ZERO;
    private static final Duration SENDING_STALE_THRESHOLD = Duration.ofMinutes(1);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LockProvider lockProvider;
    private final TransactionTemplate transactionTemplate;

    /**
     * 값을 <b>문자열 그대로</b> 보내는 템플릿을 받는다.
     *
     * <p>페이로드는 이미 JSON 문자열이다. {@code KafkaTemplate<String, Object>} 로
     * 보내면 Jackson 직렬화기가 그것을 <b>한 번 더 감싸</b> 따옴표에 갇힌 문자열이
     * 되고, 소비 쪽은 JSON 을 두 번 풀어야 한다. 그리고 그것은 터지지 않고 그냥
     * 이상하게 동작한다.
     */
    public OutboxRelay(OutboxEventRepository repository,
                       @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                       LockProvider lockProvider,
                       TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.lockProvider = lockProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${dday.outbox.relay.fixed-delay-ms:3000}")
    public void schedule() {
        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR));

        if (lock.isEmpty()) {
            log.debug("[OutboxRelay] 다른 인스턴스가 돌고 있다");
            return;
        }
        try {
            publishPendingEvents();
        } finally {
            lock.get().unlock();
        }
    }

    private void publishPendingEvents() {
        List<Candidate> candidates = claim();
        if (candidates.isEmpty()) {
            return;
        }
        log.info("[OutboxRelay] {}건 발행 시작", candidates.size());

        for (Candidate candidate : candidates) {
            try {
                kafkaTemplate.send(
                                candidate.type().topic().getTopic(),
                                candidate.partitionKey(),
                                candidate.payload())
                        /* 기다린다. 안 기다리면 「보냈다」가 아니라 「보내라고 했다」를
                           PUBLISHED 로 적게 된다 */
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                markPublished(candidate.id());
                log.debug("[OutboxRelay] 발행 성공. id={}, type={}", candidate.id(), candidate.eventType());
            } catch (Exception e) {
                markPublishFailed(candidate.id());
                log.error("[OutboxRelay] 발행 실패. id={}", candidate.id(), e);
            }
        }
    }

    /**
     * 집어서 {@code SENDING} 으로 바꾼다. 트랜잭션은 여기서 끝난다.
     *
     * <p>엔티티를 밖으로 들고 나가지 않고 {@link Candidate} 로 바꿔 나간다.
     * 트랜잭션 밖에서 엔티티를 만지면 지연 로딩과 더티 체킹이 둘 다 뜻을 잃는다.
     */
    private List<Candidate> claim() {
        List<Candidate> claimed = transactionTemplate.execute(status -> {
            LocalDateTime staleThreshold = LocalDateTime.now().minus(SENDING_STALE_THRESHOLD);

            List<OutboxEvent> events = repository.claimCandidates(staleThreshold, BATCH_SIZE);
            events.forEach(OutboxEvent::markSending);

            return events.stream()
                    .map(event -> new Candidate(
                            event.getId(),
                            event.getAggregateType(),
                            event.getEventType(),
                            event.getPartitionKey(),
                            event.getPayload()))
                    .toList();
        });
        return claimed == null ? List.of() : claimed;
    }

    private void markPublished(Long id) {
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(id).ifPresent(OutboxEvent::markPublished));
    }

    private void markPublishFailed(Long id) {
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(id).ifPresent(OutboxEvent::markPublishFailed));
    }

    private record Candidate(Long id,
                             AggregateType type,
                             String eventType,
                             String partitionKey,
                             String payload) {
    }
}
