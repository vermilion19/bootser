package com.booster.dday.sky.web.dto;

import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.locale.Lang;
import com.booster.dday.sky.api.SkyEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 하늘 사건 하나의 응답.
 *
 * <p><b>{@code dDay} 가 여기서 붙는다.</b> 캐시에 든 것은 UTC 시각뿐이고, 「며칠
 * 남았나」는 이 레코드를 만들 때 정해진다 (SPEC §9.9(2)).
 *
 * @param date   <b>그 시간대에서의 날짜</b>. UTC 날짜가 아니다 (E-1)
 * @param utc    원래 시각. 날짜만 보면 몇 시인지 알 수 없어 같이 준다
 * @param dDay   오늘부터 며칠. 오늘이면 0, 지난 날이면 음수다
 * @param zone   어느 시간대로 센 값인지. <b>안 알려 주면 사용자가 자기 시간대로 읽는다</b>
 * @param estimated 우리가 셈한 값인가. 유성우에만 뜻이 있다
 */
public record SkyEventResponse(
        String kind,
        String code,
        String name,
        LocalDate date,
        Instant utc,
        int dDay,
        String zone,
        boolean estimated
) {

    public static SkyEventResponse of(SkyEvent event, Lang lang, ZoneId zone,
                                      Instant now, DDayCalculator calculator) {
        LocalDate localDate = event.date(zone);

        return new SkyEventResponse(
                event.kind(),
                event.code(),
                lang == Lang.EN ? event.nameEn() : event.nameKo(),
                localDate,
                /* 초까지만 내보낸다. 급수를 더해 나온 double 이라 나노초 자리가 차 있는데,
                   그것은 정밀도가 아니라 부동소수점의 찌꺼기다 — 검산점도 분 단위로 본다.
                   내보내면 읽는 쪽이 있지도 않은 정밀도를 믿는다 */
                event.at().truncatedTo(ChronoUnit.SECONDS),
                calculator.daysUntil(localDate, zone, now),
                zone.getId(),
                event.estimated());
    }
}
