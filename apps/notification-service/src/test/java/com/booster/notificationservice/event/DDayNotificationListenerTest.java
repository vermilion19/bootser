package com.booster.notificationservice.event;

import com.booster.common.JsonUtils;
import com.booster.core.web.event.DDayNotificationEvent;
import com.booster.notificationservice.client.SlackClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * d-day 알림 요청을 받는 자리.
 *
 * <p>여기서 무는 것 둘.
 *
 * <ol>
 *   <li><b>모르는 {@code field} 가 와도 살아남는가.</b> d-day 가 칸을 하나 더하는
 *       일이 이 서비스의 배포를 기다리는 일이 되면 안 되고, 죽으면 그 파티션의
 *       뒤 알림이 전부 막힌다</li>
 *   <li><b>못 푸는 본문을 재시도하지 않는가.</b> {@link IllegalArgumentException}
 *       이어야 에러 핸들러가 바로 격리한다</li>
 * </ol>
 */
class DDayNotificationListenerTest {

    private static final Instant STARTS = Instant.parse("2026-09-11T09:30:00Z");
    private static final Instant DETECTED = Instant.parse("2026-09-11T08:00:00Z");

    private SlackClient slackClient;
    private DDayNotificationListener listener;

    @BeforeEach
    void setUp() {
        slackClient = mock(SlackClient.class);
        listener = new DDayNotificationListener(slackClient);
    }

    private static String json(String field, String oldValue, String newValue) {
        return JsonUtils.toJson(new DDayNotificationEvent(
                7L, DDayNotificationEvent.REASON_RELEASE_CHANGED, "SPORT_EVENT", 100L,
                "Hanwha Eagles vs NC Dinos", field, oldValue, newValue,
                STARTS, "Asia/Seoul", DETECTED));
    }

    private static String anniversary(String zoneId, Instant occursAt, int offset) {
        return JsonUtils.toJson(new DDayNotificationEvent(
                7L, DDayNotificationEvent.REASON_ANNIVERSARY_DUE, "ANNIVERSARY", 100L,
                "생일", "D_DAY", null, String.valueOf(offset),
                occursAt, zoneId, DETECTED));
    }

    private String sentText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(slackClient).sendMessage(text.capture());
        return text.getValue();
    }

    /** KST 18:30 경기다 — UTC 09:30 을 그대로 쓰면 9시간 어긋난다 */
    @Test
    @DisplayName("순연 알림이 이름과 한국 시각으로 나간다")
    void postponementIsReadable() {
        listener.handle(json("POSTPONED", "false", "true"));

        assertThat(sentText())
                .contains("Hanwha Eagles vs NC Dinos")
                .contains("순연됐습니다")
                .contains("9월 11일 18:30");
    }

    @Test
    @DisplayName("순연이 풀린 것도 구별해서 알린다")
    void unpostponementIsDifferent() {
        listener.handle(json("POSTPONED", "true", "false"));

        assertThat(sentText()).contains("순연이 풀렸습니다");
    }

    @Test
    @DisplayName("시각이 옮겨진 것도 알린다")
    void timeShiftIsReadable() {
        listener.handle(json("STARTS_AT", "2026-09-11T09:30:00Z", "2026-09-12T09:30:00Z"));

        assertThat(sentText()).contains("시작 시각이 바뀌었습니다");
    }

    /**
     * <b>이 테스트가 이 파일에서 제일 중요하다.</b> 모르는 값에 예외를 던지면
     * d-day 가 칸을 하나 더하는 것이 이 서비스의 배포를 기다리는 일이 된다.
     */
    @Test
    @DisplayName("모르는 field 가 와도 보낸다 — 그 값을 그대로 싣는다")
    void unknownFieldStillSends() {
        listener.handle(json("VENUE_MOVED", "대전", "청주"));

        assertThat(sentText()).contains("VENUE_MOVED");
    }

    @Test
    @DisplayName("시각을 모르면 시각 없이 보낸다")
    void withoutTimeItStillSends() {
        String noTime = JsonUtils.toJson(new DDayNotificationEvent(
                7L, DDayNotificationEvent.REASON_RELEASE_CHANGED, "SPORT_EVENT", 100L,
                "Hanwha Eagles vs NC Dinos", "POSTPONED", "false", "true",
                null, "Asia/Seoul", DETECTED));

        listener.handle(noTime);

        assertThat(sentText()).contains("순연됐습니다").doesNotContain("9월");
    }

    @Test
    @DisplayName("기념일 알림은 남은 날수로 말한다")
    void anniversaryCountsDown() {
        listener.handle(anniversary("Asia/Seoul",
                Instant.parse("2026-05-20T15:00:00Z"), 7));

        assertThat(sentText()).contains("생일").contains("7일 남았습니다").contains("5월 21일");
    }

    @Test
    @DisplayName("당일이면 「오늘입니다」다")
    void anniversaryToday() {
        listener.handle(anniversary("Asia/Seoul",
                Instant.parse("2026-05-20T15:00:00Z"), 0));

        assertThat(sentText()).contains("오늘입니다").doesNotContain("0일");
    }

    /**
     * <b>보낸 쪽이 알려 준 시간대로 그린다.</b> 우리 시간대(KST)로 그리면
     * 오클랜드 회원의 자정이 <b>전날 21시</b>가 되어 날짜가 하루 이르게 나온다 —
     * d-day 가 고치려고 만들어진 바로 그 고장이다.
     */
    @Test
    @DisplayName("회원의 시간대로 날짜를 그린다 — 우리 시간대가 아니다")
    void rendersInTheSendersZone() {
        /* 오클랜드 2026-05-20 자정 = UTC 2026-05-19 12:00 */
        listener.handle(anniversary("Pacific/Auckland",
                Instant.parse("2026-05-19T12:00:00Z"), 0));

        assertThat(sentText())
                .as("KST 로 그리면 5월 19일이 된다")
                .contains("5월 20일");
    }

    @Test
    @DisplayName("모르는 시간대가 와도 알림은 나간다")
    void unknownZoneStillSends() {
        listener.handle(anniversary("Mars/Olympus", STARTS, 0));

        assertThat(sentText()).contains("생일");
    }

    @Test
    @DisplayName("못 푸는 본문은 IllegalArgumentException 이다 — 재시도하지 않게")
    void unparsableBodyIsNotRetryable() {
        assertThatThrownBy(() -> listener.handle("{이건 JSON 이 아니다"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(slackClient, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("받는 사람이 없는 요청도 재시도하지 않는다")
    void missingMemberIsNotRetryable() {
        String noMember = JsonUtils.toJson(new DDayNotificationEvent(
                null, "RELEASE_CHANGED", "SPORT_EVENT", 100L, "x",
                "POSTPONED", "false", "true", STARTS, "Asia/Seoul", DETECTED));

        assertThatThrownBy(() -> listener.handle(noMember))
                .isInstanceOf(IllegalArgumentException.class);

        verify(slackClient, never()).sendMessage(anyString());
    }
}
