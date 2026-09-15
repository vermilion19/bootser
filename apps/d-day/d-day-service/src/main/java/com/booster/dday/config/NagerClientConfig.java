package com.booster.dday.config;

import com.booster.dday.holiday.infrastructure.NagerClient;
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
 * Nager.Date 호출에 필요한 것들.
 *
 * <h2>배치용 회로는 사용자 요청용과 값이 달라야 한다</h2>
 *
 * <p>{@code libs/core-resilience} 의 기본값(실패율 50% · OPEN 1초 · 창 100)은
 * <b>사용자 요청용이지 배치용이 아니다</b> (ARCHITECTURE §5.5). 배치는 호출이
 * 한꺼번에 몰리고 회복 대기가 길어야 한다 — 1초 뒤에 다시 1,020개를 던지면
 * 죽어 가는 원천을 우리가 마저 눕힌다. 그래서 이름 붙인 인스턴스를 따로 둔다.
 *
 * <h2>타임아웃의 순서</h2>
 *
 * <pre>
 *   connect 1s + read 3s   &lt;   TimeLimiter 4s
 * </pre>
 *
 * <p><b>소켓에서 먼저 실패해야 회로가 그것을 실패로 기록한다.</b> TimeLimiter 가
 * 먼저 터지면 회로는 «우리가 기다리다 포기했다» 를 기록할 뿐, 어느 원천이 느린지
 * 구별하지 못한다. {@code CatalogServiceClient} 가 이미 이 순서로 해 두었고
 * 그 값을 그대로 따른다.
 */
@Configuration
@EnableConfigurationProperties(HolidaySyncProperties.class)
public class NagerClientConfig {

    @Bean
    public RestClient nagerRestClient(HolidaySyncProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 원천을 때리는 속도.
     *
     * <p>가상 스레드가 동시성의 상한을 없앴으므로 <b>이것이 유일한 제한자</b>다.
     * {@code limitRefreshPeriod} 를 1초로 두고 그 안에 {@code permitsPerSecond} 개만
     * 허가한다.
     */
    @Bean
    public RateLimiter nagerRateLimiter(HolidaySyncProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(properties.rateLimit().permitsPerSecond())
                .timeoutDuration(properties.rateLimit().waitTimeout())
                .build();

        return RateLimiter.of(NagerClient.CIRCUIT, config);
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> nagerCircuitBreakerCustomizer() {
        CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                /* 배치라 길게 잡는다. 1초 뒤에 다시 1,020개를 던지면
                   죽어 가는 원천을 우리가 마저 눕힌다 */
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .slidingWindowSize(50)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                /* 4xx 는 실패가 아니다. 한 나라가 404 를 주는 것은 원천의 장애가
                   아니라 그 나라의 사정이고, 그걸로 회로를 열면 203국이 막힌다 */
                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        TimeLimiterConfig timeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(4))
                .build();

        return factory -> factory.configure(
                (Resilience4JConfigBuilder builder) -> builder
                        .circuitBreakerConfig(circuitBreaker)
                        .timeLimiterConfig(timeLimiter),
                NagerClient.CIRCUIT);
    }
}
