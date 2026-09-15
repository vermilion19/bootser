package com.booster.dday.holiday.infrastructure;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v3/PublicHolidays/{year}/{cc}} 의 아홉 필드 (SPEC §11.1).
 *
 * <p><b>원천이 준 모양 그대로다.</b> 여기서 정규화하지 않는다 — 「원천이 그렇게
 * 말했다」와 「우리가 그렇게 정했다」가 한 타입 안에서 섞이면, 나중에 값이 이상할 때
 * 어느 쪽 탓인지 알 수 없다.
 *
 * @param localName 현지어. <b>한국어가 아니다</b> — 일본은 {@code 元日} 이 온다
 * @param counties  {@code null} 또는 {@code ["US-TX"]}. 둘 다 전국을 뜻할 수 있다
 * @param types     <b>배열이고 실제로 복수가 온다</b> — {@code Public+Bank} 가 미국에서 10건 (§11.2)
 */
public record NagerHoliday(
        LocalDate date,
        String localName,
        String name,
        String countryCode,
        Boolean fixed,
        Boolean global,
        List<String> counties,
        Integer launchYear,
        List<String> types
) {
}
