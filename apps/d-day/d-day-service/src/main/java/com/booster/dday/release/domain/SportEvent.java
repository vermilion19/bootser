package com.booster.dday.release.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 경기 하나 (D-2 · D-4).
 *
 * <h2>{@code sourceHash} — 「다시 쓰지 않기」를 한 칼럼으로 만든다</h2>
 *
 * <p>창 누적이 10분마다 도므로 <b>같은 경기를 하루 144번 다시 본다</b> (SPEC §12.2).
 * 안 바뀌었는데 매번 {@code UPDATE} 하면 하루 144번의 쓸모없는 갱신이고,
 * 그것이 그대로 {@code updated_at} 을 흔들어 <b>「언제 바뀌었나」를 못 믿게</b> 만든다.
 *
 * <p>가변 필드의 해시를 들고 있으면 <b>비교 한 번으로 건너뛸 수 있다.</b>
 *
 * <h2>소프트 삭제를 하지 않는다</h2>
 *
 * <p>공휴일과 다른 점이다. 원천이 <b>다음 1건만 주므로 「사라졌다」를 관측할 수 없다</b>
 * (SPEC §12.2). 관측할 수 없는 것을 자료로 표현하면 <b>없는 정보를 있는 척</b>하게 된다.
 *
 * <h2>{@code timeIsUnknown} — 조용히 00:00 으로 퉁치지 않는다</h2>
 *
 * <p>{@code strTime} 이 없을 때가 있다. 그때 자정으로 채우면 <b>「자정에 시작하는
 * 경기」가 되고 D-day 가 하루 어긋날 수 있다.</b> 모르는 것은 모른다고 적는다
 * (SPEC §9.9(4)).
 */
@Entity
@Table(
        name = "sport_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sport_event_source", columnNames = {"source", "external_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SportEvent extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 20)
    private String source;

    /** 원천의 {@code idEvent}. <b>Outbox 의 파티션 키가 이 값이다</b> (§3.5) */
    @Column(name = "external_id", nullable = false, length = 40)
    private String externalId;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "home_team_id")
    private Long homeTeamId;

    @Column(name = "away_team_id")
    private Long awayTeamId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "starts_at")
    private Instant startsAt;

    /** 날짜는 아는데 시각을 모른다. <b>자정으로 퉁치지 않는다</b> */
    @Column(name = "time_is_unknown", nullable = false)
    private boolean timeIsUnknown;

    @Column(length = 20)
    private String status;

    /** 우천 순연. <b>D-4 의 근거다</b> (SPEC §12.1) */
    @Column(nullable = false)
    private boolean postponed;

    @Column(length = 20)
    private String season;

    @Column(length = 200)
    private String venue;

    /** 가변 필드의 md5. 같으면 갱신을 건너뛴다 */
    @Column(name = "source_hash", nullable = false, length = 32)
    private String sourceHash;

    /** 마지막으로 본 시각. <b>「사라졌다」 대신 「오래 못 봤다」로 표현한다</b> */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public static SportEvent of(String source, String externalId, Long leagueId,
                                Long homeTeamId, Long awayTeamId, String name,
                                Instant startsAt, boolean timeIsUnknown, String status,
                                boolean postponed, String season, String venue, Instant seenAt) {

        SportEvent event = new SportEvent();
        event.id = SnowflakeGenerator.nextId();
        event.source = source;
        event.externalId = externalId;
        event.leagueId = leagueId;
        event.apply(homeTeamId, awayTeamId, name, startsAt, timeIsUnknown,
                status, postponed, season, venue, seenAt);
        return event;
    }

    /**
     * 원천이 준 것을 반영한다. <b>바뀐 것이 있으면 무엇이 바뀌었는지 돌려준다.</b>
     *
     * <p>돌려주는 까닭은 {@code DateChange} 와 Outbox 가 <b>같은 트랜잭션</b>에
     * 있어야 하기 때문이다 (ARCHITECTURE §3.5). 부르는 쪽이 그 목록으로 둘을 만든다 —
     * 「무엇이 바뀌었나」를 여기서 판단하고 저기서 다시 판단하면 둘이 갈라진다.
     *
     * @return 바뀐 칸들. 비어 있으면 아무것도 안 바뀌었다는 뜻이고 그때는 <b>쓰지도 않는다</b>
     */
    public List<Change> refresh(Long homeTeamId, Long awayTeamId, String name,
                                Instant startsAt, boolean timeIsUnknown, String status,
                                boolean postponed, String season, String venue, Instant seenAt) {

        String incomingHash = hashOf(startsAt, timeIsUnknown, status, postponed, name, venue);

        /* 안 바뀌었으면 본 시각만 적고 끝낸다. 하루 144번 도는 일이라 이 한 줄이
           갱신 143번을 없앤다 */
        if (incomingHash.equals(this.sourceHash)) {
            this.lastSeenAt = seenAt;
            return List.of();
        }

        List<Change> changes = new ArrayList<>(2);
        if (!java.util.Objects.equals(this.startsAt, startsAt)) {
            changes.add(new Change(ChangedField.STARTS_AT,
                    text(this.startsAt), text(startsAt)));
        }
        if (this.postponed != postponed) {
            changes.add(new Change(ChangedField.POSTPONED,
                    String.valueOf(this.postponed), String.valueOf(postponed)));
        }

        apply(homeTeamId, awayTeamId, name, startsAt, timeIsUnknown,
                status, postponed, season, venue, seenAt);
        return changes;
    }

    private void apply(Long homeTeamId, Long awayTeamId, String name,
                       Instant startsAt, boolean timeIsUnknown, String status,
                       boolean postponed, String season, String venue, Instant seenAt) {

        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.name = requireName(name);
        this.startsAt = startsAt;
        this.timeIsUnknown = timeIsUnknown;
        this.status = status;
        this.postponed = postponed;
        this.season = season;
        this.venue = venue;
        this.lastSeenAt = seenAt;
        this.sourceHash = hashOf(startsAt, timeIsUnknown, status, postponed, name, venue);
    }

    /** 바뀐 칸 하나 */
    public record Change(ChangedField field, String oldValue, String newValue) {
    }

    /**
     * 가변 필드만 넣는다.
     *
     * <p>{@code externalId} 나 {@code leagueId} 를 넣으면 안 된다 — 그것이 바뀌면
     * 같은 경기가 아니다. 그리고 <b>{@code lastSeenAt} 을 넣으면 해시가 매번 달라져</b>
     * 이 장치가 통째로 무의미해진다.
     */
    private static String hashOf(Instant startsAt, boolean timeIsUnknown, String status,
                                 boolean postponed, String name, String venue) {
        String joined = String.join("|",
                text(startsAt), String.valueOf(timeIsUnknown), nullToEmpty(status),
                String.valueOf(postponed), nullToEmpty(name), nullToEmpty(venue));

        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 가 없다", e);
        }
    }

    private static String text(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("경기 이름이 없다");
        }
        return name.length() <= 200 ? name : name.substring(0, 200);
    }
}
