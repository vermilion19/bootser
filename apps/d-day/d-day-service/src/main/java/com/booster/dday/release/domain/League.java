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

import java.time.ZoneId;

/**
 * 리그 (D-2 · D-5).
 *
 * <p>SPEC §D-5 가 「종목 고르기」를 <b>「리그 켜기」</b>로 바꿨다. 그래서 켜는 리그가
 * 코드가 아니라 <b>자료</b>다 — 1차는 KBO 하나이고, 늘리는 것은 설정 한 줄이다.
 *
 * <p>1차에 KBO 를 고른 근거가 «인기» 가 아니라 <b>«D-4 가 실제로 발동한다»</b> 는
 * 것이었다 (SPEC §12.1). 축구를 켜면 일정 변경이 드물어 <b>변경 감지 → 이벤트 →
 * 알림 사슬이 시즌 내내 한 번도 안 돌 수 있다.</b> 만들어 놓고 검증을 못 하는 것이
 * 비계로서는 최악이다. 야구는 우천 순연이 일상이라 그 길을 여러 번 밟는다.
 */
@Entity
@Table(
        name = "league",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_league_source", columnNames = {"source", "external_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class League extends BaseEntity {

    @Id
    private Long id;

    /** 어느 원천에서 온 리그인가. {@code thesportsdb} */
    @Column(nullable = false, length = 20)
    private String source;

    /** 원천이 준 id. <b>문자열이다</b> — 남의 번호 체계다 */
    @Column(name = "external_id", nullable = false, length = 40)
    private String externalId;

    @Column(nullable = false, length = 40)
    private String sport;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** 경기 시각을 해석하는 시간대. KBO 는 KST 하나다 (SPEC §12.1) */
    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    /** 껐다 켜는 것이 설정이 아니라 자료다 */
    @Column(nullable = false)
    private boolean enabled;

    public static League of(String source, String externalId, String sport, String name,
                            String countryCode, ZoneId zone, boolean enabled) {
        League league = new League();
        league.id = SnowflakeGenerator.nextId();
        league.source = require(source, "원천");
        league.externalId = require(externalId, "원천 id");
        league.sport = require(sport, "종목");
        league.name = require(name, "이름");
        league.countryCode = countryCode;
        league.zoneId = require(zone, "시간대").getId();
        league.enabled = enabled;
        return league;
    }

    public void refresh(String sport, String name, String countryCode, ZoneId zone) {
        this.sport = require(sport, "종목");
        this.name = require(name, "이름");
        this.countryCode = countryCode;
        this.zoneId = require(zone, "시간대").getId();
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    private static <T> T require(T value, String what) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }
}
