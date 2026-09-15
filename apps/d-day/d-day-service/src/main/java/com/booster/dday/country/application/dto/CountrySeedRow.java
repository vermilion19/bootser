package com.booster.dday.country.application.dto;

import com.booster.dday.country.domain.Weekend;

/**
 * 시드 파일 한 줄. <b>원천이 그렇게 말했다</b>는 것 말고 아무 뜻도 없다.
 *
 * <p>{@code infrastructure} 가 파일에서 읽어 만들고 {@code application} 이 받아
 * 반영한다. 파일 모양을 아는 것은 읽는 쪽 하나뿐이어야 하므로, 경계에는
 * <b>파일이 아니라 값</b>이 오간다 — 나중에 시드를 JSON 이나 API 로 바꿔도
 * 반영하는 쪽은 그대로다.
 */
public record CountrySeedRow(
        String code,
        String nameEn,
        String nameKo,
        String zoneId,
        boolean zoneAmbiguous,
        Weekend weekend
) {
}
