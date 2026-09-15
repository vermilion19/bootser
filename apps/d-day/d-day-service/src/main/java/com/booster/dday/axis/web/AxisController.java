package com.booster.dday.axis.web;

import com.booster.core.web.response.ApiResponse;
import com.booster.dday.axis.application.AxisService;
import com.booster.dday.axis.application.dto.NameHubView;
import com.booster.dday.axis.application.dto.NameLeafView;
import com.booster.dday.axis.application.dto.RankView;
import com.booster.dday.axis.application.dto.WeekdayView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 축 — <b>같은 자료를 다르게 잘라 본다</b> (A-5 ~ A-7, SPEC §10.2).
 *
 * <pre>
 *   GET /api/v1/dday/holiday-names?year=2026          이름 허브
 *   GET /api/v1/dday/holiday-names/{slug}?year=2026   이름 낱장
 *   GET /api/v1/dday/ranks?year=2026                  순위 표 넷
 *   GET /api/v1/dday/weekdays?year=2026               요일 축
 * </pre>
 *
 * <h2>D-day 가 없다</h2>
 *
 * <p>하늘 조회와 다른 점이다. 축은 <b>「그 해에 어땠나」</b>를 묻지 「며칠 남았나」를
 * 묻지 않는다. 오늘에 의존하는 값이 없으므로 시간대도 안 받는다 — 안 쓰는 인자를
 * 받아 두면 <b>받았으니 쓰는 줄 안다.</b>
 *
 * <h2>언어도 안 받는다</h2>
 *
 * <p>이름 축이 보여 주는 것은 «그 묶음에서 가장 흔한 영어 이름» 이다. 한국어
 * 라벨은 {@code HolidayNameLabel} 표가 채워진 뒤에 붙는다 (§9.9(1) — 문턱을
 * 정하는 것이 아직 열린 결정이다). <b>없는 것을 있는 척 받지 않는다.</b>
 */
@RestController
@RequestMapping("/api/v1/dday")
@RequiredArgsConstructor
public class AxisController {

    private final AxisService axisService;

    /** 이름 허브 — "크리스마스에 쉬는 나라 178개국" 이 여기서 나온다 */
    @GetMapping("/holiday-names")
    public ApiResponse<NameHubView> nameHub(@RequestParam int year) {
        return ApiResponse.success(axisService.nameHub(year));
    }

    /**
     * 이름 낱장.
     *
     * <p>허브와 <b>같은 키(slug)</b>로 묶인다. 둘이 다른 키로 묶이면 허브의 숫자와
     * 낱장의 목록이 안 맞고, 안 맞는 이유가 안 보인다.
     */
    @GetMapping("/holiday-names/{slug}")
    public ApiResponse<NameLeafView> nameLeaf(@PathVariable String slug,
                                              @RequestParam int year) {
        return ApiResponse.success(axisService.nameLeaf(year, slug));
    }

    /** 순위 표 넷을 한 번에 — 넷이 서로 다른 회차의 자료를 가리키면 안 된다 */
    @GetMapping("/ranks")
    public ApiResponse<RankView> ranks(@RequestParam int year) {
        return ApiResponse.success(axisService.ranks(year));
    }

    /** 요일 축 — 주말에 겹쳐 날아간 공휴일 */
    @GetMapping("/weekdays")
    public ApiResponse<WeekdayView> weekdays(@RequestParam int year) {
        return ApiResponse.success(axisService.weekdays(year));
    }
}
