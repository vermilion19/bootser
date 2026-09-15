package com.booster.dday.release.event;

import com.booster.common.JsonUtils;
import com.booster.core.web.event.DDayNotificationEvent;
import com.booster.dday.release.application.WatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 2단 발행의 두 번째 단 (ARCHITECTURE §3.4 「다」).
 *
 * <pre>
 *   1단 · 사실   경기가 연기됐다        → dday.release.changed   (Outbox 릴레이가 낸다)
 *   2단 · 의도   A 씨에게 알려야 한다   → dday.notification.requested  (여기)
 * </pre>
 *
 * <h2>동기화 트랜잭션에서 펼치지 않은 까닭</h2>
 *
 * <p>관심자 수에 비례해 <b>동기화 트랜잭션이 길어진다.</b> 인기 팀 하나가 동기화
 * 전체를 붙들고, 재시도 단위도 통째가 된다. 여기서 펼치면 그 둘이 갈라진다 —
 * 동기화는 {@code Watch} 크기와 무관해지고, 펼치기는 Kafka 컨슈머라 <b>혼자
 * 재시도되고 끝내 실패하면 DLT 로 간다.</b>
 *
 * <h2>팀 관심도 같이 본다</h2>
 *
 * <p>한화 팬은 «2026-09-11 한화 vs NC» 를 등록한 것이 아니라 <b>«한화»</b> 를
 * 등록해 두었다. 경기에 걸린 관심만 보면 그 팬에게 아무것도 안 간다 —
 * {@code ck_watch_subject} 가 {@code TEAM} 을 허용하는 것이 그 뜻이다.
 *
 * <h2>한 사람에게 한 번만 보낸다</h2>
 *
 * <p>경기에도 걸고 두 팀 모두에 걸어 둔 사람이 있을 수 있다. 그대로 펼치면
 * <b>같은 연기 소식을 세 번 받는다.</b> 회원 번호로 접는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dday.fanout.enabled", havingValue = "true", matchIfMissing = true)
public class DateChangeFanoutConsumer {

    private final WatchService watches;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DateChangeFanoutConsumer(WatchService watches,
                                    @Qualifier("stringKafkaTemplate")
                                    KafkaTemplate<String, String> kafkaTemplate) {
        this.watches = watches;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "#{T(com.booster.storage.kafka.core.KafkaTopic).DDAY_RELEASE_CHANGED.getTopic()}",
            groupId = "${dday.fanout.group-id:d-day-fanout}",
            containerFactory = "ddayStringListenerFactory")
    public void onReleaseChanged(String message) {
        ReleaseChangedEvent fact = read(message);

        List<Long> memberIds = watches.receiversOf(
                fact.subjectType(), fact.subjectId(), fact.relatedTeamIds());

        if (memberIds.isEmpty()) {
            log.debug("[Fanout] {} 에 관심을 둔 사람이 없다", fact.externalId());
            return;
        }

        for (Long memberId : memberIds) {
            DDayNotificationEvent request = requestFor(memberId, fact);
            kafkaTemplate.send(
                    com.booster.storage.kafka.core.KafkaTopic.DDAY_NOTIFICATION_REQUESTED.getTopic(),
                    /* 파티션 키가 memberId 다 (§3.5) — 한 회원의 알림이 순서대로 */
                    String.valueOf(memberId),
                    JsonUtils.toJson(request));
        }
        log.info("[Fanout] {} 의 {} 변경을 {}명에게 펼쳤다",
                fact.externalId(), fact.field(), memberIds.size());
    }

    /**
     * 사실 하나를 <b>한 사람에게 보낼 의도</b>로 바꾼다.
     *
     * <p>열거형을 이름으로 풀어 싣는다. {@code libs} 의 본문 타입이 문자열을 받는
     * 까닭은 <b>우리가 값을 하나 더하는 일이 알림 서비스의 배포를 기다리는 일이
     * 되지 않게</b> 하기 위해서다 — 그리고 모르는 값이 와도 컨슈머가 역직렬화에서
     * 죽지 않는다.
     */
    private static DDayNotificationEvent requestFor(Long memberId, ReleaseChangedEvent fact) {
        return new DDayNotificationEvent(
                memberId,
                DDayNotificationEvent.REASON_RELEASE_CHANGED,
                fact.subjectType().name(),
                fact.subjectId(),
                fact.subjectName(),
                fact.field().name(),
                fact.oldValue(),
                fact.newValue(),
                fact.startsAt(),
                fact.detectedAt());
    }

    /**
     * 본문을 푼다.
     *
     * <p>못 푸는 것은 <b>{@link IllegalArgumentException} 으로 올린다</b> — 에러
     * 핸들러가 그것만 재시도 없이 DLT 로 보낸다. 본문이 깨진 것은 1초 뒤에 세 번 더
     * 시도해도 낫지 않고, 그동안 <b>그 파티션의 뒤 메시지가 전부 막힌다.</b>
     */
    private ReleaseChangedEvent read(String message) {
        try {
            ReleaseChangedEvent fact = JsonUtils.fromJson(message, ReleaseChangedEvent.class);
            if (fact == null || fact.subjectId() == null) {
                throw new IllegalArgumentException("대상이 없는 변경 사실: " + message);
            }
            return fact;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("변경 사실을 못 풀었다: " + message, e);
        }
    }
}
