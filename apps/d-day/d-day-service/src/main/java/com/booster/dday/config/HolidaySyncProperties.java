package com.booster.dday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 공휴일 동기화 설정.
 *
 * <h2>{@code years} 가 이 서비스의 <b>정의역</b>이다</h2>
 *
 * <p>ARCHITECTURE §4.1 이 "캐시 키를 만드는 값은 전부 우리가 아는 유한 집합에서 와야
 * 한다" 고 했고, 공휴일 쪽의 그 집합이 여기서 나온다. 이 범위 밖의 날짜로 오는 요청은
 * <b>캐시에 닿기 전에</b> 400 으로 잘린다.
 *
 * <p>SCHEMA §13.3 의 S-2 가 열어 둔 값이다. 문서는 5년을 가정해 규모를 셌지만
 * 실제 값은 여기다 — 10년이어도 28,000행이라 결론은 안 바뀐다.
 *
 * <p>기본값을 <b>고정 연도가 아니라 「올해 기준 몇 해」</b>로 둔다. 고정 연도로 두면
 * 해가 바뀌는 날 아무도 모르게 정의역이 한 해씩 낡는다.
 *
 * @param backYears    올해 이전 몇 해까지 담나
 * @param forwardYears 올해 이후 몇 해까지 담나
 * @param rateLimit    원천을 때리는 속도. <b>진짜 제한자는 이것 하나뿐이다</b> (§5.4-1)
 */
@ConfigurationProperties(prefix = "dday.sync.holiday")
public record HolidaySyncProperties(
        String baseUrl,
        int backYears,
        int forwardYears,
        RateLimit rateLimit,
        Duration connectTimeout,
        Duration readTimeout
) {

    /**
     * @param permitsPerSecond 초당 몇 건
     * @param waitTimeout      허가를 기다리는 상한. <b>회차 전체가 걸리는 시간보다 길어야 한다</b> —
     *                         2,040 호출 ÷ 5rps ≈ 7분이므로 그보다 넉넉히 둔다.
     *                         짧으면 뒤쪽 나라들이 줄을 서다 실패로 기록된다
     */
    public record RateLimit(int permitsPerSecond, Duration waitTimeout) {
    }

    public HolidaySyncProperties {
        baseUrl = baseUrl == null ? "https://date.nager.at" : baseUrl;
        backYears = backYears <= 0 ? 1 : backYears;
        forwardYears = forwardYears <= 0 ? 2 : forwardYears;
        rateLimit = rateLimit == null
                ? new RateLimit(5, Duration.ofMinutes(15)) : rateLimit;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
    }

    /**
     * 이번 회차가 다룰 연도들. <b>「오늘」이 인자다</b>.
     *
     * <p>숨은 시계를 두지 않는 것은 {@code DDayCalculator} 와 같은 규칙이다 —
     * 해가 바뀌는 자리는 이 서비스에서 가장 틀리기 쉬운 자리인데, 시계가 숨어
     * 있으면 그 자리를 테스트할 방법이 없다.
     */
    public List<Integer> years(int currentYear) {
        return IntStream.rangeClosed(currentYear - backYears, currentYear + forwardYears)
                .boxed()
                .toList();
    }
}
