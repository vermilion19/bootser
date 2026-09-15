package com.booster.dday.release.infrastructure;

import com.booster.dday.config.SportsSyncProperties;
import com.booster.dday.release.exception.SportSourceUnavailableException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

/**
 * TheSportsDB v1 (SPEC §12).
 *
 * <h2>id 로 팀 목록을 받는 길을 이 클래스가 막는다</h2>
 *
 * <p>SPEC §12.3 이 실측했다. {@code lookup_all_teams.php?id=} 는 무료 키에서
 * <b>id 를 무시하고 언제나 잉글랜드 3부 24팀</b>을 준다. 야구 리그를 물었는데 축구팀이
 * 오고 <b>HTTP 200 이고 모양도 정상이다.</b>
 *
 * <p>그래서 그 엔드포인트를 부르는 메서드를 <b>두지 않는다.</b> 「쓰지 마라」를 문서에만
 * 적으면 언젠가 누가 쓰고, 그 코드는 잘 도는 것처럼 보인다. 부를 수 없게 만드는 편이
 * 낫다.
 *
 * <h2>무료 키는 시즌 전수를 안 준다</h2>
 *
 * <p>{@code eventsseason.php} 가 5건만 온다 (§12.2). 그래서 무료에서는
 * {@link #nextEvents}·{@link #pastEvents} 를 주기적으로 긁어 쌓는 <b>창 누적</b>으로
 * 가고, 유료 키가 있을 때만 {@link #seasonEvents} 를 쓴다. 어느 길로 갈지는
 * {@code SportEventSyncTask} 가 설정을 보고 정한다.
 *
 * <h2>4xx 를 가른다 — 401 은 빈 목록이 아니다</h2>
 *
 * <p>공휴일 쪽은 4xx 를 전부 빈 목록으로 접었다. 여기서는 <b>401·403 만 예외로
 * 올린다.</b> 그것은 「그 리그에 경기가 없다」가 아니라 「우리 키가 틀렸다」이고,
 * 빈 목록으로 접으면 <b>아무도 키를 고치지 않는다.</b>
 */
@Slf4j
@Component
public class SportsDbClient {

    /** 회로 이름. 원천마다 하나다 (§5.5) */
    public static final String CIRCUIT = "thesportsdb";

    private final RestClient restClient;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final String apiKey;

    public SportsDbClient(@Qualifier("sportsDbRestClient") RestClient restClient,
                          @Qualifier("sportsDbRateLimiter") RateLimiter rateLimiter,
                          CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                          SportsSyncProperties properties) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT);
        this.apiKey = properties.apiKey();
    }

    /** 다음 경기들. <b>무료 키에서는 1건이다</b> (§12.2) */
    public List<SportsDbEvent> nextEvents(String leagueExternalId) {
        return events("next " + leagueExternalId, () -> restClient.get()
                .uri("/api/v1/json/{key}/eventsnextleague.php?id={id}", apiKey, leagueExternalId)
                .retrieve()
                .body(SportsDbEvents.class));
    }

    /** 지난 경기들. 창 누적의 나머지 반쪽 */
    public List<SportsDbEvent> pastEvents(String leagueExternalId) {
        return events("past " + leagueExternalId, () -> restClient.get()
                .uri("/api/v1/json/{key}/eventspastleague.php?id={id}", apiKey, leagueExternalId)
                .retrieve()
                .body(SportsDbEvents.class));
    }

    /**
     * 시즌 전수. <b>유료 키가 있을 때만 뜻이 있다.</b>
     *
     * <p>무료 키로 부르면 5건이 온다 — 에러가 아니라서 «시즌에 5경기» 로 담긴다.
     */
    public List<SportsDbEvent> seasonEvents(String leagueExternalId, String season) {
        return events("season " + leagueExternalId + " " + season, () -> restClient.get()
                .uri("/api/v1/json/{key}/eventsseason.php?id={id}&s={season}",
                        apiKey, leagueExternalId, season)
                .retrieve()
                .body(SportsDbEvents.class));
    }

    /**
     * 팀 목록 — <b>리그 이름으로만 받는다</b> (§12.3).
     *
     * @param leagueName {@code Korean KBO League}. id 가 아니다
     */
    public List<SportsDbTeam> teamsOf(String leagueName) {
        SportsDbTeams body = call("teams " + leagueName, () -> restClient.get()
                .uri("/api/v1/json/{key}/search_all_teams.php?l={league}", apiKey, leagueName)
                .retrieve()
                .body(SportsDbTeams.class));

        return body == null ? List.of() : body.orEmpty();
    }

    private List<SportsDbEvent> events(String what, Supplier<SportsDbEvents> fetch) {
        SportsDbEvents body = call(what, fetch);
        return body == null ? List.of() : body.orEmpty();
    }

    private <T> T call(String what, Supplier<T> fetch) {
        if (!rateLimiter.acquirePermission()) {
            throw new SportSourceUnavailableException(
                    "속도 제한 허가를 못 받았다: " + what, null);
        }
        return circuitBreaker.run(fetch, throwable -> fallback(throwable, what));
    }

    private <T> T fallback(Throwable throwable, String what) {
        if (throwable instanceof HttpClientErrorException clientError) {
            HttpStatusCode status = clientError.getStatusCode();

            /* 키가 틀린 것을 «경기가 없다» 로 접으면 아무도 키를 고치지 않는다 */
            if (status.value() == 401 || status.value() == 403) {
                throw new SportSourceUnavailableException(
                        "원천이 우리 키를 거부했다 (" + status + "): " + what, throwable);
            }
            log.debug("[SportsDb] {} → {} — 그 리그의 사정으로 본다", what, status);
            return null;
        }
        throw new SportSourceUnavailableException(
                "원천이 응답을 못 줬다: " + what, throwable);
    }
}
