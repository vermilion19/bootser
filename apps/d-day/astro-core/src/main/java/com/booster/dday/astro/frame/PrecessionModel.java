package com.booster.dday.astro.frame;

/**
 * 두 기준계 사이를 옮긴다. 이 인터페이스를 지나는 것이 <b>유일한</b> 길이다.
 *
 * <p>세차(歲差)는 지구 자전축이 팽이처럼 도는 것이고, 그래서 분점이 황도를 따라
 * 연 50초각쯤 뒤로 밀린다. J2000.0 에 고정한 황경과 그 시점의 황경이 어긋나는 까닭이다.
 */
public interface PrecessionModel {

    /** equinox 2000.0 황경 → 그 시점의 겉보기 황경 */
    ApparentEclipticLongitude toApparent(J2000EclipticLongitude longitude, double julianDayTt);

    /** 그 시점의 황경 → equinox 2000.0 황경 */
    J2000EclipticLongitude toJ2000(ApparentEclipticLongitude longitude, double julianDayTt);
}
