package com.booster.dday.shared.outbox;

import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox 가 DB 와 만나는 자리. H2 를 PostgreSQL 모드로 띄운다.
 *
 * <p>여기서 무는 것 둘이 이 구조의 급소다.
 *
 * <ol>
 *   <li><b>중복 {@code append} 가 트랜잭션을 더럽히지 않는가</b> (SCHEMA §7.3).
 *       창 누적은 같은 경기를 하루 144번 다시 보므로 중복이 정상 경로인데, 그것이
 *       예외로 튀면 같은 트랜잭션의 도메인 변경이 통째로 날아간다.</li>
 *   <li><b>claim 질의가 무엇을 집고 무엇을 안 집는가</b> (SCHEMA §7.2).
 *       잘못 집으면 같은 이벤트를 두 번 보내거나, 죽은 줄이 배치를 영원히 차지한다.</li>
 * </ol>
 *
 * <p>Docker 없이 돈다. Testcontainers PostgreSQL 이 필요한 것 — 부분 인덱스를
 * <b>실제로 타는지</b> — 은 {@code SchemaIndexTest} 쪽 몫이다. 여기서는 <b>뜻</b>이
 * 맞는지만 묻는다.
 *
 * <h2>{@code out} 프로필을 쓰지 않는다</h2>
 *
 * <p>두 가지에 걸린다. (1) 그 프로필은 Loki 부가 기능을 켜는데(core-observability 의
 * {@code logback-spring.xml}) 로컬에 Loki 가 없어서, <b>슬라이스가 둘이 되는 순간
 * 두 번째 스프링 컨텍스트가 Logback 설정 오류로 통째로 못 뜬다.</b>
 * (2) 그 프로필의 인메모리 DB 이름이 하나라 슬라이스끼리 서로의 표를 지운다.
 *
 * <p>그래서 이 테스트가 <b>필요한 것만 스스로 적는다.</b> DB 이름도 클래스마다 다르다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-outbox;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({JpaDomainOutbox.class, JpaConfig.class})
class OutboxPersistenceTest {

    @Autowired
    private DomainOutbox outbox;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    /**
     * 트랜잭션이 「이미 못 쓰게 됐는가」를 본다.
     *
     * <p>유일 제약 위반이 예외로 튀면 Hibernate 가 그 자리에서 트랜잭션을
     * rollback-only 로 표시한다. 그 뒤로는 무엇을 더 써도 커밋 때 통째로 날아가고,
     * <b>날아가는 것은 커밋 시점이라 예외는 훨씬 나중에 엉뚱한 자리에서 보인다.</b>
     * 그래서 결과가 아니라 이 깃발을 직접 본다.
     */
    private boolean transactionIsPoisoned() {
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        return holder != null && holder.getEntityManager().getTransaction().getRollbackOnly();
    }

    private record Payload(String what, int howMany) {
    }

    /** {@code created_at} · {@code updated_at} · {@code status} 를 손으로 정한다 */
    private void forceRow(long id, OutboxStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        em.createNativeQuery("""
                        update outbox_event
                           set status = :status, created_at = :createdAt, updated_at = :updatedAt
                         where id = :id
                        """)
                .setParameter("status", status.name())
                .setParameter("createdAt", createdAt)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private long appendOne(String idempotencyKey) {
        assertThat(outbox.append(AggregateType.SPORT_EVENT, "2400325", "CHANGED",
                "2400325", new Payload("연기", 1), idempotencyKey)).isTrue();
        em.flush();
        em.clear();
        return repository.findAll().stream()
                .filter(e -> e.getIdempotencyKey().equals(idempotencyKey))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Nested
    @DisplayName("적기")
    class Append {

        @Test
        @DisplayName("한 줄이 PENDING 으로 적힌다")
        void writesOnePendingRow() {
            outbox.append(AggregateType.SPORT_EVENT, "2400325", "CHANGED",
                    "2400325", new Payload("연기", 1), "2400325:2026-04-01:2026-04-02");
            em.flush();
            em.clear();

            List<OutboxEvent> all = repository.findAll();
            assertThat(all).hasSize(1);

            OutboxEvent event = all.get(0);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getRetryCount()).isZero();
            assertThat(event.getPublishedAt()).isNull();
            assertThat(event.getAggregateType()).isEqualTo(AggregateType.SPORT_EVENT);
            assertThat(event.getPartitionKey()).isEqualTo("2400325");
            assertThat(event.getPayload()).contains("\"howMany\":1");
        }

        /**
         * 네이티브 질의는 JPA 감사를 안 거친다. 두 칼럼 다 {@code NOT NULL} 이라
         * 안 채우면 {@code INSERT} 자체가 실패하는데, <b>그 실패는 이벤트가 필요한
         * 바로 그 트랜잭션에서 난다.</b>
         */
        @Test
        @DisplayName("감사 칼럼이 채워진다 — 네이티브 질의라 손으로 넣는다")
        void auditColumnsAreFilled() {
            appendOne("k-1");

            OutboxEvent event = repository.findAll().get(0);
            assertThat(event.getCreatedAt()).isNotNull();
            assertThat(event.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("같은 멱등키로 다시 적으면 아무 일도 안 일어난다")
        void duplicateIsANoOp() {
            outbox.append(AggregateType.SPORT_EVENT, "2400325", "CHANGED",
                    "2400325", new Payload("연기", 1), "same-key");

            boolean second = outbox.append(AggregateType.SPORT_EVENT, "2400325", "CHANGED",
                    "2400325", new Payload("연기", 2), "same-key");
            em.flush();
            em.clear();

            assertThat(second).isFalse();
            assertThat(repository.findAll()).hasSize(1);
            assertThat(repository.findAll().get(0).getPayload())
                    .as("먼저 적힌 것이 이긴다. 뒤엣것이 덮어쓰지 않는다")
                    .contains("\"howMany\":1");
        }

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b>
         *
         * <p>JPA 로 {@code save()} 했다면 여기서 유일 제약 위반이 튀고 <b>트랜잭션이
         * rollback-only 로 표시된다.</b> 그러면 같은 트랜잭션에 있던
         * {@code SportEvent} 갱신과 {@code DateChange} 저장까지 통째로 날아간다 —
         * 멱등을 지키려던 방어가 멱등이 지키려던 트랜잭션을 부순다 (SCHEMA §7.3).
         *
         * <p>{@code ARCHITECTURE §3.5} 가 "{@code DateChange} 와 Outbox 가 같은
         * 트랜잭션에 있는 것이 D-4 의 전부" 라고 못 박은 그 트랜잭션이다.
         */
        @Test
        @DisplayName("중복이어도 트랜잭션이 더럽혀지지 않는다 — 같은 트랜잭션의 다른 쓰기가 살아남는다")
        void duplicateDoesNotPoisonTheTransaction() {
            outbox.append(AggregateType.SPORT_EVENT, "2400325", "CHANGED",
                    "2400325", new Payload("연기", 1), "same-key");

            outbox.append(AggregateType.SPORT_EVENT, "2400325", "CHANGED",
                    "2400325", new Payload("연기", 2), "same-key");   // 중복

            assertThat(transactionIsPoisoned())
                    .as("중복 하나가 트랜잭션을 못 쓰게 만들면 안 된다")
                    .isFalse();

            boolean third = outbox.append(AggregateType.SPORT_EVENT, "2400326", "CHANGED",
                    "2400326", new Payload("취소", 1), "other-key");
            em.flush();
            em.clear();

            assertThat(third).isTrue();
            assertThat(repository.findAll()).hasSize(2);
        }

        /**
         * 유일 인덱스가 {@code (aggregate_type, idempotency_key)} 인 까닭이다.
         * 두 도메인이 같은 모양의 키를 만들 수 있다 — {@code "123:2027-03-15"}.
         * 한 표를 공유하는 대가다 (SCHEMA §7.3).
         */
        @Test
        @DisplayName("갈래가 다르면 같은 멱등키라도 둘 다 적힌다")
        void sameKeyDifferentAggregateTypeBothLand() {
            outbox.append(AggregateType.SPORT_EVENT, "123", "CHANGED",
                    "123", new Payload("연기", 1), "123:2027-03-15");
            outbox.append(AggregateType.ANNIVERSARY, "123", "NOTIFY",
                    "777", new Payload("알림", 1), "123:2027-03-15");
            em.flush();
            em.clear();

            assertThat(repository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("빠진 인자로는 적을 수 없다")
        void refusesIncompleteArguments() {
            assertThatThrownBy(() -> outbox.append(null, "1", "CHANGED", "1", new Payload("x", 1), "k"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> outbox.append(AggregateType.MOVIE, " ", "CHANGED", "1", new Payload("x", 1), "k"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> outbox.append(AggregateType.MOVIE, "1", "CHANGED", "1", new Payload("x", 1), ""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> outbox.append(AggregateType.MOVIE, "1", "CHANGED", "1", null, "k"))
                    .as("페이로드 없이 적으면 소비 쪽이 빈 사실을 받는다")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("집기 질의 (§7.2)")
    class Claim {

        @Test
        @DisplayName("PENDING 을 집는다")
        void picksPending() {
            appendOne("k-1");

            assertThat(repository.claimCandidates(LocalDateTime.now().minusMinutes(1), 100))
                    .hasSize(1);
        }

        @Test
        @DisplayName("PUBLISHED 와 FAILED 는 안 집는다")
        void ignoresTerminalRows() {
            long published = appendOne("k-1");
            long failed = appendOne("k-2");
            LocalDateTime now = LocalDateTime.now();
            forceRow(published, OutboxStatus.PUBLISHED, now, now);
            forceRow(failed, OutboxStatus.FAILED, now, now);

            assertThat(repository.claimCandidates(now.minusMinutes(1), 100)).isEmpty();
        }

        /**
         * 다른 인스턴스가 지금 보내는 중일 수 있다. 1분도 안 지났는데 다시 집으면
         * 같은 이벤트가 두 번 나간다.
         */
        @Test
        @DisplayName("방금 SENDING 이 된 것은 안 집는다")
        void leavesFreshSendingAlone() {
            long id = appendOne("k-1");
            LocalDateTime now = LocalDateTime.now();
            forceRow(id, OutboxStatus.SENDING, now.minusSeconds(5), now.minusSeconds(5));

            assertThat(repository.claimCandidates(now.minusMinutes(1), 100)).isEmpty();
        }

        /**
         * 이것이 없으면 <b>보내다 죽은 줄이 영원히 SENDING 으로 남는다.</b>
         * 에러도 안 나고 그 이벤트만 조용히 사라진다.
         */
        @Test
        @DisplayName("1분 넘게 SENDING 인 것은 다시 집는다")
        void reclaimsStaleSending() {
            long id = appendOne("k-1");
            LocalDateTime now = LocalDateTime.now();
            forceRow(id, OutboxStatus.SENDING, now.minusMinutes(10), now.minusMinutes(10));

            assertThat(repository.claimCandidates(now.minusMinutes(1), 100)).hasSize(1);
        }

        /**
         * 적체돼도 <b>오래된 것부터 정해진 수만</b> 집는다. 순서가 뒤집히면 늦게
         * 들어온 이벤트가 먼저 나가고, 같은 경기의 변경 순서가 깨진다 (§3.5).
         */
        @Test
        @DisplayName("오래된 것부터, 상한만큼만 집는다")
        void oldestFirstAndCapped() {
            LocalDateTime base = LocalDateTime.now().minusHours(1);
            long newest = appendOne("k-newest");
            long oldest = appendOne("k-oldest");
            long middle = appendOne("k-middle");
            forceRow(newest, OutboxStatus.PENDING, base.plusMinutes(30), base.plusMinutes(30));
            forceRow(oldest, OutboxStatus.PENDING, base, base);
            forceRow(middle, OutboxStatus.PENDING, base.plusMinutes(10), base.plusMinutes(10));

            List<OutboxEvent> two = repository.claimCandidates(LocalDateTime.now().minusMinutes(1), 2);

            assertThat(two).extracting(OutboxEvent::getId).containsExactly(oldest, middle);
        }
    }

    @Nested
    @DisplayName("상태기계")
    class StateMachine {

        @Test
        @DisplayName("보내는 중 → 발행됨")
        void sendingThenPublished() {
            long id = appendOne("k-1");
            OutboxEvent event = repository.findById(id).orElseThrow();

            event.markSending();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENDING);

            event.markPublished();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(event.getPublishedAt()).isNotNull();
        }

        /**
         * 무한히 되돌리면 <b>죽은 이벤트 하나가 배치 100칸 중 한 칸을 영원히
         * 차지한다.</b> 세 번에서 굳히고 사람을 부른다.
         */
        @Test
        @DisplayName("두 번까지는 다시 집게 두고, 세 번째에 FAILED 로 굳는다")
        void failsAfterThreeAttempts() {
            long id = appendOne("k-1");
            OutboxEvent event = repository.findById(id).orElseThrow();

            event.markPublishFailed();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getRetryCount()).isEqualTo((short) 1);

            event.markPublishFailed();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

            event.markPublishFailed();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(event.getRetryCount()).isEqualTo((short) 3);
        }

        @Test
        @DisplayName("다시 태우는 것은 FAILED 만 — 보내는 중인 것을 되돌리면 두 번 나간다")
        void retryOnlyFromFailed() {
            long id = appendOne("k-1");
            OutboxEvent event = repository.findById(id).orElseThrow();

            assertThatThrownBy(event::retry).isInstanceOf(IllegalStateException.class);

            event.markPublishFailed();
            event.markPublishFailed();
            event.markPublishFailed();
            event.retry();

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("상태 변경이 실제로 DB 에 쓰인다 — 2세대가 깨져 있던 자리다")
        void transitionsReachTheDatabase() {
            long id = appendOne("k-1");

            repository.findById(id).orElseThrow().markSending();
            em.flush();
            em.clear();

            assertThat(repository.findById(id).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.SENDING);
        }
    }
}
