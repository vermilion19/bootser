package com.booster.dday.holiday.infrastructure;

import com.booster.dday.holiday.exception.HolidaySourceUnavailableException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Nager.Date v3 (SPEC §11).
 *
 * <h2>4xx 를 회로의 실패로 세지 않는다</h2>
 *
 * <p>ARCHITECTURE §5.5 가 정한 것이다. <b>한 나라가 404 를 주는 것은 원천의 장애가
 * 아니라 그 나라의 사정</b>이고, 그걸로 회로를 열면 나머지 203국이 막힌다. 그래서
 * 4xx 는 빈 목록으로 접어 «그 나라에 자료가 없다» 로 돌려주고, 회로는 5xx ·
 * 타임아웃 · 커넥션 실패만 본다.
 *
 * <p>회로는 <b>원천마다 하나</b>다. 국가별로 만들면 204개 회로가 각자 표본 부족이라
 * <b>영영 안 열린다.</b>
 *
 * <h2>속도 제한을 회로 <b>바깥</b>에 둔다 — 순서가 중요하다</h2>
 *
 * <p>1,020 요청이 한꺼번에 나가면 무료 API 를 우리가 DoS 하는 셈이다 (§5.4-1).
 * 동시성은 가상 스레드가 열고 <b>속도는 RateLimiter 가 잡는다.</b>
 *
 * <p>그런데 <b>RateLimiter 를 회로 안에 두면 함정이 생긴다.</b> 허가를 기다린
 * 시간까지 TimeLimiter 가 세므로, 줄이 길어진 것만으로 타임아웃이 나고 <b>그것이
 * 원천의 실패로 기록된다.</b> 원천은 멀쩡한데 우리 대기줄 때문에 회로가 열린다.
 * 그래서 허가를 먼저 받고, 받은 다음에 회로 안으로 들어간다.
 */
@Slf4j
@Component
public class NagerClient {

    /** 회로 이름. 원천마다 하나다 (§5.5) */
    public static final String CIRCUIT = "nager";

    private static final ParameterizedTypeReference<List<NagerHoliday>> HOLIDAYS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<NagerLongWeekend>> LONG_WEEKENDS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public NagerClient(@Qualifier("nagerRestClient") RestClient restClient,
                       @Qualifier("nagerRateLimiter") RateLimiter rateLimiter,
                       CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT);
    }

    /**
     * 그 나라 그 해의 공휴일 전부. <b>{@code Public} 이 아닌 것도 받는다</b> (A-10).
     *
     * @return 원천이 404 를 주면 빈 목록 — 자료가 없다는 뜻이지 실패가 아니다
     * @throws HolidaySourceUnavailableException 원천이 응답을 못 줬다
     */
    public List<NagerHoliday> holidays(String countryCode, int year) {
        return call("/api/v3/PublicHolidays/{year}/{cc}", year, countryCode, HOLIDAYS);
    }

    public List<NagerLongWeekend> longWeekends(String countryCode, int year) {
        return call("/api/v3/LongWeekend/{year}/{cc}", year, countryCode, LONG_WEEKENDS);
    }

    private <T> List<T> call(String path, int year, String countryCode,
                             ParameterizedTypeReference<List<T>> type) {

        /* 허가를 먼저 받는다. 이 대기는 회로의 시간 예산 밖이다 (위 주석) */
        if (!rateLimiter.acquirePermission()) {
            throw new HolidaySourceUnavailableException(
                    "속도 제한 허가를 못 받았다: " + countryCode + " " + year, null);
        }

        return circuitBreaker.run(
                () -> {
                    List<T> body = restClient.get()
                            .uri(path, year, countryCode)
                            .retrieve()
                            .body(type);
                    /* 본문이 비면 빈 목록으로 접는다. null 을 그대로 올리면 급감 가드가
                       「0건을 받았다」와 「못 받았다」를 구별하지 못한다 */
                    return body == null ? List.<T>of() : body;
                },
                throwable -> fallback(throwable, countryCode, year));
    }

    /**
     * 회로가 잡은 것을 가른다.
     *
     * <p>4xx 는 {@code ignoreExceptions} 라 실패로 기록되지 않았지만 <b>폴백은
     * 그래도 불린다.</b> 여기서 빈 목록으로 접는다 — 그 나라의 사정이다.
     */
    private <T> List<T> fallback(Throwable throwable, String countryCode, int year) {
        if (throwable instanceof HttpClientErrorException clientError) {
            log.debug("[Nager] {} {} → {} — 그 나라의 사정으로 본다",
                    countryCode, year, clientError.getStatusCode());
            return List.of();
        }
        throw new HolidaySourceUnavailableException(
                "원천이 응답을 못 줬다: " + countryCode + " " + year, throwable);
    }
}
