package com.booster.dday.holiday.application.dto;

import com.booster.dday.holiday.domain.NameSlug;

/**
 * 공휴일 <b>이름</b> 한 줄 — 검색이 읽는 것.
 *
 * <p>검색의 단위는 공휴일 행이 아니라 <b>이름</b>이다. 「크리스마스」를 찾는 사람에게
 * 178개국의 크리스마스를 178줄로 내보내면 그것은 결과가 아니라 소음이다.
 * 한 줄로 주고, 누르면 이름 낱장(A-5)이 나라들을 보여 준다.
 *
 * @param countryCount 몇 나라가 쉬나. 정렬과 곁들일 한 줄에 쓴다
 */
public record HolidayNameRow(NameSlug nameSlug, String nameEn, long countryCount) {
}
