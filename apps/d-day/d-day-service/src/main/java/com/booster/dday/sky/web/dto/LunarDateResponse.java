package com.booster.dday.sky.web.dto;

import com.booster.dday.astro.lunar.LunarDate;

import java.time.LocalDate;

/**
 * 음력 ↔ 양력 변환 결과 (B-4).
 *
 * <p><b>양쪽을 다 준다.</b> 한쪽만 주면 부르는 쪽이 자기가 보낸 값을 다시 붙여야 하고,
 * 그러다 «내가 뭘 물었더라» 가 틀리면 조용히 다른 날이 화면에 뜬다.
 *
 * @param meridian 어느 기준 자오선으로 센 음력인가. <b>이것을 안 주면 거짓말이 된다</b> —
 *                 한국 음력과 중국 음력은 같은 해에 하루 갈라지는 일이 있다 (SPEC §9.9(4))
 */
public record LunarDateResponse(
        LocalDate solar,
        int lunarYear,
        int lunarMonth,
        int lunarDay,
        boolean leapMonth,
        String text,
        String meridian
) {

    public static LunarDateResponse of(LocalDate solar, LunarDate lunar) {
        return new LunarDateResponse(
                solar,
                lunar.year(),
                lunar.month(),
                lunar.day(),
                lunar.leapMonth(),
                lunar.toString(),
                "Asia/Seoul");
    }
}
