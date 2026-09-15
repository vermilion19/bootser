package com.booster.dday.country.api;

/**
 * 다른 컨텍스트에 공표하는 국가 한 줄 (ARCHITECTURE §1.5 R2).
 *
 * <p>엔티티를 그대로 건네지 않는다. 건네면 받는 쪽이 {@code holiday} 를 타고
 * 집합체 경계를 넘어 다니게 되고, 그 순간 R2 가 막으려던 것이 열린다.
 *
 * <p>{@code weekendMask} 를 {@code Weekend} 가 아니라 숫자로 내보내는 것은
 * <b>일부러다.</b> 이 레코드는 캐시와 응답을 함께 타므로 JSON 으로 굳는데,
 * 도메인 VO 가 직렬화 규약에 묶이면 VO 를 고칠 때마다 캐시에 든 옛 값이 깨진다.
 *
 * @param zoneAmbiguous 시간대가 여럿이라 <b>우리가 하나를 골랐다</b>는 표시.
 *                      이 값이 응답까지 따라가야 SPEC §9.9(4) 가 지켜진다
 */
public record CountryView(
        String code,
        String nameEn,
        String nameKo,
        String zoneId,
        boolean zoneAmbiguous,
        short weekendMask
) {
}
