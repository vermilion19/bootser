package com.booster.dday.shared.outbox;

import com.booster.storage.kafka.core.KafkaTopic;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 릴레이. DB 도 Kafka 도 Redis 도 띄우지 않는다.
 *
 * <p>여기서 무는 것은 <b>순서와 경계</b>다 — 락을 못 잡으면 아무것도 안 한다,
 * 잡았으면 무슨 일이 있어도 푼다, 한 건이 실패해도 나머지를 계속 보낸다,
 * 페이로드를 열어 보지 않는다. 전부 실제 브로커 없이 답이 정해지는 것들이고,
 * <b>실제로 틀리면 조용히 틀리는</b> 것들이다.
 */
class OutboxRelayTest {

    private OutboxEventRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private LockProvider lockProvider;
    private SimpleLock lock;

    private OutboxRelay relay;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        lockProvider = mock(LockProvider.class);
        lock = mock(SimpleLock.class);

        when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));
        when(repository.claimCandidates(any(), anyInt())).thenReturn(List.of());
        sendSucceeds();

        relay = new OutboxRelay(repository, kafkaTemplate, lockProvider, immediateTransactions());
    }

    /**
     * 콜백을 그 자리에서 실행하는 트랜잭션 템플릿.
     *
     * <p>릴레이가 {@code TransactionTemplate} 을 쓰는 것 자체가 설계의 일부다 —
     * 2세대는 같은 클래스 안에서 {@code @Transactional} 메서드를 직접 불러
     * 프록시를 안 거쳤고, <b>상태기계가 DB 에 한 글자도 안 쓰였다</b>
     * (ARCHITECTURE §3.1). 그 함정이 있을 자리를 없앤 것이라 여기서는 실행만 시킨다.
     */
    private static TransactionTemplate immediateTransactions() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    private void sendSucceeds() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(sent());
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, String>> sent() {
        return CompletableFuture.completedFuture((SendResult<String, String>) mock(SendResult.class));
    }

    private OutboxEvent event(long id, AggregateType type, String partitionKey, String payload) {
        OutboxEvent event = mock(OutboxEvent.class);
        when(event.getId()).thenReturn(id);
        when(event.getAggregateType()).thenReturn(type);
        when(event.getEventType()).thenReturn("CHANGED");
        when(event.getPartitionKey()).thenReturn(partitionKey);
        when(event.getPayload()).thenReturn(payload);
        when(repository.findById(id)).thenReturn(Optional.of(event));
        return event;
    }

    private void claims(OutboxEvent... events) {
        when(repository.claimCandidates(any(), anyInt())).thenReturn(List.of(events));
    }

    @Nested
    @DisplayName("락 (§5.6)")
    class Locking {

        @Test
        @DisplayName("못 잡으면 DB 도 Kafka 도 건드리지 않는다")
        void doesNothingWithoutLock() {
            when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

            relay.schedule();

            verifyNoInteractions(repository);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("이름 하나로 잡는다 — 스케줄 경로와 수동 경로가 같은 이름을 써야 한다")
        void locksByTheAgreedName() {
            relay.schedule();

            ArgumentCaptor<LockConfiguration> config = ArgumentCaptor.forClass(LockConfiguration.class);
            verify(lockProvider).lock(config.capture());
            assertThat(config.getValue().getName()).isEqualTo("dday-outbox-relay");
        }

        @Test
        @DisplayName("일이 끝나면 푼다")
        void unlocksAfterWork() {
            relay.schedule();

            verify(lock).unlock();
        }

        /**
         * 안 풀면 {@code lockAtMostFor}(30초)가 지날 때까지 릴레이가 통째로 멈춘다.
         * 알림이 30초씩 밀리는데 <b>로그에는 아무 에러도 안 찍힌다.</b>
         */
        @Test
        @DisplayName("중간에 터져도 푼다")
        void unlocksEvenWhenClaimBlowsUp() {
            when(repository.claimCandidates(any(), anyInt()))
                    .thenThrow(new IllegalStateException("DB 가 죽었다"));

            assertThatThrownBy(() -> relay.schedule()).isInstanceOf(IllegalStateException.class);

            verify(lock).unlock();
        }
    }

    @Nested
    @DisplayName("집기")
    class Claiming {

        @Test
        @DisplayName("배치 상한은 100 이다 — 밀린 게 많아도 한 트랜잭션이 길어지지 않는다")
        void batchIsCapped() {
            relay.schedule();

            verify(repository).claimCandidates(any(), eq(OutboxRelay.BATCH_SIZE));
            assertThat(OutboxRelay.BATCH_SIZE).isEqualTo(100);
        }

        @Test
        @DisplayName("stale 기준은 1분 전이다")
        void staleThresholdIsOneMinuteAgo() {
            LocalDateTime before = LocalDateTime.now();

            relay.schedule();

            ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repository).claimCandidates(threshold.capture(), anyInt());

            Duration gap = Duration.between(threshold.getValue(), before);
            assertThat(gap).isBetween(Duration.ofSeconds(59), Duration.ofSeconds(61));
        }

        @Test
        @DisplayName("집은 것은 SENDING 으로 바꾼다 — 안 그러면 다음 회차가 또 집는다")
        void marksClaimedAsSending() {
            OutboxEvent first = event(1L, AggregateType.SPORT_EVENT, "2400325", "{}");
            OutboxEvent second = event(2L, AggregateType.ANNIVERSARY, "77", "{}");
            claims(first, second);

            relay.schedule();

            verify(first).markSending();
            verify(second).markSending();
        }

        @Test
        @DisplayName("집을 게 없으면 Kafka 를 건드리지 않는다")
        void emptyBatchTouchesNothing() {
            relay.schedule();

            verifyNoInteractions(kafkaTemplate);
        }
    }

    @Nested
    @DisplayName("보내기")
    class Publishing {

        /**
         * 릴레이는 페이로드를 <b>열어 보지 않는다.</b> 토픽은 집합체 갈래에서,
         * 파티션 키는 칼럼에서 온다 (SCHEMA §7.1). 페이로드를 파싱해 키를 뽑기
         * 시작하면 그 순간 릴레이가 도메인을 알게 되고, 한 테이블로 간 이유가 사라진다.
         */
        @Test
        @DisplayName("토픽은 집합체 갈래가, 파티션 키는 칼럼이 정한다")
        void routingComesFromColumnsNotPayload() {
            claims(event(1L, AggregateType.SPORT_EVENT, "2400325", "{\"memberId\":999}"));

            relay.schedule();

            verify(kafkaTemplate).send(
                    KafkaTopic.DDAY_RELEASE_CHANGED.getTopic(),
                    "2400325",
                    "{\"memberId\":999}");
        }

        @Test
        @DisplayName("기념일은 알림 토픽으로, memberId 를 키로 간다")
        void anniversaryGoesToNotificationTopic() {
            claims(event(1L, AggregateType.ANNIVERSARY, "77", "{}"));

            relay.schedule();

            verify(kafkaTemplate).send(KafkaTopic.DDAY_NOTIFICATION_REQUESTED.getTopic(), "77", "{}");
        }

        @Test
        @DisplayName("영화도 경기와 같은 사실 토픽을 탄다")
        void movieSharesTheReleaseTopic() {
            claims(event(1L, AggregateType.MOVIE, "m-1", "{}"));

            relay.schedule();

            verify(kafkaTemplate).send(KafkaTopic.DDAY_RELEASE_CHANGED.getTopic(), "m-1", "{}");
        }

        @Test
        @DisplayName("보냈으면 PUBLISHED 로 적는다")
        void marksPublishedOnSuccess() {
            OutboxEvent event = event(1L, AggregateType.SPORT_EVENT, "2400325", "{}");
            claims(event);

            relay.schedule();

            verify(event).markPublished();
        }

        @Test
        @DisplayName("브로커가 안 받으면 실패로 적는다")
        void marksFailedWhenBrokerRefuses() {
            OutboxEvent event = event(1L, AggregateType.SPORT_EVENT, "2400325", "{}");
            claims(event);
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 없음")));

            relay.schedule();

            verify(event).markPublishFailed();
            verify(event, never()).markPublished();
        }

        /**
         * 한 건이 나머지를 막으면 <b>죽은 이벤트 하나가 배치를 통째로 세운다.</b>
         * 그 줄만 재시도 대상으로 남기고 나머지는 흘려보내야 한다.
         */
        @Test
        @DisplayName("한 건이 실패해도 나머지는 계속 보낸다")
        void oneFailureDoesNotStopTheBatch() {
            OutboxEvent bad = event(1L, AggregateType.SPORT_EVENT, "bad", "{}");
            OutboxEvent good = event(2L, AggregateType.SPORT_EVENT, "good", "{}");
            claims(bad, good);

            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 없음")))
                    .thenReturn(sent());

            relay.schedule();

            verify(bad).markPublishFailed();
            verify(good).markPublished();
            verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("상태를 적을 때는 그 id 로 다시 읽는다 — 트랜잭션이 이미 끝났기 때문이다")
        void reloadsBeforeMarking() {
            claims(event(1L, AggregateType.SPORT_EVENT, "2400325", "{}"));

            relay.schedule();

            verify(repository).findById(1L);
        }

        @Test
        @DisplayName("사라진 줄은 조용히 넘어간다 — 보존 청소가 먼저 지웠을 수 있다")
        void missingRowIsNotAnError() {
            claims(event(1L, AggregateType.SPORT_EVENT, "2400325", "{}"));
            when(repository.findById(anyLong())).thenReturn(Optional.empty());

            relay.schedule();

            verify(lock).unlock();
        }
    }
}
