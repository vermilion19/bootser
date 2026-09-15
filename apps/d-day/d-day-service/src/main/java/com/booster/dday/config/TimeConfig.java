package com.booster.dday.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시계를 빈으로 둔다.
 *
 * <p>{@code DDayCalculator} 는 「지금」을 인자로 받는다 — 사용자에게 보이는 날짜라
 * 시계가 숨으면 자정 경계를 테스트할 방법이 없기 때문이다. 동기화 쪽은 그렇게까지
 * 할 일이 아니지만, <b>어느 해를 담을지</b>는 오늘에 의존한다. 그 한 자리 때문에
 * 시계가 필요하고, 빈으로 두면 테스트가 해를 옮겨 가며 물을 수 있다.
 *
 * <p>UTC 로 둔다. 어느 해를 담을지는 ±몇 해를 담는 문제라 자정 몇 시간 차이가
 * 답을 바꾸지 않는다 — <b>나라별 「오늘」이 필요한 자리는 여기가 아니라
 * 조립 단계</b>이고 거기서는 {@code Country.zone()} 을 쓴다 (E-1).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
