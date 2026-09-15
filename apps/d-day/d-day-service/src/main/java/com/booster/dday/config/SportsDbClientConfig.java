package com.booster.dday.config;

import com.booster.dday.release.infrastructure.SportsDbClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * TheSportsDB 호출에 필요한 것들.
 *
 * <h2>공휴일 쪽보다 훨씬 느리게 때린다</h2>
 *
 * <p>공휴일은 초당 5건이었다. 여기는 <b>초당 2건</b>이다. 근거가 다르다 —
 * 공휴일은 회차 하나가 2,040 호출이라 속도가 회차 길이를 정했지만, 창 누적은
 * <b>리그당 2~3 호출</b>이라 빠를 필요가 없다. 그리고 이 원천은 무료 키에
 * 제한이 명시돼 있지 않아서, 모를 때는 천천히 가는 편이 낫다.
 *
 * <h2>회로 값도 다르다</h2>
 *
 * <p>호출 수가 적으므로 {@code minimumNumberOfCalls} 를 낮춰야 회로가 <b>열릴 수
 * 있다.</b> 공휴일 값(10)을 그대로 쓰면 10분마다 3건씩 부르는 이 원천에서는
 * 표본이 차기까지 30분이 걸리고, 그동안 회로는 아무 일도 하지 않는다.
 */
@Configuration
@EnableConfigurationProperties(SportsSyncProperties.class)
public class SportsDbClientConfig {

    @Bean
    public RestClient sportsDbRestClient(SportsSyncProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public RateLimiter sportsDbRateLimiter(SportsSyncProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(properties.rateLimit().permitsPerSecond())
                .timeoutDuration(properties.rateLimit().waitTimeout())
                .build();

        return RateLimiter.of(SportsDbClient.CIRCUIT, config);
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> sportsDbCircuitBreakerCustomizer() {
        CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                /* 호출이 적어 창도 표본도 작아야 한다 — 크게 두면 영영 안 열린다 */
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .permittedNumberOfCallsInHalfOpenState(2)
                /* 4xx 는 회로의 실패가 아니다. 401 은 클라이언트가 따로 가른다 */
                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        TimeLimiterConfig timeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(4))
                .build();

        return factory -> factory.configure(
                (Resilience4JConfigBuilder builder) -> builder
                        .circuitBreakerConfig(circuitBreaker)
                        .timeLimiterConfig(timeLimiter),
                SportsDbClient.CIRCUIT);
    }
}
