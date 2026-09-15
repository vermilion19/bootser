package com.booster.dday.holiday.application.dto;

import com.booster.dday.holiday.domain.NameSlug;

import java.time.LocalDate;

/**
 * 축이 읽는 공휴일 한 줄. <b>축에 필요한 칸만</b> 담는다.
 *
 * <p>SPEC §9.4 가 «축은 자료를 하나도 더 만들지 않는다» 로 정했다 — {@code holiday}
 * 표 하나를 다르게 집계할 뿐이다. 그래서 축에는 도메인도 표도 없고, 읽기 모델만 있다.
 *
 * <p>엔티티를 그대로 들고 오지 않는 까닭은 한 해가 2,800행이기 때문이다. 축 하나를
 * 세는 데 쓰지도 않을 칸까지 영속성 컨텍스트에 올리면 <b>세는 값보다 관리하는 값이
 * 많아진다.</b>
 */
public record HolidayRow(
        String countryCode,
        LocalDate date,
        NameSlug nameSlug,
        String nameEn,
        boolean global
) {
}
