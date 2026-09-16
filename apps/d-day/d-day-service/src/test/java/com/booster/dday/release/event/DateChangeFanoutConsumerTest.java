package com.booster.dday.release.event;

import com.booster.common.JsonUtils;
import com.booster.core.web.event.DDayNotificationEvent;
import com.booster.dday.release.application.WatchService;
import com.booster.dday.release.domain.ChangedField;
import com.booster.dday.release.domain.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 2단 발행의 두 번째 단 (ARCHITECTURE §3.4 「다」).
 *
 * <p>여기서 무는 것 셋.
 *
 * <ol>
 *   <li><b>파티션 키가 {@code memberId} 인가</b> (§3.5). 아니면 한 회원의 알림
 *       순서가 안 지켜진다</li>
 *   <li><b>관심자가 없으면 아무것도 안 보내는가.</b> 사실은 일어났지만 받을 사람이
 *       없는 것이 정상이다</li>
 *   <li><b>못 푸는 본문을 재시도하지 않는가.</b> 재시도해도 안 낫고, 그동안 그
 *       파티션의 뒤 메시지가 전부 막힌다</li>
 * </ol>
 */
class DateChangeFanoutConsumerTest {

    private static final Instant DETECTED = Instant.parse("2026-09-11T08:00:00Z");
    private static final String TOPIC = "dday.notification.requested";

    private WatchService watches;
    private KafkaTemplate<String, String> kafka;
    private DateChangeFanoutConsumer consumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        watches = mock(WatchService.class);
        kafka = mock(KafkaTemplate.class);
        consumer = new DateChangeFanoutConsumer(watches, kafka);
    }

    private static String factJson() {
        return JsonUtils.toJson(new ReleaseChangedEvent(
                SubjectType.SPORT_EVENT, 100L, "2400325", "Hanwha Eagles vs NC Dinos",
                ChangedField.POSTPONED, "false", "true",
                Instant.parse("2026-09-11T09:30:00Z"), "Asia/Seoul", DETECTED,
                List.of(10L, 20L)));
    }

    @Test
    @DisplayName("관심자마다 하나씩, 파티션 키는 회원 번호다")
    void fansOutKeyedByMember() {
        when(watches.receiversOf(SubjectType.SPORT_EVENT, 100L, List.of(10L, 20L)))
                .thenReturn(List.of(7L, 8L));

        consumer.onReleaseChanged(factJson());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(kafka, org.mockito.Mockito.times(2))
                .send(org.mockito.ArgumentMatchers.eq(TOPIC), keys.capture(), bodies.capture());

        assertThat(keys.getAllValues()).containsExactly("7", "8");

        DDayNotificationEvent first = JsonUtils.fromJson(
                bodies.getAllValues().get(0), DDayNotificationEvent.class);
        assertThat(first.memberId()).isEqualTo(7L);
        assertThat(first.reason()).isEqualTo("RELEASE_CHANGED");
        /* 열거형이 아니라 이름이다 — 우리가 값을 더하는 일이 알림 서비스의
           배포를 기다리는 일이 되지 않게 */
        assertThat(first.field()).isEqualTo(ChangedField.POSTPONED.name());
        assertThat(first.subjectType()).isEqualTo(SubjectType.SPORT_EVENT.name());
        assertThat(first.subjectName()).isEqualTo("Hanwha Eagles vs NC Dinos");
        assertThat(first.occursAt()).isEqualTo(Instant.parse("2026-09-11T09:30:00Z"));
        /* 받는 쪽이 자기 시간대로 그리면 날짜가 하루 어긋날 수 있다 — 리그가 정한다 */
        assertThat(first.zoneId()).isEqualTo("Asia/Seoul");
        assertThat(first.detectedAt()).isEqualTo(DETECTED);
    }

    @Test
    @DisplayName("팀 id 를 그대로 물어본다 — 경기를 다시 조회하지 않는다")
    void asksWithTeamIdsFromThePayload() {
        when(watches.receiversOf(any(), any(), any())).thenReturn(List.of());

        consumer.onReleaseChanged(factJson());

        verify(watches).receiversOf(SubjectType.SPORT_EVENT, 100L, List.of(10L, 20L));
    }

    @Test
    @DisplayName("관심자가 없으면 아무것도 안 보낸다")
    void sendsNothingWithoutWatchers() {
        when(watches.receiversOf(any(), any(), any())).thenReturn(List.of());

        consumer.onReleaseChanged(factJson());

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    /**
     * 본문이 깨진 것은 1초 뒤에 세 번 더 시도해도 낫지 않는다.
     * {@link IllegalArgumentException} 이라 에러 핸들러가 바로 DLT 로 보낸다.
     */
    @Test
    @DisplayName("못 푸는 본문은 IllegalArgumentException 이다 — 재시도하지 않게")
    void unparsableBodyIsNotRetryable() {
        assertThatThrownBy(() -> consumer.onReleaseChanged("{이건 JSON 이 아니다"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(watches);
    }

    @Test
    @DisplayName("대상이 없는 사실도 재시도하지 않는다")
    void missingSubjectIsNotRetryable() {
        String noSubject = JsonUtils.toJson(new ReleaseChangedEvent(
                SubjectType.SPORT_EVENT, null, "2400325", "x",
                ChangedField.POSTPONED, "false", "true", null, "Asia/Seoul", DETECTED,
                List.of()));

        assertThatThrownBy(() -> consumer.onReleaseChanged(noSubject))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(watches);
    }
}
