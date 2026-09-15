package com.booster.dday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * 경기 일정 동기화 설정 (SPEC §12).
 *
 * <h2>켜는 리그가 설정이다 — 코드에 박히지 않는다</h2>
 *
 * <p>SPEC §12.1 이 1차 리그로 KBO(id 4830)를 골랐지만 <b>그 선택이 코드에 없다.</b>
 * 여기에 한 줄 더하면 리그가 늘고, 빼면 줄어든다. 고른 근거(「D-4 가 실제로
 * 발동한다」)는 그 선택을 설명할 뿐 그 선택을 고정하지 않는다.
 *
 * <h2>키가 없으면 <b>아예 안 돈다</b></h2>
 *
 * <p>{@code apiKey} 가 비면 {@code enabled()} 가 거짓이고, 동기화는 시작하기 전에
 * 끝난다. 키 없이 돌리면 원천이 401 을 주고 <b>그것이 회차마다 실패로 기록되어</b>
 * 실패 기록이 「원천이 이상하다」를 뜻하지 않게 된다 — 켤 수 없는 것이 꺼져 있는
 * 것으로 보여야 한다.
 *
 * <h2>무료 키와 유료 키가 가는 길이 다르다</h2>
 *
 * <p>SPEC §12.2 가 실측했다. 무료 키는 시즌 질의에 <b>5건만</b> 준다 — 시즌 전수가
 * 아니다. 그래서 무료에서는 「다음 · 지난 경기를 주기적으로 긁어 쌓는」
 * <b>창 누적</b>으로 가고, {@code bulk} 를 켜면 시즌 질의로 간다.
 *
 * @param apiKey 비어 있으면 이 기능이 꺼진다. 공개 저장소에 적지 않는다
 * @param bulk   유료 키가 있어 시즌 전수를 받을 수 있나
 */
@ConfigurationProperties(prefix = "dday.sync.sports")
public record SportsSyncProperties(
        String baseUrl,
        String apiKey,
        boolean bulk,
        List<LeagueSpec> leagues,
        RateLimit rateLimit,
        Duration connectTimeout,
        Duration readTimeout
) {

    /**
     * 켤 리그 하나.
     *
     * @param externalId 원천의 리그 id. KBO 는 {@code 4830}
     * @param name       <b>팀 목록을 이 이름으로 받는다</b> — id 로 받는 길은 못 쓴다 (§12.3)
     * @param zone       경기 시각을 해석하는 시간대. KBO 는 {@code Asia/Seoul}
     * @param season     시즌 질의에 쓸 값. 야구는 {@code 2026}, 유럽 축구는 {@code 2025-2026}
     */
    public record LeagueSpec(
            String externalId,
            String sport,
            String name,
            String countryCode,
            ZoneId zone,
            String season
    ) {
        public LeagueSpec {
            zone = zone == null ? ZoneId.of("Asia/Seoul") : zone;
        }
    }

    public record RateLimit(int permitsPerSecond, Duration waitTimeout) {
    }

    public SportsSyncProperties {
        baseUrl = baseUrl == null ? "https://www.thesportsdb.com" : baseUrl;
        apiKey = apiKey == null ? "" : apiKey.trim();
        leagues = leagues == null ? List.of() : List.copyOf(leagues);
        rateLimit = rateLimit == null
                ? new RateLimit(2, Duration.ofMinutes(5)) : rateLimit;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
    }

    /** 키도 리그도 있어야 돈다 */
    public boolean enabled() {
        return !apiKey.isBlank() && !leagues.isEmpty();
    }
}
