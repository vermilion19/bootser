package com.booster.dday.holiday.application;

import com.booster.dday.holiday.application.dto.HolidayNameRow;
import com.booster.dday.holiday.domain.HolidayNameLabel;
import com.booster.dday.holiday.domain.HolidayNameLabelRepository;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.search.api.MatchScore;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchHits;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchScope;
import com.booster.dday.search.api.SearchSource;
import com.booster.dday.search.api.SearchTerm;
import com.booster.dday.shared.locale.Lang;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 공휴일 <b>이름</b>을 찾는다 (E-2 · A-5).
 *
 * <h2>단위가 행이 아니라 이름이다</h2>
 *
 * <p>「크리스마스」를 찾는 사람에게 178개국의 크리스마스를 178줄로 내보내면 그것은
 * 결과가 아니라 소음이다. 한 줄로 주고, 누르면 이름 낱장이 나라들을 보여 준다.
 *
 * <h2>한국어로도 찾는다</h2>
 *
 * <p>원천은 영어 이름과 현지어만 준다 — <b>한국어는 우리가 채운 라벨</b>이다
 * ({@code holiday_name_label}). 라벨이 없는 이름은 영어로만 걸린다. 그것이
 * 「문턱을 넘는 이름만 채운다」(§10-9)의 대가이고, 숨기지 않고 그대로 둔다.
 *
 * <h2>올해 이름만 본다</h2>
 *
 * <p>정의역이 여러 해지만 <b>이름은 해마다 거의 같다.</b> 다 읽으면 같은 이름이
 * 해 수만큼 나오고 그것을 다시 접어야 한다 — 올해 하나로 충분하고, 올해 없는
 * 이름은 검색에도 거의 안 쓰인다.
 */
@Component
@RequiredArgsConstructor
public class HolidayNameSearchSource implements SearchSource {

    private final HolidayRepository holidays;
    private final HolidayNameLabelRepository labels;
    private final Clock clock;

    @Override
    public SearchKind kind() {
        return SearchKind.HOLIDAY_NAME;
    }

    @Override
    public SearchScope scope() {
        return SearchScope.PUBLIC;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
        short year = (short) LocalDate.now(clock).getYear();

        Map<String, String> koreanBySlug = new HashMap<>();
        for (HolidayNameLabel label : labels.findAllByLang(Lang.KO.name())) {
            koreanBySlug.put(label.getNameSlug(), label.getLabel());
        }

        List<SearchHit> hits = new ArrayList<>();
        for (HolidayNameRow row : holidays.findPublicNamesOfYear(year)) {
            String slug = row.nameSlug().value();
            String korean = koreanBySlug.get(slug);

            int score = MatchScore.best(term, korean, row.nameEn(), slug);
            if (score == MatchScore.NONE) {
                continue;
            }
            hits.add(SearchHit.of(SearchKind.HOLIDAY_NAME, slug,
                    korean == null ? row.nameEn() : korean,
                    row.countryCount() + "개국", score,
                    SearchTerm.normalize(row.nameEn())));
        }
        return SearchHits.top(hits, limit);
    }
}
