package com.booster.dday.holiday.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 정의역과 급감 가드의 근거 (SCHEMA §8.4).
 *
 * <p>조회는 PK 단건이다. 한 회차에 1,020번 부르지만 전부 PK 라 싼다 — 이력을
 * 뒤지는 것과 자릿수가 다르다.
 */
public interface HolidayCoverageRepository
        extends JpaRepository<HolidayCoverage, HolidayCoverage.Key> {

    /**
     * PK 단건 조회를 <b>파생 질의로</b> 한다.
     *
     * <p>{@code findById} 를 쓰려면 {@code @IdClass} 인스턴스를 손으로 조립해야 하는데,
     * 그러면 키를 만드는 방법이 엔티티 밖에 하나 더 생긴다. 이름으로 찾으면 그럴 일이 없다.
     */
    Optional<HolidayCoverage> findByCountryCodeAndHolidayYear(String countryCode, short holidayYear);

    List<HolidayCoverage> findAllByCountryCode(String countryCode);

    List<HolidayCoverage> findAllByHolidayYear(short holidayYear);
}
