package com.booster.notificationservice.event;

import com.booster.common.JsonUtils;
import com.booster.core.web.event.DDayNotificationEvent;
import com.booster.notificationservice.client.SlackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * d-day 의 알림 요청을 받는다 — {@code dday.notification.requested}.
 *
 * <p>d-day 의 2단 발행에서 두 번째 단이 낸 것이 여기 온다
 * (apps/d-day/docs/ARCHITECTURE.md §3.4). 1단은 «경기가 연기됐다» 라는 사실이고
 * 이것은 «A 씨에게 알려야 한다» 라는 의도다. <b>수신자를 고르는 일은 d-day 가 이미
 * 했다</b> — 관심 등록({@code watch})은 d-day 소유라 우리가 그 표를 보지 않는다.
 *
 * <h2>문자열로 받는다 — 배치도 아니다</h2>
 *
 * <p>{@code WaitingEvent} 쪽은 배치({@code List<WaitingEvent>})로 받는다. 이쪽은
 * 다르다.
 *
 * <ul>
 *   <li><b>본문에 타입 헤더가 없다.</b> d-day 의 Outbox 릴레이는 페이로드를 열어
 *       보지 않기로 했으므로 그것이 무슨 타입인지 모르고, 모르는 것을 헤더에 적을
 *       수 없다 — 그래서 문자열로 받아 우리가 푼다</li>
 *   <li><b>건수가 적다.</b> 우천 순연 한 건에 관심자 수십이고 배치로 묶을 이득이
 *       없다. 대신 <b>한 건이 실패해도 나머지가 나간다</b></li>
 * </ul>
 *
 * <h2>DB 에 안 적는다 — 아직 적을 표가 없다</h2>
 *
 * <p>{@code notification} 표는 웨이팅 모양이다({@code waiting_id} ·
 * {@code restaurant_id} 가 필수). d-day 알림을 거기 적으려면 <b>그 표를 고치는
 * 결정</b>이 필요하고, 그것은 이 서비스의 스키마 결정이지 d-day 의 결정이 아니다.
 *
 * <p>그래서 지금은 <b>채널로 보내고 로그만 남긴다.</b> 「보냈는지 나중에 확인할 수
 * 없다」가 그 대가이고, 그것을 여기 적어 둔다 — 안 적으면 표를 고칠 때 이 자리가
 * 안 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DDayNotificationListener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일");

    /** 보낸 쪽이 시간대를 안 실어 줬을 때. 1차의 자료가 전부 한국이라 이 값이다 */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Seoul");

    private final SlackClient slackClient;

    @KafkaListener(
            topics = "#{T(com.booster.storage.kafka.core.KafkaTopic).DDAY_NOTIFICATION_REQUESTED.getTopic()}",
            groupId = "notification-service-dday-group",
            containerFactory = "ddayStringListenerFactory")
    public void handle(String message) {
        DDayNotificationEvent event = read(message);

        log.info("[d-day] 회원 {} 에게 {} — {} {} → {}", event.memberId(), event.reason(),
                event.subjectName(), event.oldValue(), event.newValue());

        slackClient.sendMessage(textOf(event));
    }

    /**
     * 사람이 읽을 한 줄.
     *
     * <p><b>왜 보내는지({@code reason})로 먼저 가른다.</b> 일정이 바뀐 것과 기념일이
     * 다가온 것은 문장이 다르다 — {@code field} 만 보면 기념일의 「D_DAY」가
     * 「일정이 바뀌었습니다 (D_DAY)」로 나간다.
     *
     * <p>모르는 값은 <b>그대로 싣는다.</b> 모르는 값에 예외를 던지면 d-day 가 칸을
     * 하나 더하는 일이 <b>이 서비스의 배포를 기다리는 일</b>이 되고, 그동안 그
     * 파티션의 뒤 알림이 전부 막힌다.
     */
    private static String textOf(DDayNotificationEvent event) {
        if (DDayNotificationEvent.REASON_ANNIVERSARY_DUE.equals(event.reason())) {
            return "[D-day] " + event.subjectName() + " " + daysLeft(event)
                    + on(event, DAY);
        }

        String what = switch (event.field() == null ? "" : event.field()) {
            case "POSTPONED" -> "true".equalsIgnoreCase(event.newValue())
                    ? "순연됐습니다" : "순연이 풀렸습니다";
            case "STARTS_AT" -> "시작 시각이 바뀌었습니다";
            case "RELEASE_DATE" -> "개봉일이 바뀌었습니다";
            default -> "일정이 바뀌었습니다 (" + event.field() + ")";
        };
        return "[D-day] " + event.subjectName() + " " + what + on(event, WHEN);
    }

    /**
     * 「7일 남았습니다」 · 「오늘입니다」.
     *
     * <p>기념일 알림의 {@code newValue} 가 <b>며칠 전에 보내는지</b>다 (C-10).
     * 못 읽으면 날수를 빼고 보낸다 — 알림을 통째로 버리는 것보다 낫다.
     */
    private static String daysLeft(DDayNotificationEvent event) {
        try {
            int offset = Integer.parseInt(event.newValue());
            return offset == 0 ? "오늘입니다" : offset + "일 남았습니다";
        } catch (NumberFormatException | NullPointerException e) {
            return "다가옵니다";
        }
    }

    /**
     * 「언제」를 <b>보낸 쪽이 알려 준 시간대로</b> 그린다.
     *
     * <p>우리 시간대로 그리면 안 된다. 기념일은 그 회원이 고른 시간대의 자정이고,
     * 경기는 그 리그의 시간대다 — UTC+13 회원의 기념일 자정을 KST 로 그리면
     * <b>날짜가 하루 이르게 나온다.</b> 그것이 d-day 가 고치려고 만들어진 고장이다.
     */
    private static String on(DDayNotificationEvent event, DateTimeFormatter formatter) {
        if (event.occursAt() == null) {
            return "";
        }
        return " · " + formatter.withZone(zoneOf(event)).format(event.occursAt());
    }

    private static ZoneId zoneOf(DDayNotificationEvent event) {
        if (event.zoneId() == null || event.zoneId().isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(event.zoneId());
        } catch (RuntimeException e) {
            /* 모르는 시간대 이름이 와도 알림은 나가야 한다 */
            log.warn("[d-day] 모르는 시간대: {} — 기본값으로 그린다", event.zoneId());
            return FALLBACK_ZONE;
        }
    }

    /**
     * 본문을 푼다.
     *
     * <p>못 푸는 것은 {@link IllegalArgumentException} 으로 올린다 —
     * {@code KafkaRetryConfig} 가 그것만 재시도 없이 DLT 로 보내게 해 두면, 깨진
     * 본문 하나가 <b>1초 간격으로 세 번 더 죽으며 그 파티션을 붙드는 일</b>이 없다.
     */
    private static DDayNotificationEvent read(String message) {
        try {
            DDayNotificationEvent event =
                    JsonUtils.fromJson(message, DDayNotificationEvent.class);

            if (event == null || event.memberId() == null) {
                throw new IllegalArgumentException("받는 사람이 없는 알림 요청: " + message);
            }
            return event;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("알림 요청을 못 풀었다: " + message, e);
        }
    }
}
